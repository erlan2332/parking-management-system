package com.parking.backend.barrier;

import com.parking.backend.common.exception.NoActiveVisitException;
import com.parking.backend.common.exception.NoAvailableParkingSpotException;
import com.parking.backend.common.exception.ReservationConflictException;
import com.parking.backend.common.exception.ReservationEntryConflictException;
import com.parking.backend.common.exception.VehicleAlreadyParkedException;
import com.parking.backend.parking.ParkingSpotAvailability;
import com.parking.backend.parking.ParkingSpotEntity;
import com.parking.backend.parking.ParkingSpotRepository;
import com.parking.backend.parking.ParkingSpotService;
import com.parking.backend.parking.ParkingSpotStatus;
import com.parking.backend.reservation.ReservationEntity;
import com.parking.backend.reservation.ReservationRepository;
import com.parking.backend.reservation.ReservationService;
import com.parking.backend.reservation.ReservationStatus;
import com.parking.backend.user.UserEntity;
import com.parking.backend.user.UserRepository;
import com.parking.backend.vehicle.VehicleEntity;
import com.parking.backend.vehicle.VehicleRepository;
import com.parking.backend.visit.ParkingEntryType;
import com.parking.backend.visit.ParkingVisitEntity;
import com.parking.backend.visit.ParkingVisitRepository;
import com.parking.backend.visit.ParkingVisitService;
import com.parking.backend.visit.ParkingVisitStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Import(BarrierIntegrationTest.TestClockConfiguration.class)
class BarrierIntegrationTest {

	private static final Instant BASE_TIME = Instant.parse("2026-12-01T08:00:00Z");

	@Autowired
	private BarrierService barrierService;
	@Autowired
	private ParkingVisitService parkingVisitService;
	@Autowired
	private ParkingVisitRepository parkingVisitRepository;
	@Autowired
	private ReservationService reservationService;
	@Autowired
	private ReservationRepository reservationRepository;
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
	void reservationEntryAndExitCompleteVisitAndReservation() {
		Driver driver = createDriver("reserved-entry@example.com", "01KG601AAA");
		ReservationEntity reservation = createConfirmedReservation(driver, spot("A2"));
		clock.set(reservation.getStartAt().plus(Duration.ofMinutes(5)));

		ParkingVisitEntity visit = barrierService.enter(driver.user().getId(), "01 kg 601 aaa");

		assertEquals(ParkingEntryType.RESERVATION, visit.getEntryType());
		assertEquals(reservation.getId(), visit.getReservation().getId());
		assertEquals(ParkingVisitStatus.ACTIVE, visit.getStatus());
		assertEquals(
				ReservationStatus.CHECKED_IN,
				reservationRepository.findById(reservation.getId()).orElseThrow().getStatus()
		);
		assertEquals(ParkingSpotStatus.OCCUPIED, parkingSpotService.getCurrentSpotState(spot("A2").getId()).status());

		clock.advance(Duration.ofMinutes(85));
		ParkingVisitEntity completed = barrierService.exit(
				driver.user().getId(),
				driver.vehicle().getPlateNumber()
		).visit();
		assertEquals(ParkingVisitStatus.COMPLETED, completed.getStatus());
		assertEquals(5100, Duration.between(completed.getEnteredAt(), completed.getExitedAt()).getSeconds());
		assertEquals(
				ReservationStatus.COMPLETED,
				reservationRepository.findById(reservation.getId()).orElseThrow().getStatus()
		);
		assertEquals(ParkingSpotStatus.FREE, parkingSpotService.getCurrentSpotState(spot("A2").getId()).status());
		assertEquals(
				ReservationStatus.COMPLETED,
				reservationService.getMyReservations(driver.user().getId()).getFirst().getStatus()
		);
	}

