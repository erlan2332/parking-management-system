package com.parking.backend.visit.dto;

import com.parking.backend.pricing.model.ParkingCostBreakdown;
import com.parking.backend.visit.entity.ParkingVisitEntity;

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

	public static ParkingVisitCostResponse from(ParkingVisitEntity visit, ParkingCostBreakdown cost) {
		return new ParkingVisitCostResponse(
				visit.getId(),
				cost.currency(),
				visit.getEnteredAt(),
				visit.getExitedAt(),
				cost.totalDurationSeconds(),
				cost.dayDurationSeconds(),
				cost.nightDurationSeconds(),
				cost.dayRatePerMinute(),
				cost.nightRatePerMinute(),
				cost.dayCost(),
				cost.nightCost(),
				cost.totalCost()
		);
	}
}
