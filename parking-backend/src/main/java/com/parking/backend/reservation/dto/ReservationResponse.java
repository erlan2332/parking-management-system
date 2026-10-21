package com.parking.backend.reservation.dto;

import com.parking.backend.reservation.model.ReservationStatus;

import java.time.Instant;

public record ReservationResponse(
		Long id,
		Long vehicleId,
		Long spotId,
		String spotCode,
		Instant startAt,
		Instant endAt,
		ReservationStatus status,
		Instant confirmedAt,
		Instant cancelledAt
) {
}
