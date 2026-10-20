package com.parking.backend.barrier.dto;

import com.parking.backend.visit.ParkingEntryType;
import com.parking.backend.visit.ParkingVisitEntity;
import com.parking.backend.visit.ParkingVisitStatus;

import java.time.Duration;
import java.time.Instant;

public record BarrierExitResponse(
		Long visitId,
		String plateNumber,
		String spotCode,
		ParkingEntryType entryType,
		Instant enteredAt,
		Instant exitedAt,
		long durationSeconds,
		ParkingVisitStatus status
) {

	public static BarrierExitResponse from(ParkingVisitEntity visit) {
		return new BarrierExitResponse(
				visit.getId(),
				visit.getVehicle().getNormalizedPlate(),
				visit.getSpot().getCode(),
				visit.getEntryType(),
				visit.getEnteredAt(),
				visit.getExitedAt(),
				Duration.between(visit.getEnteredAt(), visit.getExitedAt()).getSeconds(),
				visit.getStatus()
		);
	}
}
