package com.parking.backend.visit.dto;

import com.parking.backend.visit.model.ParkingEntryType;
import com.parking.backend.visit.model.ParkingVisitStatus;

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
}
