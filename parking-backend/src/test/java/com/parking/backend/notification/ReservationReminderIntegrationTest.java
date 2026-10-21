package com.parking.backend.notification;

import com.parking.backend.support.MutableClock;

import com.parking.backend.notification.entity.ReservationNotificationEntity;
import com.parking.backend.notification.model.ReservationNotificationStatus;
import com.parking.backend.notification.model.ReservationNotificationType;
import com.parking.backend.notification.model.ReservationReminderMessage;
import com.parking.backend.notification.repository.ReservationNotificationRepository;
import com.parking.backend.notification.sender.ReservationEmailSender;
import com.parking.backend.notification.service.ReservationReminderService;

import com.parking.backend.barrier.service.BarrierService;
import com.parking.backend.parking.entity.ParkingSpotEntity;
import com.parking.backend.parking.repository.ParkingSpotRepository;
import com.parking.backend.reservation.entity.ReservationEntity;
import com.parking.backend.reservation.repository.ReservationRepository;
import com.parking.backend.reservation.service.ReservationService;
import com.parking.backend.reservation.service.ReservationExpirationService;
import com.parking.backend.reservation.model.ReservationStatus;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "application.reservations.automation-delay=PT1H")
@ActiveProfiles("test")
@Import({
		ReservationReminderIntegrationTest.TestClockConfiguration.class,
		ReservationReminderIntegrationTest.FakeEmailConfiguration.class
})
class ReservationReminderIntegrationTest {

	private static final Instant BASE_TIME = Instant.parse("2026-12-01T08:00:00Z");

	@Autowired
	private ReservationReminderService reminderService;
	@Autowired
	private ReservationNotificationRepository notificationRepository;
	@Autowired
	private ReservationService reservationService;
	@Autowired
	private ReservationExpirationService reservationExpirationService;
	@Autowired
	private ReservationRepository reservationRepository;
	@Autowired
	private BarrierService barrierService;
	@Autowired
	private ParkingSpotRepository parkingSpotRepository;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private VehicleRepository vehicleRepository;
	@Autowired
	private JdbcTemplate jdbcTemplate;
	@Autowired
	private FakeReservationEmailSender emailSender;
	@Autowired
	private MutableClock clock;

	@BeforeEach
	void setUp() {
		cleanBusinessData();
		jdbcTemplate.update("UPDATE parking_spots SET active = TRUE");
		clock.set(BASE_TIME);
		emailSender.reset();
	}

	@AfterEach
	void tearDown() {
		emailSender.releaseBlockedSend();
		cleanBusinessData();
		jdbcTemplate.update("UPDATE parking_spots SET active = TRUE");
	}

	@Test
	@Tag("smoke")
	void sendsOneReminderWithOwnerDataAndNeverSendsItAgain() {
		CheckedInBooking booking = createCheckedInBooking(
				"reminder-owner@example.com",
				"01KG941AAA",
				"A2"
		);
		clock.set(booking.reservation().getEndAt().minus(Duration.ofMinutes(10)));

		assertEquals(1, reminderService.processDueReminders());
		assertEquals(0, reminderService.processDueReminders());

		ReservationNotificationEntity notification = notification(booking.reservation());
		ReservationReminderMessage message = emailSender.lastMessage();
		assertEquals(1, emailSender.attemptCount());
		assertEquals(ReservationNotificationStatus.SENT, notification.getStatus());
		assertEquals(1, notification.getAttemptCount());
		assertNotNull(notification.getSentAt());
		assertEquals("reminder-owner@example.com", message.recipient());
		assertEquals("Parking Management System — бронирование скоро заканчивается", message.subject());
		assertTrue(message.body().contains("Автомобиль: 01KG941AAA"));
		assertTrue(message.body().contains("Парковочное место: A2"));
		assertTrue(message.body().contains("Плановое окончание: 17:00 (Asia/Bishkek)"));
	}