	@ParameterizedTest
	@ValueSource(longs = {-1, 900})
	void rejectsReservationEntryBeforeStartAndAtFifteenMinuteDeadline(long secondsFromStart) {
		Driver driver = createDriver("window-" + secondsFromStart + "@example.com", "01KG602" + Math.abs(secondsFromStart));
		ReservationEntity reservation = createConfirmedReservation(driver, spot("A1"));
		clock.set(reservation.getStartAt().plusSeconds(secondsFromStart));

		assertThrows(
				ReservationEntryConflictException.class,
				() -> barrierService.enter(driver.user().getId(), driver.vehicle().getPlateNumber())
		);
		assertEquals(0, parkingVisitRepository.count());
		assertEquals(
				ReservationStatus.ACTIVE,
				reservationRepository.findById(reservation.getId()).orElseThrow().getStatus()
		);
	}

	@Test
	void walkInUsesFirstNaturalSpotAndHistoryRemainsOwnerScoped() {
		Driver driver = createDriver("walk-in@example.com", "01KG603AAA");
		Driver stranger = createDriver("walk-in-stranger@example.com", "01KG604AAA");

		ParkingVisitEntity visit = barrierService.enter(driver.user().getId(), "01 kg-603 aaa");

		assertEquals(ParkingEntryType.WALK_IN, visit.getEntryType());
		assertEquals("A1", visit.getSpot().getCode());
		assertNull(visit.getReservation());
		assertEquals(List.of(visit.getId()), parkingVisitService.getMyVisits(driver.user().getId()).stream()
				.map(ParkingVisitEntity::getId)
				.toList());
		assertTrue(parkingVisitService.getMyVisits(stranger.user().getId()).isEmpty());
	}

	@Test
	void rejectsWalkInWhenNoSpotIsAvailable() {
		Driver driver = createDriver("full@example.com", "01KG605AAA");
		jdbcTemplate.update("UPDATE parking_spots SET active = FALSE");

		assertThrows(
				NoAvailableParkingSpotException.class,
				() -> barrierService.enter(driver.user().getId(), driver.vehicle().getPlateNumber())
		);
		assertEquals(0, parkingVisitRepository.count());
	}

	@Test
	void rejectsDuplicateEntryAndRepeatedExitWithoutChangingHistory() {
		Driver driver = createDriver("repeat@example.com", "01KG606AAA");
		ParkingVisitEntity visit = barrierService.enter(driver.user().getId(), driver.vehicle().getPlateNumber());

		assertThrows(
				VehicleAlreadyParkedException.class,
				() -> barrierService.enter(driver.user().getId(), driver.vehicle().getPlateNumber())
		);
		clock.advance(Duration.ofMinutes(10));
		ParkingVisitEntity completed = barrierService.exit(
				driver.user().getId(),
				driver.vehicle().getPlateNumber()
		).visit();
		Instant firstExitTime = completed.getExitedAt();
		clock.advance(Duration.ofMinutes(10));
		assertThrows(
				NoActiveVisitException.class,
				() -> barrierService.exit(driver.user().getId(), driver.vehicle().getPlateNumber())
		);
		assertEquals(1, parkingVisitRepository.count());
		assertEquals(firstExitTime, parkingVisitRepository.findById(visit.getId()).orElseThrow().getExitedAt());
	}

	@Test
	void holdAndFutureReservationPreventOpenEndedWalkIn() {
		Driver driver = createDriver("obligation@example.com", "01KG607AAA");
		ParkingSpotEntity reservedSpot = spot("B10");
		ReservationEntity hold = createHold(driver, reservedSpot);

		assertThrows(
				ReservationEntryConflictException.class,
				() -> barrierService.enter(driver.user().getId(), driver.vehicle().getPlateNumber())
		);
		reservationService.releaseHold(driver.user().getId(), hold.getId());
		ReservationEntity confirmed = createConfirmedReservation(driver, reservedSpot);

		assertThrows(
				ReservationEntryConflictException.class,
				() -> barrierService.enter(driver.user().getId(), driver.vehicle().getPlateNumber())
		);
		assertEquals(ReservationStatus.ACTIVE, confirmed.getStatus());
		assertEquals(0, parkingVisitRepository.count());
	}

