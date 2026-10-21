package com.parking.backend.realtime;

import com.parking.backend.realtime.config.ParkingWebSocketConfig;
import com.parking.backend.realtime.event.ParkingAvailabilityChangedEvent;
import com.parking.backend.realtime.event.ParkingChangeReason;
import com.parking.backend.realtime.service.ParkingChangePublisher;

import com.parking.backend.barrier.service.BarrierService;
import com.parking.backend.common.exception.NoActiveVisitException;
import com.parking.backend.common.exception.ReservationConflictException;
import com.parking.backend.parking.model.ParkingSpotAvailability;
import com.parking.backend.parking.entity.ParkingSpotEntity;
import com.parking.backend.parking.repository.ParkingSpotRepository;
import com.parking.backend.parking.service.ParkingSpotService;
import com.parking.backend.parking.model.ParkingSpotStatus;
import com.parking.backend.reservation.entity.ReservationEntity;
import com.parking.backend.reservation.service.ReservationService;
import com.parking.backend.security.service.JwtService;
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
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import java.lang.reflect.Type;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "application.reservations.automation-delay=PT1H"
)
@ActiveProfiles("test")
@Import({
		ParkingRealtimeIntegrationTest.TestClockConfiguration.class,
		ParkingRealtimeIntegrationTest.RollbackProbeConfiguration.class
})
class ParkingRealtimeIntegrationTest {

	private static final Instant BASE_TIME = Instant.parse("2026-12-01T08:00:00Z");
	private static final String ALLOWED_ORIGIN = "http://localhost:5173";
	private static final Duration EVENT_TIMEOUT = Duration.ofSeconds(5);
	private static final Duration NO_EVENT_TIMEOUT = Duration.ofMillis(500);

	@LocalServerPort
	private int port;
	@Autowired
	private ReservationService reservationService;
	@Autowired
	private BarrierService barrierService;
	@Autowired
	private ParkingSpotService parkingSpotService;
	@Autowired
	private ParkingSpotRepository parkingSpotRepository;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private VehicleRepository vehicleRepository;
	@Autowired
	private JwtService jwtService;
	@Autowired
	private JdbcTemplate jdbcTemplate;
	@Autowired
	private MutableClock clock;
	@Autowired
	private RollbackProbe rollbackProbe;
	@Autowired
	private SubscriptionTracker subscriptionTracker;

	private final List<StompSession> sessions = new ArrayList<>();
	private WebSocketStompClient stompClient;

	@BeforeEach
	void setUp() {
		cleanBusinessData();
		jdbcTemplate.update("UPDATE parking_spots SET active = TRUE");
		clock.set(BASE_TIME);
		subscriptionTracker.reset();
		stompClient = new WebSocketStompClient(new StandardWebSocketClient());
		stompClient.setMessageConverter(new JacksonJsonMessageConverter());
		stompClient.setDefaultHeartbeat(new long[]{0, 0});
		stompClient.start();
	}

	@AfterEach
	void tearDown() {
		for (StompSession session : sessions) {
			if (session.isConnected()) {
				session.disconnect();
			}
		}
		stompClient.stop();
		cleanBusinessData();
		jdbcTemplate.update("UPDATE parking_spots SET active = TRUE");
	}

	@Test
	@Tag("smoke")
	void twoAuthenticatedClientsReceiveHoldAndConfirmationThenRefreshAvailability() throws Exception {
		Driver owner = createDriver("realtime-owner@example.com", "01KG951AAA");
		Driver observer = createDriver("realtime-observer@example.com", "01KG952AAA");
		EventSubscription ownerEvents = subscribe(connect(token(owner)));
		EventSubscription observerEvents = subscribe(connect(token(observer)));
		ParkingSpotEntity spot = spot("A2");
		Instant start = BASE_TIME.plus(Duration.ofHours(1));
		Instant end = start.plus(Duration.ofHours(2));

		ReservationEntity hold = reservationService.createHold(
				owner.user().getId(),
				owner.vehicle().getId(),
				spot.getId(),
				at(start),
				at(end)
		);

		assertEvent(ownerEvents, ParkingChangeReason.HOLD_CREATED, spot);
		assertEvent(observerEvents, ParkingChangeReason.HOLD_CREATED, spot);
		assertEquals(ParkingSpotStatus.HELD, availability(spot, start, end).status());

		reservationService.confirmHold(owner.user().getId(), hold.getId());

		assertEvent(ownerEvents, ParkingChangeReason.RESERVATION_CONFIRMED, spot);
		assertEvent(observerEvents, ParkingChangeReason.RESERVATION_CONFIRMED, spot);
		assertEquals(ParkingSpotStatus.RESERVED, availability(spot, start, end).status());
		reservationService.confirmHold(owner.user().getId(), hold.getId());
		assertNoEvent(observerEvents);
	}

