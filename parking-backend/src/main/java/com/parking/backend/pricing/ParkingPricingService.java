package com.parking.backend.pricing;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Objects;

@Service
public class ParkingPricingService {

	private static final String CURRENCY = "KGS";
	private static final BigDecimal SECONDS_PER_MINUTE = BigDecimal.valueOf(60);
	private static final int COMPONENT_SCALE = 6;
	private static final int TOTAL_SCALE = 2;

	private final ParkingPricingProperties properties;

	public ParkingPricingService(ParkingPricingProperties properties) {
		this.properties = Objects.requireNonNull(properties, "Pricing properties must not be null");
	}

	public ParkingCostBreakdown calculate(Instant enteredAt, Instant exitedAt) {
		Instant requiredEntry = Objects.requireNonNull(enteredAt, "Entry time must not be null");
		Instant requiredExit = Objects.requireNonNull(exitedAt, "Exit time must not be null");
		if (requiredExit.isBefore(requiredEntry)) {
			throw new IllegalArgumentException("Exit time must not be earlier than entry time");
		}

		TariffDurations durations = splitByTariff(requiredEntry, requiredExit);
		BigDecimal dayNumerator = costNumerator(durations.daySeconds(), properties.dayRatePerMinute());
		BigDecimal nightNumerator = costNumerator(durations.nightSeconds(), properties.nightRatePerMinute());
		BigDecimal dayCost = componentAmount(dayNumerator);
		BigDecimal nightCost = componentAmount(nightNumerator);
		BigDecimal totalCost = totalAmount(dayNumerator.add(nightNumerator));

		return new ParkingCostBreakdown(
				CURRENCY,
				properties.zoneId().getId(),
				durations.totalSeconds(),
				durations.daySeconds(),
				durations.nightSeconds(),
				properties.dayRatePerMinute(),
				properties.nightRatePerMinute(),
				dayCost,
				nightCost,
				totalCost
		);
	}

	private TariffDurations splitByTariff(Instant enteredAt, Instant exitedAt) {
		Duration dayDuration = Duration.ZERO;
		Duration nightDuration = Duration.ZERO;
		Instant cursor = enteredAt;

		while (cursor.isBefore(exitedAt)) {
			ZonedDateTime localCursor = cursor.atZone(properties.zoneId());
			boolean dayTariff = isDayTariff(localCursor.toLocalTime());
			Instant tariffBoundary = nextBoundary(localCursor, dayTariff);
			Instant segmentEnd = tariffBoundary.isBefore(exitedAt) ? tariffBoundary : exitedAt;
			Duration segmentDuration = Duration.between(cursor, segmentEnd);
			if (dayTariff) {
				dayDuration = dayDuration.plus(segmentDuration);
			} else {
				nightDuration = nightDuration.plus(segmentDuration);
			}
			cursor = segmentEnd;
		}

		long totalSeconds = Duration.between(enteredAt, exitedAt).getSeconds();
		long daySeconds = dayDuration.getSeconds();
		long nightSeconds = nightDuration.getSeconds();
		long unassignedSecond = totalSeconds - daySeconds - nightSeconds;
		if (unassignedSecond > 0) {
			if (dayDuration.getNano() >= nightDuration.getNano()) {
				daySeconds += unassignedSecond;
			} else {
				nightSeconds += unassignedSecond;
			}
		}
		return new TariffDurations(totalSeconds, daySeconds, nightSeconds);
	}

	private boolean isDayTariff(LocalTime localTime) {
		return !localTime.isBefore(properties.dayStart()) && localTime.isBefore(properties.dayEnd());
	}

	private Instant nextBoundary(ZonedDateTime cursor, boolean dayTariff) {
		LocalDate boundaryDate;
		LocalTime boundaryTime;
		if (dayTariff) {
			boundaryDate = cursor.toLocalDate();
			boundaryTime = properties.dayEnd();
		} else if (cursor.toLocalTime().isBefore(properties.dayStart())) {
			boundaryDate = cursor.toLocalDate();
			boundaryTime = properties.dayStart();
		} else {
			boundaryDate = cursor.toLocalDate().plusDays(1);
			boundaryTime = properties.dayStart();
		}
		ZoneId zoneId = properties.zoneId();
		return boundaryDate.atTime(boundaryTime).atZone(zoneId).toInstant();
	}

	private static BigDecimal costNumerator(long durationSeconds, BigDecimal ratePerMinute) {
		return BigDecimal.valueOf(durationSeconds).multiply(ratePerMinute);
	}

	private static BigDecimal componentAmount(BigDecimal numerator) {
		return numerator.divide(SECONDS_PER_MINUTE, COMPONENT_SCALE, RoundingMode.HALF_UP);
	}

	private static BigDecimal totalAmount(BigDecimal numerator) {
		return numerator.divide(SECONDS_PER_MINUTE, TOTAL_SCALE, RoundingMode.HALF_UP);
	}

	private record TariffDurations(long totalSeconds, long daySeconds, long nightSeconds) {
	}
}
