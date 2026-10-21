package com.parking.backend.visit.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record ParkingVisitCostResponse(
		Long visitId,
		String currency,
		Instant enteredAt,
		Instant exitedAt,
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