	@Test
	void occupiedSpotBlocksHoldUntilPhysicalExit() {
		Driver walkInDriver = createDriver("occupied@example.com", "01KG608AAA");
		Driver bookingDriver = createDriver("booking@example.com", "01KG609AAA");
		ParkingVisitEntity visit = barrierService.enter(
				walkInDriver.user().getId(),
				walkInDriver.vehicle().getPlateNumber()
		);
		Instant start = BASE_TIME.plus(Duration.ofHours(2));

		assertThrows(
				ReservationConflictException.class,
				() -> reservationService.createHold(
						bookingDriver.user().getId(),
						bookingDriver.vehicle().getId(),
						visit.getSpot().getId(),
						at(start),
						at(start.plus(Duration.ofHours(1)))
				)
		);
		assertEquals(ParkingSpotStatus.OCCUPIED, availabilityFor(visit.getSpot(), start).status());

		barrierService.exit(walkInDriver.user().getId(), walkInDriver.vehicle().getPlateNumber());
		ReservationEntity hold = reservationService.createHold(
				bookingDriver.user().getId(),
				bookingDriver.vehicle().getId(),
				visit.getSpot().getId(),
				at(start),
				at(start.plus(Duration.ofHours(1)))
		);
		assertEquals(ReservationStatus.HELD, hold.getStatus());
		assertEquals(ParkingSpotStatus.HELD, availabilityFor(visit.getSpot(), start).status());
	}

	@Test
	@Tag("smoke")
	void concurrentWalkInsUseDifferentSpotsWithoutDoubleOccupancy() throws Exception {
		Driver first = createDriver("parallel-first@example.com", "01KG610AAA");
		Driver second = createDriver("parallel-second@example.com", "01KG611AAA");
		jdbcTemplate.update("UPDATE parking_spots SET active = FALSE WHERE code NOT IN ('A1', 'A2')");
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch startGate = new CountDownLatch(1);

		try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
			Future<ParkingVisitEntity> firstEntry = executor.submit(
					() -> enterConcurrently(first, ready, startGate)
			);
			Future<ParkingVisitEntity> secondEntry = executor.submit(
					() -> enterConcurrently(second, ready, startGate)
			);
			assertTrue(ready.await(5, TimeUnit.SECONDS));
			startGate.countDown();
			ParkingVisitEntity firstVisit = firstEntry.get(10, TimeUnit.SECONDS);
			ParkingVisitEntity secondVisit = secondEntry.get(10, TimeUnit.SECONDS);
			assertNotEquals(firstVisit.getSpot().getId(), secondVisit.getSpot().getId());
		}

