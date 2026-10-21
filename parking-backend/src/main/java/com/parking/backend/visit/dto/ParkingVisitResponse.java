package com.parking.backend.visit.dto;

import com.parking.backend.visit.model.ParkingEntryType;
import com.parking.backend.visit.entity.ParkingVisitEntity;
import com.parking.backend.visit.model.ParkingVisitStatus;

import java.time.Duration;
import java.time.Instant;

public record ParkingVisitResponse(
		Long id,
		String plateNumber,
		Long spotId,
		String spotCode,
		ParkingEntryType entryType,
		Instant enteredAt,
		Instant exitedAt,
		Long durationSeconds,
		ParkingVisitStatus status
) {

	public static ParkingVisitResponse from(ParkingVisitEntity visit) {
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
}
