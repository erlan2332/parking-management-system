package com.parking.backend.reservation;

import com.parking.backend.barrier.BarrierService;
import com.parking.backend.common.exception.ReservationEntryConflictException;
import com.parking.backend.parking.ParkingSpotAvailability;
import com.parking.backend.parking.ParkingSpotEntity;
import com.parking.backend.parking.ParkingSpotRepository;
import com.parking.backend.parking.ParkingSpotService;
import com.parking.backend.parking.ParkingSpotStatus;
import com.parking.backend.user.UserEntity;
import com.parking.backend.user.UserRepository;
import com.parking.backend.vehicle.VehicleEntity;
import com.parking.backend.vehicle.VehicleRepository;
import com.parking.backend.visit.ParkingVisitRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "application.reservations.automation-delay=PT1H")
@ActiveProfiles("test")
@Import(ReservationAutomationIntegrationTest.TestClockConfiguration.class)
class ReservationAutomationIntegrationTest {

	private static final Instant BASE_TIME = Instant.parse("2026-12-01T08:00:00Z");

	@Autowired
	private ReservationService reservationService;
	@Autowired
	private ReservationRepository reservationRepository;
	@Autowired
	private BarrierService barrierService;
	@Autowired
	private ParkingVisitRepository parkingVisitRepository;
	@Autowired
	private ParkingSpotService parkingSpotService;
	@Autowired
	private ParkingSpotRepository parkingSpotRepository;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private VehicleRepository vehicleRepository;
	@Autowired
	private JdbcTemplate jdbcTemplate;
	@Autowired
	private MutableClock clock;

	@BeforeEach
	void setUp() {
		cleanBusinessData();
		jdbcTemplate.update("UPDATE parking_spots SET active = TRUE");
		clock.set(BASE_TIME);
	}

	@AfterEach
	void tearDown() {
		cleanBusinessData();
		jdbcTemplate.update("UPDATE parking_spots SET active = TRUE");
	}

	@Test
	@Tag("smoke")
	void expiresNoShowAtExactDeadlineAndRepeatedCleanupIsSafe() {
		Driver driver = createDriver("no-show@example.com", "01KG931AAA");
		ReservationEntity reservation = createConfirmedReservation(
				driver,
				spot("A1"),
				BASE_TIME.plus(Duration.ofHours(1)),
				Duration.ofHours(2)
		);

		clock.set(reservation.getStartAt().plus(Duration.ofMinutes(15)).minusSeconds(1));
		assertEquals(0, reservationService.expireNoShows());
		assertEquals(ReservationStatus.ACTIVE, reload(reservation).getStatus());

		clock.advance(Duration.ofSeconds(1));
		assertEquals(1, reservationService.expireNoShows());
		assertEquals(ReservationStatus.EXPIRED, reload(reservation).getStatus());
		assertEquals(0, reservationService.expireNoShows());
		assertEquals(
				ReservationStatus.EXPIRED,
				reservationService.getMyReservations(driver.user().getId()).getFirst().getStatus()
		);
	}

	@Test
	void shortReservationExpiresAtItsOwnEndBeforeFifteenMinutes() {
		Driver driver = createDriver("short-no-show@example.com", "01KG932AAA");
		ReservationEntity reservation = createConfirmedReservation(
				driver,
				spot("A1"),
				BASE_TIME.plus(Duration.ofHours(1)),
				Duration.ofMinutes(5)
		);

		clock.set(reservation.getEndAt());

		assertEquals(1, reservationService.expireNoShows());
		assertEquals(ReservationStatus.EXPIRED, reload(reservation).getStatus());
	}

	@Test
	void checkedInAndCancelledReservationsNeverExpireAsNoShows() {
		Driver checkedInDriver = createDriver("checked-in@example.com", "01KG933AAA");
		ReservationEntity checkedIn = createConfirmedReservation(
				checkedInDriver,
				spot("A1"),
				BASE_TIME.plus(Duration.ofHours(1)),
				Duration.ofHours(2)
		);
		Driver cancelledDriver = createDriver("cancelled@example.com", "01KG934AAA");
		ReservationEntity cancelled = createConfirmedReservation(
				cancelledDriver,
				spot("A2"),
				BASE_TIME.plus(Duration.ofHours(3)),
				Duration.ofHours(1)
		);
		reservationService.cancelReservation(cancelledDriver.user().getId(), cancelled.getId());
		clock.set(checkedIn.getStartAt().plus(Duration.ofMinutes(5)));
		barrierService.enter(checkedInDriver.user().getId(), checkedInDriver.vehicle().getPlateNumber());

		clock.set(cancelled.getEndAt().plus(Duration.ofMinutes(1)));

		assertEquals(0, reservationService.expireNoShows());
		assertEquals(ReservationStatus.CHECKED_IN, reload(checkedIn).getStatus());
		assertEquals(ReservationStatus.CANCELLED, reload(cancelled).getStatus());
	}

	@Test
	void lateEntryExpiresReservationWithoutCreatingVisit() {
		Driver driver = createDriver("late-entry@example.com", "01KG935AAA");
		ReservationEntity reservation = createConfirmedReservation(
				driver,
				spot("A1"),
				BASE_TIME.plus(Duration.ofHours(1)),
				Duration.ofHours(2)
		);
		clock.set(reservation.getStartAt().plus(Duration.ofMinutes(15)));

		assertThrows(
				ReservationEntryConflictException.class,
				() -> barrierService.enter(driver.user().getId(), driver.vehicle().getPlateNumber())
		);

		assertEquals(ReservationStatus.EXPIRED, reload(reservation).getStatus());
		assertEquals(0, parkingVisitRepository.count());
	}

