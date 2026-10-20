package com.parking.backend.pricing;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ParkingPricingServiceTest {

	private static final ZoneId BISHKEK = ZoneId.of("Asia/Bishkek");
	private static final BigDecimal DAY_RATE = new BigDecimal("2.00");
	private static final BigDecimal NIGHT_RATE = new BigDecimal("1.00");

	private final ParkingPricingService pricingService = service(DAY_RATE, NIGHT_RATE);

	@Test
	void calculatesDayOnlyParking() {
		ParkingCostBreakdown cost = calculate("2026-12-01T14:00:00+06:00", "2026-12-01T15:30:00+06:00");

		assertBreakdown(cost, 5_400, 5_400, 0, "180.00", "0.00", "180.00");
	}

	@Test
	void calculatesNightOnlyParking() {
		ParkingCostBreakdown cost = calculate("2026-12-01T22:00:00+06:00", "2026-12-01T23:30:00+06:00");

		assertBreakdown(cost, 5_400, 0, 5_400, "0.00", "90.00", "90.00");
	}

	@Test
	void splitsParkingAtDayToNightBoundary() {
		ParkingCostBreakdown cost = calculate("2026-12-01T19:30:00+06:00", "2026-12-01T20:30:00+06:00");

		assertBreakdown(cost, 3_600, 1_800, 1_800, "60.00", "30.00", "90.00");
	}

	@Test
	void splitsParkingAtNightToDayBoundary() {
		ParkingCostBreakdown cost = calculate("2026-12-01T07:30:00+06:00", "2026-12-01T08:30:00+06:00");

		assertBreakdown(cost, 3_600, 1_800, 1_800, "60.00", "30.00", "90.00");
	}

	@Test
	void calculatesParkingAcrossMidnight() {
		ParkingCostBreakdown cost = calculate("2026-12-01T19:00:00+06:00", "2026-12-02T09:00:00+06:00");

		assertBreakdown(cost, 50_400, 7_200, 43_200, "240.00", "720.00", "960.00");
	}

	@Test
	void appliesTariffsForEveryDayOfMultiDayParking() {
		ParkingCostBreakdown cost = calculate("2026-12-01T07:00:00+06:00", "2026-12-03T09:00:00+06:00");

		assertBreakdown(cost, 180_000, 90_000, 90_000, "3000.00", "1500.00", "4500.00");
	}

	@Test
	void treatsBoundariesAsHalfOpenInConfiguredZoneRegardlessOfInputOffset() {
		ParkingCostBreakdown fullDay = calculate("2026-12-01T05:00:00+03:00", "2026-12-01T17:00:00+03:00");
		ParkingCostBreakdown fromDayEnd = calculate("2026-12-01T20:00:00+06:00", "2026-12-01T21:00:00+06:00");

		assertBreakdown(fullDay, 43_200, 43_200, 0, "1440.00", "0.00", "1440.00");
		assertBreakdown(fromDayEnd, 3_600, 0, 3_600, "0.00", "60.00", "60.00");
	}

	@Test
	void billsPartialMinutesBySecondsAndRoundsOnlyTheCombinedTotal() {
		ParkingCostBreakdown thirtySeconds = calculate(
				"2026-12-01T14:00:00+06:00",
				"2026-12-01T14:00:30+06:00"
		);
		ParkingPricingService equalRateService = service(new BigDecimal("1.50"), new BigDecimal("1.50"));
		ParkingCostBreakdown acrossBoundary = equalRateService.calculate(
				instant("2026-12-01T19:59:59+06:00"),
				instant("2026-12-01T20:00:01+06:00")
		);

		assertBreakdown(thirtySeconds, 30, 30, 0, "1.00", "0.00", "1.00");
		assertEquals(new BigDecimal("0.03"), acrossBoundary.dayCost());
		assertEquals(new BigDecimal("0.03"), acrossBoundary.nightCost());
		assertEquals(new BigDecimal("0.05"), acrossBoundary.totalCost());
	}

	@Test
	void rejectsInvalidIntervalsAndConfigurationButAllowsZeroDuration() {
		Instant instant = instant("2026-12-01T14:00:00+06:00");

		assertThrows(NullPointerException.class, () -> pricingService.calculate(null, instant));
		assertThrows(NullPointerException.class, () -> pricingService.calculate(instant, null));
		assertThrows(IllegalArgumentException.class, () -> pricingService.calculate(instant.plusSeconds(1), instant));
		assertThrows(IllegalArgumentException.class, () -> service(new BigDecimal("-0.01"), NIGHT_RATE));
		assertThrows(IllegalArgumentException.class, () -> new ParkingPricingProperties(
				BISHKEK,
				LocalTime.of(8, 0),
				LocalTime.of(8, 0),
				DAY_RATE,
				NIGHT_RATE
		));

		assertBreakdown(pricingService.calculate(instant, instant), 0, 0, 0, "0.00", "0.00", "0.00");
	}

	private ParkingCostBreakdown calculate(String enteredAt, String exitedAt) {
		return pricingService.calculate(instant(enteredAt), instant(exitedAt));
	}

	private static Instant instant(String value) {
		return OffsetDateTime.parse(value).toInstant();
	}

	private static ParkingPricingService service(BigDecimal dayRate, BigDecimal nightRate) {
		return new ParkingPricingService(new ParkingPricingProperties(
				BISHKEK,
				LocalTime.of(8, 0),
				LocalTime.of(20, 0),
				dayRate,
				nightRate
		));
	}

	private static void assertBreakdown(
			ParkingCostBreakdown cost,
			long totalSeconds,
			long daySeconds,
			long nightSeconds,
			String dayCost,
			String nightCost,
			String totalCost
	) {
		assertEquals("KGS", cost.currency());
		assertEquals(totalSeconds, cost.totalDurationSeconds());
		assertEquals(daySeconds, cost.dayDurationSeconds());
		assertEquals(nightSeconds, cost.nightDurationSeconds());
		assertEquals(totalSeconds, cost.dayDurationSeconds() + cost.nightDurationSeconds());
		assertEquals(DAY_RATE, cost.dayRatePerMinute());
		assertEquals(NIGHT_RATE, cost.nightRatePerMinute());
		assertEquals(new BigDecimal(dayCost), cost.dayCost());
		assertEquals(new BigDecimal(nightCost), cost.nightCost());
		assertEquals(new BigDecimal(totalCost), cost.totalCost());
	}
}
