package com.parking.backend.pricing;

import java.math.BigDecimal;

public record ParkingCostBreakdown(
		String currency,
		String pricingZoneId,
		long totalDurationSeconds,
		long dayDurationSeconds,
		long nightDurationSeconds,
		BigDecimal dayRatePerMinute,
		BigDecimal nightRatePerMinute,
		BigDecimal dayCost,
		BigDecimal nightCost,
		BigDecimal totalCost
) {
}