		assertEquals(2, jdbcTemplate.queryForObject(
				"SELECT count(*) FROM parking_visits WHERE exited_at IS NULL",
				Integer.class
		));
		assertEquals(2, jdbcTemplate.queryForObject(
				"SELECT count(DISTINCT spot_id) FROM parking_visits WHERE exited_at IS NULL",
				Integer.class
		));
	}

	@Test
	void concurrentHoldAndWalkInCannotClaimSameSpot() throws Exception {
		Driver bookingDriver = createDriver("race-booking@example.com", "01KG612AAA");
		Driver walkInDriver = createDriver("race-walk-in@example.com", "01KG613AAA");
		ParkingSpotEntity spot = spot("A1");
		jdbcTemplate.update("UPDATE parking_spots SET active = FALSE WHERE code <> 'A1'");
		Instant start = BASE_TIME.plus(Duration.ofHours(1));
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch startGate = new CountDownLatch(1);

		try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
			Future<Boolean> holdAttempt = executor.submit(() -> {
				ready.countDown();
				startGate.await();
				try {
					reservationService.createHold(
							bookingDriver.user().getId(),
							bookingDriver.vehicle().getId(),
							spot.getId(),
							at(start),
							at(start.plus(Duration.ofHours(1)))
					);
					return true;
				} catch (ReservationConflictException expected) {
					return false;
				}
			});
			Future<Boolean> walkInAttempt = executor.submit(() -> {
				ready.countDown();
				startGate.await();
				try {
					barrierService.enter(walkInDriver.user().getId(), walkInDriver.vehicle().getPlateNumber());
					return true;
				} catch (NoAvailableParkingSpotException expected) {
					return false;
				}
			});
			assertTrue(ready.await(5, TimeUnit.SECONDS));
			startGate.countDown();
			int successes = (holdAttempt.get(10, TimeUnit.SECONDS) ? 1 : 0)
					+ (walkInAttempt.get(10, TimeUnit.SECONDS) ? 1 : 0);
			assertEquals(1, successes);
		}

		Integer claims = jdbcTemplate.queryForObject(
				"""
				SELECT
				    (SELECT count(*) FROM parking_visits WHERE spot_id = ? AND exited_at IS NULL)
				    +
				    (SELECT count(*) FROM reservations WHERE spot_id = ? AND status = 'HELD')
				""",
				Integer.class,
				spot.getId(),
				spot.getId()
		);
		assertEquals(1, claims);
	}

	@Test
	void databaseConstraintsRejectDuplicateActiveVehicleSpotAndReservation() {
		Driver reservedDriver = createDriver("db-reserved@example.com", "01KG614AAA");
		Driver otherDriver = createDriver("db-other@example.com", "01KG615AAA");
		Driver thirdDriver = createDriver("db-third@example.com", "01KG616AAA");
		ReservationEntity reservation = createConfirmedReservation(reservedDriver, spot("A1"));
		clock.set(reservation.getStartAt());
		ParkingVisitEntity visit = barrierService.enter(
				reservedDriver.user().getId(),
				reservedDriver.vehicle().getPlateNumber()
		);

		assertThrows(
				DataIntegrityViolationException.class,
				() -> insertVisit(reservedDriver.vehicle(), spot("A2"), null, "WALK_IN")
		);
		assertThrows(
				DataIntegrityViolationException.class,
				() -> insertVisit(otherDriver.vehicle(), visit.getSpot(), null, "WALK_IN")
		);
		assertThrows(
				DataIntegrityViolationException.class,
				() -> insertVisit(thirdDriver.vehicle(), spot("A3"), reservation.getId(), "RESERVATION")
		);
		assertEquals(1, parkingVisitRepository.count());
	}

	private ParkingVisitEntity enterConcurrently(
			Driver driver,
			CountDownLatch ready,
			CountDownLatch startGate
	) throws InterruptedException {
		ready.countDown();
		startGate.await();
		return barrierService.enter(driver.user().getId(), driver.vehicle().getPlateNumber());
	}

	private ReservationEntity createConfirmedReservation(Driver driver, ParkingSpotEntity spot) {
		ReservationEntity hold = createHold(driver, spot);
		return reservationService.confirmHold(driver.user().getId(), hold.getId());
	}

	private ReservationEntity createHold(Driver driver, ParkingSpotEntity spot) {
		Instant start = BASE_TIME.plus(Duration.ofHours(1));
		return reservationService.createHold(
				driver.user().getId(),
				driver.vehicle().getId(),
				spot.getId(),
				at(start),
				at(start.plus(Duration.ofHours(2)))
		);
	}

	private ParkingSpotAvailability availabilityFor(ParkingSpotEntity spot, Instant start) {
		return parkingSpotService.getAvailability(at(start), at(start.plus(Duration.ofHours(1)))).stream()
				.filter(candidate -> candidate.id().equals(spot.getId()))
				.findFirst()
				.orElseThrow();
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

	private void insertVisit(
			VehicleEntity vehicle,
			ParkingSpotEntity spot,
			Long reservationId,
			String entryType
	) {
		jdbcTemplate.update(
				"""
				INSERT INTO parking_visits (
				    vehicle_id, spot_id, reservation_id, entry_type, entered_at, created_at
				) VALUES (?, ?, ?, ?, ?, ?)
				""",
				vehicle.getId(),
				spot.getId(),
				reservationId,
				entryType,
				at(BASE_TIME),
				at(BASE_TIME)
		);
	}

	private void cleanBusinessData() {
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
		MutableClock barrierTestClock() {
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
