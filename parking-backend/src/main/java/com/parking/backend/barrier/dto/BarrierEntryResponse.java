package com.parking.backend.barrier.dto;

import com.parking.backend.visit.model.ParkingEntryType;
import com.parking.backend.visit.entity.ParkingVisitEntity;
import com.parking.backend.visit.model.ParkingVisitStatus;

import java.time.Instant;

public record BarrierEntryResponse(
		Long visitId,
		String plateNumber,
		Long spotId,
		String spotCode,
		ParkingEntryType entryType,
		Instant enteredAt,
		ParkingVisitStatus status
) {

	public static BarrierEntryResponse from(ParkingVisitEntity visit) {
		return new BarrierEntryResponse(
				visit.getId(),
				visit.getVehicle().getNormalizedPlate(),
				visit.getSpot().getId(),
				visit.getSpot().getCode(),
				visit.getEntryType(),
				visit.getEnteredAt(),
				visit.getStatus()
		);
	}
}