	@Test
	void activeCancelledAndExpiredReservationsDoNotCreateReminders() {
		Driver activeDriver = createDriver("active-no-mail@example.com", "01KG942AAA");
		ReservationEntity active = createConfirmedReservation(activeDriver, spot("A1"));
		Driver cancelledDriver = createDriver("cancelled-no-mail@example.com", "01KG943AAA");
		ReservationEntity cancelled = createConfirmedReservation(cancelledDriver, spot("A2"));
		reservationService.cancelReservation(cancelledDriver.user().getId(), cancelled.getId());
		Driver expiredDriver = createDriver("expired-no-mail@example.com", "01KG944AAA");
		ReservationEntity expired = createConfirmedReservation(expiredDriver, spot("A3"));
		clock.set(expired.getStartAt().plus(Duration.ofMinutes(15)));
		reservationExpirationService.expireDueNoShows();

		clock.set(active.getEndAt().minus(Duration.ofMinutes(10)));

		assertEquals(0, reminderService.processDueReminders());
		assertEquals(0, emailSender.attemptCount());
		assertEquals(0, notificationRepository.count());
		assertEquals(ReservationStatus.EXPIRED, reload(active).getStatus());
		assertEquals(ReservationStatus.CANCELLED, reload(cancelled).getStatus());
		assertEquals(ReservationStatus.EXPIRED, reload(expired).getStatus());
	}

	@Test
	void earlyExitAndRestartAfterEndDoNotSendStaleReminder() {
		CheckedInBooking completed = createCheckedInBooking(
				"early-exit@example.com",
				"01KG945AAA",
				"A1"
		);
		clock.set(completed.reservation().getEndAt().minus(Duration.ofMinutes(30)));
		barrierService.exit(completed.driver().user().getId(), completed.driver().vehicle().getPlateNumber());
		clock.set(completed.reservation().getEndAt().minus(Duration.ofMinutes(10)));
		assertEquals(0, reminderService.processDueReminders());

		clock.set(BASE_TIME);
		CheckedInBooking overdue = createCheckedInBooking(
				"overdue-mail@example.com",
				"01KG946AAA",
				"A2"
		);
		clock.set(overdue.reservation().getEndAt());

		assertEquals(0, reminderService.processDueReminders());
		assertEquals(0, emailSender.attemptCount());
		assertEquals(0, notificationRepository.count());
	}

	@Test
	void smtpFailureKeepsDomainStateAndRetriesOnlyInsideWindow() {
		CheckedInBooking booking = createCheckedInBooking(
				"retry-mail@example.com",
				"01KG947AAA",
				"A1"
		);
		clock.set(booking.reservation().getEndAt().minus(Duration.ofMinutes(10)));
		emailSender.failWith(new IllegalStateException("simulated SMTP outage"));

		assertEquals(0, reminderService.processDueReminders());
		ReservationNotificationEntity failed = notification(booking.reservation());
		assertEquals(ReservationNotificationStatus.FAILED, failed.getStatus());
		assertEquals(1, failed.getAttemptCount());
		assertEquals("Email delivery failed: IllegalStateException", failed.getLastError());
		assertEquals(ReservationStatus.CHECKED_IN, reload(booking.reservation()).getStatus());
		assertEquals(1, jdbcTemplate.queryForObject(
				"SELECT count(*) FROM parking_visits WHERE reservation_id = ? AND exited_at IS NULL",
				Integer.class,
				booking.reservation().getId()
		));

		clock.advance(Duration.ofSeconds(1));
		emailSender.failWith(null);
		assertEquals(1, reminderService.processDueReminders());
		ReservationNotificationEntity sent = notification(booking.reservation());
		assertEquals(ReservationNotificationStatus.SENT, sent.getStatus());
		assertEquals(2, sent.getAttemptCount());
	}

	@Test
	void concurrentProcessorsCannotSendTheSameReminderInParallel() throws Exception {
		CheckedInBooking booking = createCheckedInBooking(
				"concurrent-mail@example.com",
				"01KG948AAA",
				"A1"
		);
		clock.set(booking.reservation().getEndAt().minus(Duration.ofMinutes(10)));
		emailSender.blockNextSend();

		try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
			Future<Integer> first = executor.submit(reminderService::processDueReminders);
			assertTrue(emailSender.awaitSendStarted());
			Future<Integer> second = executor.submit(reminderService::processDueReminders);
			assertEquals(0, second.get(10, TimeUnit.SECONDS));
			emailSender.releaseBlockedSend();
			assertEquals(1, first.get(10, TimeUnit.SECONDS));
		}

