package com.parking.backend.reservation;

import com.parking.backend.support.MutableClock;

import com.parking.backend.reservation.entity.ReservationEntity;
import com.parking.backend.reservation.model.ReservationStatus;
import com.parking.backend.reservation.repository.ReservationRepository;
import com.parking.backend.reservation.service.ReservationService;
import com.parking.backend.reservation.service.ReservationExpirationService;

import com.parking.backend.common.exception.ReservationConflictException;
import com.parking.backend.common.exception.ReservationNotFoundException;
import com.parking.backend.parking.model.ParkingSpotAvailability;
import com.parking.backend.parking.entity.ParkingSpotEntity;
import com.parking.backend.parking.repository.ParkingSpotRepository;
import com.parking.backend.parking.service.ParkingSpotService;
import com.parking.backend.parking.model.ParkingSpotStatus;
import com.parking.backend.user.entity.UserEntity;
import com.parking.backend.user.repository.UserRepository;
import com.parking.backend.vehicle.entity.VehicleEntity;
import com.parking.backend.vehicle.repository.VehicleRepository;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Import(ReservationIntegrationTest.TestClockConfiguration.class)
class ReservationIntegrationTest {

	private static final Instant BASE_TIME = Instant.parse("2026-12-01T08:00:00Z");

	@Autowired
	private ReservationService reservationService;
	@Autowired
	private ReservationExpirationService reservationExpirationService;
	@Autowired
	private ReservationRepository reservationRepository;
	@Autowired
	private ParkingSpotRepository parkingSpotRepository;
	@Autowired
	private ParkingSpotService parkingSpotService;
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
		cleanReservationData();
		clock.set(BASE_TIME);
	}

	@AfterEach
	void tearDown() {
		cleanReservationData();
	}

	@Test
	void allowsAdjacentIntervalsAndRejectsOverlappingSpotHold() {
		Driver first = createDriver("first@example.com", "01KG001AAA");
		Driver second = createDriver("second@example.com", "01KG002AAA");
		ParkingSpotEntity spot = spot("A1");
		Instant start = BASE_TIME.plus(Duration.ofHours(1));
		Instant end = start.plus(Duration.ofHours(1));
		reservationService.createHold(first.user().getId(), first.vehicle().getId(), spot.getId(), at(start), at(end));

		assertThrows(
				ReservationConflictException.class,
				() -> reservationService.createHold(
						second.user().getId(),
						second.vehicle().getId(),
						spot.getId(),
						at(start.plusSeconds(1)),
						at(end.plusSeconds(1))
				)
		);

		ReservationEntity adjacent = reservationService.createHold(
				second.user().getId(),
				second.vehicle().getId(),
				spot.getId(),
				at(end),
				at(end.plus(Duration.ofHours(1)))
		);
		assertEquals(ReservationStatus.HELD, adjacent.getStatus());
	}

	@Test
	void rejectsOverlappingHoldForSameVehicleOnDifferentSpot() {
		Driver driver = createDriver("vehicle-conflict@example.com", "01KG003AAA");
		Instant start = BASE_TIME.plus(Duration.ofHours(1));
		Instant end = start.plus(Duration.ofHours(1));
		reservationService.createHold(
				driver.user().getId(), driver.vehicle().getId(), spot("A1").getId(), at(start), at(end)
		);

		assertThrows(
				ReservationConflictException.class,
				() -> reservationService.createHold(
						driver.user().getId(),
						driver.vehicle().getId(),
						spot("A2").getId(),
						at(start.plusSeconds(30)),
						at(end.plusSeconds(30))
				)
		);
	}

	@Test
	void databaseConstraintsRejectSpotAndVehicleOverlapEvenWithoutServicePrecheck() {
		Driver first = createDriver("database-first@example.com", "01KG014AAA");
		Driver second = createDriver("database-second@example.com", "01KG015AAA");
		ParkingSpotEntity firstSpot = spot("A1");
		ParkingSpotEntity secondSpot = spot("A2");
		Instant start = BASE_TIME.plus(Duration.ofHours(1));
		Instant end = start.plus(Duration.ofHours(1));
		insertHold(first, firstSpot, start, end);

		assertThrows(
				DataIntegrityViolationException.class,
				() -> insertHold(second, firstSpot, start.plusSeconds(1), end.plusSeconds(1))
		);
		assertThrows(
				DataIntegrityViolationException.class,
				() -> insertHold(first, secondSpot, start.plusSeconds(1), end.plusSeconds(1))
		);
		assertEquals(1, jdbcTemplate.queryForObject("SELECT count(*) FROM reservations", Integer.class));
	}

	@Test
	@Tag("smoke")
	void confirmsAndCancelsIdempotentlyAndKeepsReservationHistory() {
		Driver driver = createDriver("lifecycle@example.com", "01KG004AAA");
		Driver stranger = createDriver("lifecycle-stranger@example.com", "01KG017AAA");
		ReservationEntity hold = createFutureHold(driver, spot("A1"));

		ReservationEntity confirmed = reservationService.confirmHold(driver.user().getId(), hold.getId());
		ReservationEntity confirmedAgain = reservationService.confirmHold(driver.user().getId(), hold.getId());
		assertEquals(ReservationStatus.ACTIVE, confirmed.getStatus());
		assertEquals(confirmed.getConfirmedAt(), confirmedAgain.getConfirmedAt());
		assertEquals(List.of(hold.getId()), reservationService.getMyReservations(driver.user().getId()).stream()
				.map(ReservationEntity::getId)
				.toList());
		assertThrows(
				ReservationNotFoundException.class,
				() -> reservationService.cancelReservation(stranger.user().getId(), hold.getId())
		);

		ReservationEntity cancelled = reservationService.cancelReservation(driver.user().getId(), hold.getId());
		ReservationEntity cancelledAgain = reservationService.cancelReservation(driver.user().getId(), hold.getId());
		assertEquals(ReservationStatus.CANCELLED, cancelled.getStatus());
		assertEquals(cancelled.getCancelledAt(), cancelledAgain.getCancelledAt());
		assertEquals(
				ReservationStatus.CANCELLED,
				reservationService.getMyReservations(driver.user().getId()).getFirst().getStatus()
		);
	}

	@Test
	void releasesIdempotentlyAndHidesForeignHoldAsNotFound() {
		Driver owner = createDriver("owner@example.com", "01KG005AAA");
		Driver stranger = createDriver("stranger@example.com", "01KG006AAA");
		ReservationEntity hold = createFutureHold(owner, spot("A1"));

		assertThrows(
				ReservationNotFoundException.class,
				() -> reservationService.releaseHold(stranger.user().getId(), hold.getId())
		);

		ReservationEntity released = reservationService.releaseHold(owner.user().getId(), hold.getId());
		ReservationEntity releasedAgain = reservationService.releaseHold(owner.user().getId(), hold.getId());
		assertEquals(ReservationStatus.HOLD_RELEASED, released.getStatus());
		assertEquals(released.getCancelledAt(), releasedAgain.getCancelledAt());
		assertTrue(reservationService.getMyActiveHolds(owner.user().getId()).isEmpty());
	}

	@Test
	void expiresStaleConflictInsideNewHoldTransaction() {
		Driver first = createDriver("expired@example.com", "01KG007AAA");
		Driver second = createDriver("replacement@example.com", "01KG008AAA");
		ParkingSpotEntity spot = spot("A1");
		ReservationEntity stale = createFutureHold(first, spot);
		clock.advance(Duration.ofMinutes(3));

		ReservationEntity replacement = reservationService.createHold(
				second.user().getId(),
				second.vehicle().getId(),
				spot.getId(),
				at(stale.getStartAt()),
				at(stale.getEndAt())
		);

		assertEquals(
				ReservationStatus.HOLD_EXPIRED,
				reservationRepository.findById(stale.getId()).orElseThrow().getStatus()
		);
		assertEquals(ReservationStatus.HELD, replacement.getStatus());
		assertNotEquals(stale.getId(), replacement.getId());
	}

	@Test
	void cleanupExpiresDueHoldsWithoutChangingConfirmedReservations() {
		Driver heldDriver = createDriver("cleanup-held@example.com", "01KG012AAA");
		Driver confirmedDriver = createDriver("cleanup-active@example.com", "01KG013AAA");
		ReservationEntity dueHold = createFutureHold(heldDriver, spot("A1"));
		ReservationEntity confirmed = createFutureHold(confirmedDriver, spot("A2"));
		reservationService.confirmHold(confirmedDriver.user().getId(), confirmed.getId());
		clock.advance(Duration.ofMinutes(3));

		assertEquals(1, reservationExpirationService.expireDueHolds());
		assertEquals(
				ReservationStatus.HOLD_EXPIRED,
				reservationRepository.findById(dueHold.getId()).orElseThrow().getStatus()
		);
		assertEquals(
				ReservationStatus.ACTIVE,
				reservationRepository.findById(confirmed.getId()).orElseThrow().getStatus()
		);
		assertEquals(0, reservationExpirationService.expireDueHolds());
	}

	@Test
	void availabilityShowsHeldAndReservedButCurrentStateIgnoresFutureReservation() {
		Driver driver = createDriver("availability@example.com", "01KG009AAA");
		Driver competingDriver = createDriver("availability-conflict@example.com", "01KG016AAA");
		ParkingSpotEntity spot = spot("A1");
		ReservationEntity hold = createFutureHold(driver, spot);

		assertEquals(ParkingSpotStatus.HELD, availabilityFor(spot, hold).status());
		assertEquals(ParkingSpotStatus.FREE, parkingSpotService.getCurrentSpotState(spot.getId()).status());

		reservationService.confirmHold(driver.user().getId(), hold.getId());
		assertEquals(ParkingSpotStatus.RESERVED, availabilityFor(spot, hold).status());
		assertThrows(
				ReservationConflictException.class,
				() -> reservationService.createHold(
						competingDriver.user().getId(),
						competingDriver.vehicle().getId(),
						spot.getId(),
						at(hold.getStartAt()),
						at(hold.getEndAt())
				)
		);

		reservationService.cancelReservation(driver.user().getId(), hold.getId());
		assertEquals(ParkingSpotStatus.FREE, availabilityFor(spot, hold).status());

		ReservationEntity expiring = createFutureHold(driver, spot);
		clock.advance(Duration.ofMinutes(3));
		assertEquals(ParkingSpotStatus.FREE, availabilityFor(spot, expiring).status());
	}

	@Test
	@Tag("smoke")
	void concurrentHoldAndConfirmationRequestsRemainConflictSafe() throws Exception {
		Driver first = createDriver("concurrent-first@example.com", "01KG010AAA");
		Driver second = createDriver("concurrent-second@example.com", "01KG011AAA");
		ParkingSpotEntity spot = spot("A1");
		Instant start = BASE_TIME.plus(Duration.ofHours(1));
		Instant end = start.plus(Duration.ofHours(1));
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch startGate = new CountDownLatch(1);

		try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
			Future<HoldAttempt> firstAttempt = executor.submit(
					() -> tryCreateConcurrently(first, spot, start, end, ready, startGate)
			);
			Future<HoldAttempt> secondAttempt = executor.submit(
					() -> tryCreateConcurrently(second, spot, start, end, ready, startGate)
			);
			assertTrue(ready.await(5, TimeUnit.SECONDS), "Both transactions must reach the start gate");
			startGate.countDown();

			HoldAttempt firstResult = firstAttempt.get(10, TimeUnit.SECONDS);
			HoldAttempt secondResult = secondAttempt.get(10, TimeUnit.SECONDS);
			int successfulAttempts = (firstResult.created() ? 1 : 0) + (secondResult.created() ? 1 : 0);
			assertEquals(1, successfulAttempts);

			HoldAttempt winner = firstResult.created() ? firstResult : secondResult;
			CountDownLatch confirmReady = new CountDownLatch(2);
			CountDownLatch confirmGate = new CountDownLatch(1);
			Future<ReservationStatus> firstConfirmation = executor.submit(
					() -> confirmConcurrently(winner, confirmReady, confirmGate)
			);
			Future<ReservationStatus> secondConfirmation = executor.submit(
					() -> confirmConcurrently(winner, confirmReady, confirmGate)
			);
			assertTrue(confirmReady.await(5, TimeUnit.SECONDS), "Both confirmations must reach the start gate");
			confirmGate.countDown();
			assertEquals(ReservationStatus.ACTIVE, firstConfirmation.get(10, TimeUnit.SECONDS));
			assertEquals(ReservationStatus.ACTIVE, secondConfirmation.get(10, TimeUnit.SECONDS));
		}

		Integer persistedReservations = jdbcTemplate.queryForObject(
				"SELECT count(*) FROM reservations WHERE spot_id = ? AND status = 'ACTIVE'",
				Integer.class,
				spot.getId()
		);
		assertEquals(1, persistedReservations);
	}

	private HoldAttempt tryCreateConcurrently(
			Driver driver,
			ParkingSpotEntity spot,
			Instant start,
			Instant end,
			CountDownLatch ready,
			CountDownLatch startGate
	) throws InterruptedException {
		ready.countDown();
		startGate.await();
		try {
			ReservationEntity hold = reservationService.createHold(
					driver.user().getId(), driver.vehicle().getId(), spot.getId(), at(start), at(end)
			);
			return new HoldAttempt(hold.getId(), driver.user().getId(), true);
		} catch (ReservationConflictException expected) {
			return new HoldAttempt(null, driver.user().getId(), false);
		}
	}

	private ReservationStatus confirmConcurrently(
			HoldAttempt winner,
			CountDownLatch ready,
			CountDownLatch startGate
	) throws InterruptedException {
		ready.countDown();
		startGate.await();
		return reservationService.confirmHold(winner.userId(), winner.holdId()).getStatus();
	}

	private ReservationEntity createFutureHold(Driver driver, ParkingSpotEntity spot) {
		Instant start = BASE_TIME.plus(Duration.ofHours(1));
		return reservationService.createHold(
				driver.user().getId(),
				driver.vehicle().getId(),
				spot.getId(),
				at(start),
				at(start.plus(Duration.ofHours(1)))
		);
	}

	private ParkingSpotAvailability availabilityFor(ParkingSpotEntity spot, ReservationEntity reservation) {
		return parkingSpotService.getAvailability(at(reservation.getStartAt()), at(reservation.getEndAt())).stream()
				.filter(candidate -> candidate.id().equals(spot.getId()))
				.findFirst()
				.orElseThrow();
	}

	private Driver createDriver(String email, String plate) {
		UserEntity user = userRepository.saveAndFlush(new UserEntity(email, "password-hash"));
		VehicleEntity vehicle = vehicleRepository.saveAndFlush(new VehicleEntity(user, plate, plate));
		return new Driver(user, vehicle);
	}

	private void insertHold(Driver driver, ParkingSpotEntity spot, Instant start, Instant end) {
		jdbcTemplate.update(
				"""
				INSERT INTO reservations (
				    user_id, vehicle_id, spot_id, start_at, end_at,
				    status, hold_expires_at, created_at
				) VALUES (?, ?, ?, ?, ?, 'HELD', ?, ?)
				""",
				driver.user().getId(),
				driver.vehicle().getId(),
				spot.getId(),
				at(start),
				at(end),
				at(BASE_TIME.plus(Duration.ofMinutes(3))),
				at(BASE_TIME)
		);
	}

	private ParkingSpotEntity spot(String code) {
		return parkingSpotRepository.findAllInNaturalCodeOrder().stream()
				.filter(candidate -> candidate.getCode().equals(code))
				.findFirst()
				.orElseThrow();
	}

	private void cleanReservationData() {
		jdbcTemplate.update("DELETE FROM reservations");
		jdbcTemplate.update("DELETE FROM vehicles");
		jdbcTemplate.update("DELETE FROM users");
	}

	private static OffsetDateTime at(Instant instant) {
		return instant.atOffset(ZoneOffset.UTC);
	}

	private record Driver(UserEntity user, VehicleEntity vehicle) {
	}

	private record HoldAttempt(Long holdId, Long userId, boolean created) {
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class TestClockConfiguration {

		@Bean
		@Primary
		MutableClock reservationTestClock() {
			return new MutableClock(BASE_TIME);
		}
	}

}
