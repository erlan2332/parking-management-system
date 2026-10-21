package com.parking.backend.pricing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Objects;

@ConfigurationProperties(prefix = "parking.pricing")
public record ParkingPricingProperties(
		ZoneId zoneId,
		LocalTime dayStart,
		LocalTime dayEnd,
		BigDecimal dayRatePerMinute,
		BigDecimal nightRatePerMinute
) {

	public ParkingPricingProperties {
		Objects.requireNonNull(zoneId, "Pricing zone ID must not be null");
		Objects.requireNonNull(dayStart, "Day tariff start must not be null");
		Objects.requireNonNull(dayEnd, "Day tariff end must not be null");
		Objects.requireNonNull(dayRatePerMinute, "Day rate must not be null");
		Objects.requireNonNull(nightRatePerMinute, "Night rate must not be null");
		if (!dayStart.isBefore(dayEnd)) {
			throw new IllegalArgumentException("Day tariff start must be earlier than day tariff end");
		}
		if (dayRatePerMinute.signum() < 0 || nightRatePerMinute.signum() < 0) {
			throw new IllegalArgumentException("Parking rates must not be negative");
		}
	}
}