	@Test
	void missingInvalidAndExpiredJwtConnectionsAreRejected() {
		assertConnectRejected(null, ALLOWED_ORIGIN);
		assertConnectRejected("not-a-jwt", ALLOWED_ORIGIN);

		clock.set(Instant.parse("2020-01-01T00:00:00Z"));
		String expiredToken = jwtService.issueToken(1L).value();
		clock.set(BASE_TIME);
		assertConnectRejected(expiredToken, ALLOWED_ORIGIN);
	}

	@Test
	void handshakeFromUnapprovedOriginIsRejected() {
		assertConnectRejected(jwtService.issueToken(1L).value(), "https://unapproved.example");
	}

	@Test
	void authenticatedClientCannotSubscribeToUnknownTopic() throws Exception {
		Driver driver = createDriver("unknown-topic@example.com", "01KG953AAA");
		ClientSession client = connect(token(driver));
		client.session().subscribe("/topic/private", frameHandler(new LinkedBlockingQueue<>()));

		assertTrue(client.handler().awaitError());
	}

	@Test
	void clientCannotPublishArbitraryMessagesToParkingTopic() throws Exception {
		Driver sender = createDriver("send-denied@example.com", "01KG954AAA");
		Driver observer = createDriver("send-observer@example.com", "01KG955AAA");
		ClientSession senderClient = connect(token(sender));
		EventSubscription observerEvents = subscribe(connect(token(observer)));

		senderClient.session().send(ParkingWebSocketConfig.CHANGES_TOPIC, new ParkingAvailabilityChangedEvent(
				ParkingAvailabilityChangedEvent.TYPE,
				ParkingChangeReason.HOLD_CREATED,
				999L,
				"FAKE",
				at(BASE_TIME)
		));

		assertTrue(senderClient.handler().awaitError());
		assertNoEvent(observerEvents);
	}

	@Test
	void releaseAndScheduledHoldExpiryPublishOnlyRealTransitions() throws Exception {
		Driver driver = createDriver("hold-events@example.com", "01KG956AAA");
		EventSubscription events = subscribe(connect(token(driver)));
		ReservationEntity releasedHold = createHold(driver, spot("A1"));
		assertEvent(events, ParkingChangeReason.HOLD_CREATED, spot("A1"));

		reservationService.releaseHold(driver.user().getId(), releasedHold.getId());
		assertEvent(events, ParkingChangeReason.HOLD_RELEASED, spot("A1"));
		reservationService.releaseHold(driver.user().getId(), releasedHold.getId());
		assertNoEvent(events);

		ReservationEntity expiringHold = createHold(driver, spot("A2"));
		assertEvent(events, ParkingChangeReason.HOLD_CREATED, spot("A2"));
		clock.set(expiringHold.getHoldExpiresAt());
		assertEquals(1, reservationService.expireHolds());
		assertEvent(events, ParkingChangeReason.HOLD_EXPIRED, spot("A2"));
		assertEquals(0, reservationService.expireHolds());
		assertNoEvent(events);
	}

