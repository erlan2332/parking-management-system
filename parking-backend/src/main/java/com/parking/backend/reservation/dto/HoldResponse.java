package com.parking.backend.reservation.dto;

import com.parking.backend.reservation.model.ReservationStatus;

import java.time.Instant;

public record HoldResponse(
		Long id,
		Long spotId,
		String spotCode,
		Long vehicleId,
		Instant startAt,
		Instant endAt,
		ReservationStatus status,
		Instant holdExpiresAt
) {
}
