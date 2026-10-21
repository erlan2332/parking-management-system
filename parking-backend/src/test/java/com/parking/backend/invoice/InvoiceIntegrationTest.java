package com.parking.backend.invoice;

import com.parking.backend.support.MutableClock;

import com.parking.backend.invoice.entity.InvoiceEntity;
import com.parking.backend.invoice.model.InvoiceStatus;
import com.parking.backend.invoice.repository.InvoiceRepository;
import com.parking.backend.invoice.service.InvoiceService;

import com.parking.backend.barrier.model.BarrierExitResult;
import com.parking.backend.barrier.service.BarrierService;
import com.parking.backend.common.exception.NoActiveVisitException;
import com.parking.backend.parking.entity.ParkingSpotEntity;
import com.parking.backend.parking.repository.ParkingSpotRepository;
import com.parking.backend.pricing.model.ParkingCostBreakdown;
import com.parking.backend.pricing.config.ParkingPricingProperties;
import com.parking.backend.pricing.service.ParkingPricingService;
import com.parking.backend.user.entity.UserEntity;
import com.parking.backend.user.repository.UserRepository;
import com.parking.backend.vehicle.entity.VehicleEntity;
import com.parking.backend.vehicle.repository.VehicleRepository;
import com.parking.backend.visit.entity.ParkingVisitEntity;
import com.parking.backend.visit.repository.ParkingVisitRepository;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Import(InvoiceIntegrationTest.TestClockConfiguration.class)
class InvoiceIntegrationTest {

	private static final Instant ENTRY_TIME = Instant.parse("2026-12-01T13:30:00Z");

	@Autowired
	private BarrierService barrierService;
	@Autowired
	private InvoiceService invoiceService;
	@Autowired
	private InvoiceRepository invoiceRepository;
	@Autowired
	private ParkingVisitRepository parkingVisitRepository;
	@Autowired
	private ParkingSpotRepository parkingSpotRepository;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private VehicleRepository vehicleRepository;
	@Autowired
	private JdbcTemplate jdbcTemplate;
	@Autowired
	private PlatformTransactionManager transactionManager;
	@Autowired
	private MutableClock clock;

	@BeforeEach
	void setUp() {
		dropRejectingTrigger();
		cleanBusinessData();
		jdbcTemplate.update("UPDATE parking_spots SET active = TRUE");
		clock.set(ENTRY_TIME);
	}

	@AfterEach
	void tearDown() {
		dropRejectingTrigger();
		cleanBusinessData();
		jdbcTemplate.update("UPDATE parking_spots SET active = TRUE");
	}

	@Test
	@Tag("smoke")
	void exitAtomicallyIssuesInvoiceWithPricingSnapshot() {
		Driver driver = createDriver("invoice-exit@example.com", "01KG901AAA");
		ParkingVisitEntity visit = barrierService.enter(driver.user().getId(), driver.vehicle().getPlateNumber());
		clock.advance(Duration.ofHours(1));

		BarrierExitResult result = barrierService.exit(driver.user().getId(), driver.vehicle().getPlateNumber());
		InvoiceEntity invoice = result.invoice();

		assertEquals(visit.getId(), result.visit().getId());
		assertEquals(InvoiceStatus.ISSUED, invoice.getStatus());
		assertEquals("INV-%08d".formatted(invoice.getId()), invoice.getInvoiceNumber());
		assertEquals(visit.getId(), invoice.getVisit().getId());
		assertEquals("01KG901AAA", invoice.getVehiclePlate());
		assertEquals("A1", invoice.getSpotCode());
		assertEquals("Asia/Bishkek", invoice.getPricingZoneId());
		assertEquals(3_600, invoice.getTotalDurationSeconds());
		assertEquals(1_800, invoice.getDayDurationSeconds());
		assertEquals(1_800, invoice.getNightDurationSeconds());
		assertDecimal("2.000000", invoice.getDayRatePerMinute());
		assertDecimal("1.000000", invoice.getNightRatePerMinute());
		assertDecimal("60.000000", invoice.getDayCost());
		assertDecimal("30.000000", invoice.getNightCost());
		assertDecimal("90.00", invoice.getTotalAmount());
		assertEquals("KGS", invoice.getCurrency());
		assertEquals(result.visit().getExitedAt(), invoice.getIssuedAt());
		assertEquals(1, invoiceRepository.count());
	}