	@Test
	void cancellationAndNoShowPublishOnlyRealTransitions() throws Exception {
		Driver cancelledDriver = createDriver("cancel-events@example.com", "01KG957AAA");
		EventSubscription events = subscribe(connect(token(cancelledDriver)));
		ReservationEntity cancelled = createConfirmedReservation(cancelledDriver, spot("A1"));
		assertEvent(events, ParkingChangeReason.HOLD_CREATED, spot("A1"));
		assertEvent(events, ParkingChangeReason.RESERVATION_CONFIRMED, spot("A1"));

		reservationService.cancelReservation(cancelledDriver.user().getId(), cancelled.getId());
		assertEvent(events, ParkingChangeReason.RESERVATION_CANCELLED, spot("A1"));
		reservationService.cancelReservation(cancelledDriver.user().getId(), cancelled.getId());
		assertNoEvent(events);

		Driver absentDriver = createDriver("no-show-events@example.com", "01KG958AAA");
		ReservationEntity noShow = createConfirmedReservation(absentDriver, spot("A2"));
		assertEvent(events, ParkingChangeReason.HOLD_CREATED, spot("A2"));
		assertEvent(events, ParkingChangeReason.RESERVATION_CONFIRMED, spot("A2"));
		clock.set(noShow.getStartAt().plus(Duration.ofMinutes(15)));
		assertEquals(1, reservationService.expireNoShows());
		assertEvent(events, ParkingChangeReason.RESERVATION_EXPIRED, spot("A2"));
		assertEquals(0, reservationService.expireNoShows());
		assertNoEvent(events);
	}

	@Test
	void vehicleEntryAndInvoiceBackedExitPublishOccupiedChanges() throws Exception {
		Driver driver = createDriver("barrier-events@example.com", "01KG959AAA");
		EventSubscription events = subscribe(connect(token(driver)));

		ParkingSpotEntity occupiedSpot = barrierService.enter(
				driver.user().getId(),
				driver.vehicle().getPlateNumber()
		).getSpot();
		assertEvent(events, ParkingChangeReason.VEHICLE_ENTERED, occupiedSpot);
		assertEquals(ParkingSpotStatus.OCCUPIED, parkingSpotService.getCurrentSpotState(occupiedSpot.getId()).status());

		clock.advance(Duration.ofMinutes(10));
		barrierService.exit(driver.user().getId(), driver.vehicle().getPlateNumber());
		assertEvent(events, ParkingChangeReason.VEHICLE_EXITED, occupiedSpot);
		assertEquals(ParkingSpotStatus.FREE, parkingSpotService.getCurrentSpotState(occupiedSpot.getId()).status());

		assertThrows(
				NoActiveVisitException.class,
				() -> barrierService.exit(driver.user().getId(), driver.vehicle().getPlateNumber())
		);
		assertNoEvent(events);
	}

	@Test
	void transactionRollbackSuppressesPublishedParkingEvent() throws Exception {
		Driver observer = createDriver("rollback-observer@example.com", "01KG960AAA");
		EventSubscription events = subscribe(connect(token(observer)));
		ParkingSpotEntity spot = spot("A1");

		assertThrows(IllegalStateException.class, () -> rollbackProbe.publishThenRollback(spot));

		assertNoEvent(events);
	}