	@Test
	void logicallyExpiredReservationDoesNotBlockAvailabilityOrNewHoldBeforeCleanup() {
		Driver absentDriver = createDriver("logical-expiry@example.com", "01KG936AAA");
		Driver replacementDriver = createDriver("logical-replacement@example.com", "01KG937AAA");
		ParkingSpotEntity spot = spot("A1");
		ReservationEntity expired = createConfirmedReservation(
				absentDriver,
				spot,
				BASE_TIME.plus(Duration.ofHours(1)),
				Duration.ofHours(2)
		);
		clock.set(expired.getStartAt().plus(Duration.ofMinutes(15)));

		ParkingSpotAvailability availability = parkingSpotService.getAvailability(
				at(clock.instant().plusSeconds(1)),
				at(expired.getEndAt())
		).stream().filter(candidate -> candidate.id().equals(spot.getId())).findFirst().orElseThrow();
		assertEquals(ParkingSpotStatus.FREE, availability.status());

		ReservationEntity replacement = reservationService.createHold(
				replacementDriver.user().getId(),
				replacementDriver.vehicle().getId(),
				spot.getId(),
				at(clock.instant().plus(Duration.ofMinutes(1))),
				at(expired.getEndAt())
		);

		assertEquals(ReservationStatus.EXPIRED, reload(expired).getStatus());
		assertEquals(ReservationStatus.HELD, replacement.getStatus());
	}

	@Test
	void concurrentLateEntryAndCleanupCannotCreateContradictoryState() throws Exception {
		Driver driver = createDriver("no-show-race@example.com", "01KG938AAA");
		ReservationEntity reservation = createConfirmedReservation(
				driver,
				spot("A1"),
				BASE_TIME.plus(Duration.ofHours(1)),
				Duration.ofHours(2)
		);
		clock.set(reservation.getStartAt().plus(Duration.ofMinutes(15)));
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch startGate = new CountDownLatch(1);

		try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
			Future<Boolean> entry = executor.submit(() -> {
				ready.countDown();
				startGate.await();
				try {
					barrierService.enter(driver.user().getId(), driver.vehicle().getPlateNumber());
					return true;
				} catch (ReservationEntryConflictException expected) {
					return false;
				}
			});
			Future<Integer> cleanup = executor.submit(() -> {
				ready.countDown();
				startGate.await();
				return reservationService.expireNoShows();
			});
			assertTrue(ready.await(5, TimeUnit.SECONDS));
			startGate.countDown();
			assertFalse(entry.get(10, TimeUnit.SECONDS));
			assertTrue(cleanup.get(10, TimeUnit.SECONDS) >= 0);
		}

		assertEquals(ReservationStatus.EXPIRED, reload(reservation).getStatus());
		assertEquals(0, parkingVisitRepository.count());
	}

	private ReservationEntity createConfirmedReservation(
			Driver driver,
			ParkingSpotEntity spot,
			Instant startAt,
			Duration duration
	) {
		ReservationEntity hold = reservationService.createHold(
				driver.user().getId(),
				driver.vehicle().getId(),
				spot.getId(),
				at(startAt),
				at(startAt.plus(duration))
		);
		return reservationService.confirmHold(driver.user().getId(), hold.getId());
	}

	private ReservationEntity reload(ReservationEntity reservation) {
		return reservationRepository.findById(reservation.getId()).orElseThrow();
	}

	private Driver createDriver(String email, String plate) {
		UserEntity user = userRepository.saveAndFlush(new UserEntity(email, "password-hash"));
		VehicleEntity vehicle = vehicleRepository.saveAndFlush(new VehicleEntity(user, plate, plate));
		return new Driver(user, vehicle);
	}

	private ParkingSpotEntity spot(String code) {
		return parkingSpotRepository.findAllInNaturalCodeOrder().stream()
				.filter(candidate -> candidate.getCode().equals(code))
				.findFirst()
				.orElseThrow();
	}

	private void cleanBusinessData() {
		jdbcTemplate.update("DELETE FROM reservation_notifications");
		jdbcTemplate.update("DELETE FROM invoices");
		jdbcTemplate.update("DELETE FROM parking_visits");
		jdbcTemplate.update("DELETE FROM reservations");
		jdbcTemplate.update("DELETE FROM vehicles");
		jdbcTemplate.update("DELETE FROM users");
	}

	private static OffsetDateTime at(Instant instant) {
		return instant.atOffset(ZoneOffset.UTC);
	}

	private record Driver(UserEntity user, VehicleEntity vehicle) {
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class TestClockConfiguration {

		@Bean
		@Primary
		MutableClock reservationAutomationTestClock() {
			return new MutableClock(BASE_TIME);
		}
	}

	static final class MutableClock extends Clock {

		private final AtomicReference<Instant> instant;

		MutableClock(Instant instant) {
			this.instant = new AtomicReference<>(instant);
		}

		void set(Instant value) {
			instant.set(value);
		}

		void advance(Duration duration) {
			instant.updateAndGet(current -> current.plus(duration));
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return ZoneOffset.UTC.equals(zone) ? this : Clock.fixed(instant(), zone);
		}

		@Override
		public Instant instant() {
			return instant.get();
		}
	}
}