	@Test
	void changingCurrentRatesCannotChangeIssuedInvoice() {
		Driver driver = createDriver("invoice-history@example.com", "01KG902AAA");
		barrierService.enter(driver.user().getId(), driver.vehicle().getPlateNumber());
		clock.advance(Duration.ofHours(1));
		BarrierExitResult result = barrierService.exit(driver.user().getId(), driver.vehicle().getPlateNumber());
		InvoiceEntity issued = result.invoice();

		ParkingPricingService changedPricing = new ParkingPricingService(new ParkingPricingProperties(
				ZoneId.of("Asia/Bishkek"),
				LocalTime.of(8, 0),
				LocalTime.of(20, 0),
				new BigDecimal("3.00"),
				new BigDecimal("4.00")
		));
		ParkingCostBreakdown recalculated = changedPricing.calculate(
				result.visit().getEnteredAt(),
				result.visit().getExitedAt()
		);
		InvoiceEntity reloaded = invoiceRepository.findById(issued.getId()).orElseThrow();

		assertDecimal("210.00", recalculated.totalCost());
		assertNotEquals(recalculated.totalCost(), reloaded.getTotalAmount());
		assertDecimal("90.00", reloaded.getTotalAmount());
		assertDecimal("2.000000", reloaded.getDayRatePerMinute());
		assertDecimal("1.000000", reloaded.getNightRatePerMinute());
	}

	@Test
	void repeatedIssuanceReturnsExistingInvoiceAndDatabaseRejectsDuplicateVisit() {
		Driver driver = createDriver("invoice-duplicate@example.com", "01KG903AAA");
		barrierService.enter(driver.user().getId(), driver.vehicle().getPlateNumber());
		clock.advance(Duration.ofMinutes(15));
		BarrierExitResult result = barrierService.exit(driver.user().getId(), driver.vehicle().getPlateNumber());
		TransactionTemplate transactions = new TransactionTemplate(transactionManager);

		InvoiceEntity repeated = transactions.execute(status -> invoiceService.issueForVisit(
				parkingVisitRepository.findById(result.visit().getId()).orElseThrow(),
				result.visit().getExitedAt()
		));
		assertEquals(result.invoice().getId(), repeated.getId());
		assertEquals(1, invoiceRepository.count());

		assertThrows(DataIntegrityViolationException.class, () -> transactions.executeWithoutResult(status ->
				jdbcTemplate.update(
						"""
						INSERT INTO invoices (
						    visit_id, status, issued_at, entered_at, exited_at, vehicle_plate, spot_code,
						    pricing_zone_id, currency, total_duration_seconds, day_duration_seconds,
						    night_duration_seconds, day_rate_per_minute, night_rate_per_minute,
						    day_cost, night_cost, total_amount
						)
						SELECT visit_id, status, issued_at, entered_at, exited_at, vehicle_plate, spot_code,
						       pricing_zone_id, currency, total_duration_seconds, day_duration_seconds,
						       night_duration_seconds, day_rate_per_minute, night_rate_per_minute,
						       day_cost, night_cost, total_amount
						FROM invoices WHERE id = ?
						""",
						result.invoice().getId()
				)
		));
		assertEquals(1, invoiceRepository.count());
	}

	@Test
	@Tag("smoke")
	void concurrentExitRequestsProduceExactlyOneInvoice() throws Exception {
		Driver driver = createDriver("invoice-concurrent@example.com", "01KG904AAA");
		ParkingVisitEntity visit = barrierService.enter(driver.user().getId(), driver.vehicle().getPlateNumber());
		clock.advance(Duration.ofHours(1));
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch startGate = new CountDownLatch(1);

		try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
			Future<Boolean> first = executor.submit(() -> exitConcurrently(driver, ready, startGate));
			Future<Boolean> second = executor.submit(() -> exitConcurrently(driver, ready, startGate));
			assertTrue(ready.await(5, TimeUnit.SECONDS));
			startGate.countDown();
			int successes = (first.get(10, TimeUnit.SECONDS) ? 1 : 0)
					+ (second.get(10, TimeUnit.SECONDS) ? 1 : 0);
			assertEquals(1, successes);
		}