	private ClientSession connect(String token) throws Exception {
		SessionHandler handler = new SessionHandler();
		StompSession session = connectFuture(token, ALLOWED_ORIGIN, handler)
				.get(EVENT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
		sessions.add(session);
		return new ClientSession(session, handler);
	}

	private void assertConnectRejected(String token, String origin) {
		SessionHandler handler = new SessionHandler();
		assertThrows(
				ExecutionException.class,
				() -> connectFuture(token, origin, handler)
						.get(EVENT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
		);
	}

	private java.util.concurrent.CompletableFuture<StompSession> connectFuture(
			String token,
			String origin,
			SessionHandler handler
	) {
		WebSocketHttpHeaders handshakeHeaders = new WebSocketHttpHeaders();
		handshakeHeaders.setOrigin(origin);
		StompHeaders connectHeaders = new StompHeaders();
		if (token != null) {
			connectHeaders.set(HttpHeaders.AUTHORIZATION, "Bearer " + token);
		}
		return stompClient.connectAsync(
				"ws://localhost:" + port + ParkingWebSocketConfig.ENDPOINT,
				handshakeHeaders,
				connectHeaders,
				handler
		);
	}

	private EventSubscription subscribe(ClientSession client) throws InterruptedException {
		BlockingQueue<ParkingAvailabilityChangedEvent> events = new LinkedBlockingQueue<>();
		client.session().subscribe(
				ParkingWebSocketConfig.CHANGES_TOPIC,
				frameHandler(events)
		);
		assertTrue(subscriptionTracker.awaitSubscription(
				ParkingWebSocketConfig.CHANGES_TOPIC
		));
		return new EventSubscription(events);
	}

	private static StompFrameHandler frameHandler(BlockingQueue<ParkingAvailabilityChangedEvent> events) {
		return new StompFrameHandler() {
			@Override
			public Type getPayloadType(StompHeaders headers) {
				return ParkingAvailabilityChangedEvent.class;
			}

			@Override
			public void handleFrame(StompHeaders headers, Object payload) {
				events.add((ParkingAvailabilityChangedEvent) payload);
			}
		};
	}

	private static void assertEvent(
			EventSubscription subscription,
			ParkingChangeReason reason,
			ParkingSpotEntity spot
	) throws InterruptedException {
		ParkingAvailabilityChangedEvent event = subscription.events().poll(
				EVENT_TIMEOUT.toMillis(),
				TimeUnit.MILLISECONDS
		);
		assertNotNull(event);
		assertEquals(ParkingAvailabilityChangedEvent.TYPE, event.type());
		assertEquals(reason, event.reason());
		assertEquals(spot.getId(), event.spotId());
		assertEquals(spot.getCode(), event.spotCode());
		assertNotNull(event.occurredAt());
	}

	private static void assertNoEvent(EventSubscription subscription) throws InterruptedException {
		assertNull(subscription.events().poll(NO_EVENT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS));
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

	private ReservationEntity createConfirmedReservation(Driver driver, ParkingSpotEntity spot) {
		ReservationEntity hold = createHold(driver, spot);
		return reservationService.confirmHold(driver.user().getId(), hold.getId());
	}

	private ParkingSpotAvailability availability(ParkingSpotEntity spot, Instant start, Instant end) {
		return parkingSpotService.getAvailability(at(start), at(end)).stream()
				.filter(candidate -> candidate.id().equals(spot.getId()))
				.findFirst()
				.orElseThrow();
	}

	private String token(Driver driver) {
		return jwtService.issueToken(driver.user().getId()).value();
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

	private record ClientSession(StompSession session, SessionHandler handler) {
	}

	private record EventSubscription(BlockingQueue<ParkingAvailabilityChangedEvent> events) {
	}

	static final class SessionHandler extends StompSessionHandlerAdapter {

		private final CountDownLatch error = new CountDownLatch(1);

		@Override
		public void handleException(
				StompSession session,
				StompCommand command,
				StompHeaders headers,
				byte[] payload,
				Throwable exception
		) {
			error.countDown();
		}

		@Override
		public void handleTransportError(StompSession session, Throwable exception) {
			error.countDown();
		}

		boolean awaitError() throws InterruptedException {
			return error.await(EVENT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
		}
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class TestClockConfiguration {

		@Bean
		@Primary
		MutableClock parkingRealtimeTestClock() {
			return new MutableClock(BASE_TIME);
		}
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class RollbackProbeConfiguration {

		@Bean
		RollbackProbe rollbackProbe(ParkingChangePublisher publisher) {
			return new RollbackProbe(publisher);
		}

		@Bean
		SubscriptionTracker subscriptionTracker() {
			return new SubscriptionTracker();
		}
	}

	static class SubscriptionTracker {

		private final BlockingQueue<SubscriptionNotice> subscriptions = new LinkedBlockingQueue<>();

		@EventListener
		public void onSubscribe(SessionSubscribeEvent event) {
			StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
			subscriptions.add(new SubscriptionNotice(accessor.getDestination()));
		}

		boolean awaitSubscription(String destination) throws InterruptedException {
			long deadline = System.nanoTime() + EVENT_TIMEOUT.toNanos();
			while (true) {
				long remaining = deadline - System.nanoTime();
				if (remaining <= 0) {
					return false;
				}
				SubscriptionNotice notice = subscriptions.poll(remaining, TimeUnit.NANOSECONDS);
				if (notice == null) {
					return false;
				}
				if (destination.equals(notice.destination())) {
					return true;
				}
			}
		}

		void reset() {
			subscriptions.clear();
		}
	}

	private record SubscriptionNotice(String destination) {
	}

	static class RollbackProbe {

		private final ParkingChangePublisher publisher;

		RollbackProbe(ParkingChangePublisher publisher) {
			this.publisher = publisher;
		}

		@Transactional
		public void publishThenRollback(ParkingSpotEntity spot) {
			publisher.publish(ParkingChangeReason.HOLD_CREATED, spot.getId(), spot.getCode());
			throw new IllegalStateException("simulated rollback");
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