		assertEquals(1, emailSender.attemptCount());
		assertEquals(1, notificationRepository.count());
		assertEquals(ReservationNotificationStatus.SENT, notification(booking.reservation()).getStatus());
	}

	@Test
	void staleSendingClaimIsRecoveredAfterRestartWithoutCreatingDuplicateRow() {
		CheckedInBooking booking = createCheckedInBooking(
				"stale-claim@example.com",
				"01KG949AAA",
				"A1"
		);
		clock.set(booking.reservation().getEndAt().minus(Duration.ofMinutes(10)));
		jdbcTemplate.update(
				"""
				INSERT INTO reservation_notifications (
				    reservation_id, notification_type, status, created_at,
				    processing_started_at, attempt_count
				) VALUES (?, 'RESERVATION_ENDING_SOON', 'SENDING', ?, ?, 1)
				""",
				booking.reservation().getId(),
				at(clock.instant().minus(Duration.ofMinutes(4))),
				at(clock.instant().minus(Duration.ofMinutes(3)))
		);

		assertEquals(1, reminderService.processDueReminders());

		ReservationNotificationEntity notification = notification(booking.reservation());
		assertEquals(1, notificationRepository.count());
		assertEquals(ReservationNotificationStatus.SENT, notification.getStatus());
		assertEquals(2, notification.getAttemptCount());
		assertEquals(1, emailSender.attemptCount());
	}

	private CheckedInBooking createCheckedInBooking(String email, String plate, String spotCode) {
		Driver driver = createDriver(email, plate);
		ReservationEntity reservation = createConfirmedReservation(driver, spot(spotCode));
		clock.set(reservation.getStartAt().plus(Duration.ofMinutes(5)));
		barrierService.enter(driver.user().getId(), driver.vehicle().getPlateNumber());
		return new CheckedInBooking(driver, reservation);
	}

	private ReservationEntity createConfirmedReservation(Driver driver, ParkingSpotEntity spot) {
		Instant start = BASE_TIME.plus(Duration.ofHours(1));
		ReservationEntity hold = reservationService.createHold(
				driver.user().getId(),
				driver.vehicle().getId(),
				spot.getId(),
				at(start),
				at(start.plus(Duration.ofHours(2)))
		);
		return reservationService.confirmHold(driver.user().getId(), hold.getId());
	}

	private ReservationNotificationEntity notification(ReservationEntity reservation) {
		return notificationRepository.findByReservation_IdAndNotificationType(
				reservation.getId(),
				ReservationNotificationType.RESERVATION_ENDING_SOON
		).orElseThrow();
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

	private record CheckedInBooking(Driver driver, ReservationEntity reservation) {
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class TestClockConfiguration {

		@Bean
		@Primary
		MutableClock reservationReminderTestClock() {
			return new MutableClock(BASE_TIME);
		}
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class FakeEmailConfiguration {

		@Bean
		@Primary
		FakeReservationEmailSender fakeReservationEmailSender() {
			return new FakeReservationEmailSender();
		}
	}

	static final class FakeReservationEmailSender implements ReservationEmailSender {

		private final AtomicInteger attempts = new AtomicInteger();
		private final AtomicReference<ReservationReminderMessage> lastMessage = new AtomicReference<>();
		private final AtomicReference<RuntimeException> failure = new AtomicReference<>();
		private volatile CountDownLatch sendStarted = new CountDownLatch(0);
		private volatile CountDownLatch sendRelease = new CountDownLatch(0);

		@Override
		public boolean isEnabled() {
			return true;
		}

		@Override
		public void send(ReservationReminderMessage message) {
			attempts.incrementAndGet();
			lastMessage.set(message);
			sendStarted.countDown();
			try {
				if (!sendRelease.await(10, TimeUnit.SECONDS)) {
					throw new IllegalStateException("Timed out waiting to release fake email send");
				}
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException("Fake email send was interrupted", exception);
			}
			RuntimeException configuredFailure = failure.get();
			if (configuredFailure != null) {
				throw configuredFailure;
			}
		}

		void reset() {
			attempts.set(0);
			lastMessage.set(null);
			failure.set(null);
			sendStarted = new CountDownLatch(0);
			sendRelease = new CountDownLatch(0);
		}

		void failWith(RuntimeException exception) {
			failure.set(exception);
		}

		void blockNextSend() {
			sendStarted = new CountDownLatch(1);
			sendRelease = new CountDownLatch(1);
		}

		boolean awaitSendStarted() throws InterruptedException {
			return sendStarted.await(5, TimeUnit.SECONDS);
		}

		void releaseBlockedSend() {
			sendRelease.countDown();
		}

		int attemptCount() {
			return attempts.get();
		}

		ReservationReminderMessage lastMessage() {
			return lastMessage.get();
		}
	}

}
