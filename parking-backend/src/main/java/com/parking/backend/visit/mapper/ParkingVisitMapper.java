package com.parking.backend.visit.mapper;

import com.parking.backend.pricing.model.ParkingCostBreakdown;
import com.parking.backend.visit.dto.ParkingVisitCostResponse;
import com.parking.backend.visit.dto.ParkingVisitResponse;
import com.parking.backend.visit.entity.ParkingVisitEntity;
import com.parking.backend.visit.model.ParkingVisitCost;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
public class ParkingVisitMapper {

	public ParkingVisitResponse toResponse(ParkingVisitEntity visit) {
		Long durationSeconds = visit.getExitedAt() == null
				? null
				: Duration.between(visit.getEnteredAt(), visit.getExitedAt()).getSeconds();
		return new ParkingVisitResponse(
				visit.getId(),
				visit.getVehicle().getNormalizedPlate(),
				visit.getSpot().getId(),
				visit.getSpot().getCode(),
				visit.getEntryType(),
				visit.getEnteredAt(),
				visit.getExitedAt(),
				durationSeconds,
				visit.getStatus()
		);
	}

	public List<ParkingVisitResponse> toResponses(List<ParkingVisitEntity> visits) {
		return visits.stream().map(this::toResponse).toList();
	}

	public ParkingVisitCostResponse toCostResponse(ParkingVisitCost visitCost) {
		ParkingVisitEntity visit = visitCost.visit();
		ParkingCostBreakdown cost = visitCost.cost();
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