		assertEquals(1, invoiceRepository.count());
		assertEquals(1, jdbcTemplate.queryForObject(
				"SELECT count(*) FROM invoices WHERE visit_id = ?",
				Integer.class,
				visit.getId()
		));
		assertEquals(1, jdbcTemplate.queryForObject(
				"SELECT count(*) FROM parking_visits WHERE id = ? AND exited_at IS NOT NULL",
				Integer.class,
				visit.getId()
		));
	}

	@Test
	void invoiceInsertFailureRollsBackVisitCompletion() {
		Driver driver = createDriver("invoice-rollback@example.com", "01KG905AAA");
		ParkingVisitEntity visit = barrierService.enter(driver.user().getId(), driver.vehicle().getPlateNumber());
		clock.advance(Duration.ofMinutes(10));
		createRejectingTrigger();

		try {
			assertThrows(
					RuntimeException.class,
					() -> barrierService.exit(driver.user().getId(), driver.vehicle().getPlateNumber())
			);
		} finally {
			dropRejectingTrigger();
		}

		assertNull(parkingVisitRepository.findById(visit.getId()).orElseThrow().getExitedAt());
		assertEquals(0, invoiceRepository.count());
	}

	@Test
	void databaseRejectsInvalidDurationAndNegativeRateSnapshots() {
		Driver driver = createDriver("invoice-constraints@example.com", "01KG906AAA");
		ParkingVisitEntity visit = ParkingVisitEntity.walkIn(driver.vehicle(), spot(), ENTRY_TIME);
		visit.complete(ENTRY_TIME.plusSeconds(60));
		parkingVisitRepository.saveAndFlush(visit);
		TransactionTemplate transactions = new TransactionTemplate(transactionManager);

		assertThrows(DataIntegrityViolationException.class, () -> insertInvoice(
				transactions,
				visit.getId(),
				60,
				30,
				20,
				new BigDecimal("2.00")
		));
		assertThrows(DataIntegrityViolationException.class, () -> insertInvoice(
				transactions,
				visit.getId(),
				60,
				60,
				0,
				new BigDecimal("-1.00")
		));
		assertEquals(0, invoiceRepository.count());
	}

	private boolean exitConcurrently(
			Driver driver,
			CountDownLatch ready,
			CountDownLatch startGate
	) throws InterruptedException {
		ready.countDown();
		startGate.await();
		try {
			barrierService.exit(driver.user().getId(), driver.vehicle().getPlateNumber());
			return true;
		} catch (NoActiveVisitException expected) {
			return false;
		}
	}

	private void insertInvoice(
			TransactionTemplate transactions,
			Long visitId,
			long totalSeconds,
			long daySeconds,
			long nightSeconds,
			BigDecimal dayRate
	) {
		transactions.executeWithoutResult(status -> jdbcTemplate.update(
				"""
				INSERT INTO invoices (
				    visit_id, status, issued_at, entered_at, exited_at, vehicle_plate, spot_code,
				    pricing_zone_id, currency, total_duration_seconds, day_duration_seconds,
				    night_duration_seconds, day_rate_per_minute, night_rate_per_minute,
				    day_cost, night_cost, total_amount
				) VALUES (?, 'ISSUED', ?, ?, ?, '01KG906AAA', 'A1', 'Asia/Bishkek', 'KGS',
				          ?, ?, ?, ?, 1.00, 2.00, 1.00, 3.00)
				""",
				visitId,
				ENTRY_TIME.plusSeconds(60).atOffset(ZoneOffset.UTC),
				ENTRY_TIME.atOffset(ZoneOffset.UTC),
				ENTRY_TIME.plusSeconds(60).atOffset(ZoneOffset.UTC),
				totalSeconds,
				daySeconds,
				nightSeconds,
				dayRate
		));
	}

	private Driver createDriver(String email, String plate) {
		UserEntity user = userRepository.saveAndFlush(new UserEntity(email, "password-hash"));
		VehicleEntity vehicle = vehicleRepository.saveAndFlush(new VehicleEntity(user, plate, plate));
		return new Driver(user, vehicle);
	}

	private ParkingSpotEntity spot() {
		return parkingSpotRepository.findAllInNaturalCodeOrder().getFirst();
	}

	private void createRejectingTrigger() {
		jdbcTemplate.execute("""
				CREATE FUNCTION reject_stage08_invoice() RETURNS trigger
				LANGUAGE plpgsql AS $$
				BEGIN
				    RAISE EXCEPTION 'forced invoice persistence failure';
				END;
				$$
				""");
		jdbcTemplate.execute("""
				CREATE TRIGGER reject_stage08_invoice_insert
				BEFORE INSERT ON invoices
				FOR EACH ROW EXECUTE FUNCTION reject_stage08_invoice()
				""");
	}

	private void dropRejectingTrigger() {
		jdbcTemplate.execute("DROP TRIGGER IF EXISTS reject_stage08_invoice_insert ON invoices");
		jdbcTemplate.execute("DROP FUNCTION IF EXISTS reject_stage08_invoice()");
	}

	private void cleanBusinessData() {
		jdbcTemplate.update("DELETE FROM invoices");
		jdbcTemplate.update("DELETE FROM parking_visits");
		jdbcTemplate.update("DELETE FROM reservations");
		jdbcTemplate.update("DELETE FROM vehicles");
		jdbcTemplate.update("DELETE FROM users");
	}

	private static void assertDecimal(String expected, BigDecimal actual) {
		assertEquals(0, new BigDecimal(expected).compareTo(actual));
	}

	private record Driver(UserEntity user, VehicleEntity vehicle) {
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class TestClockConfiguration {

		@Bean
		@Primary
		MutableClock invoiceTestClock() {
			return new MutableClock(ENTRY_TIME);
		}
	}

}
