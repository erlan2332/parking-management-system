package com.parking.backend.reservation.dto;

import com.parking.backend.reservation.entity.ReservationEntity;
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

	public static ReservationResponse from(ReservationEntity reservation) {
		return new ReservationResponse(
				reservation.getId(),
				reservation.getVehicle().getId(),
				reservation.getSpot().getId(),
				reservation.getSpot().getCode(),
				reservation.getStartAt(),
				reservation.getEndAt(),
				reservation.getStatus(),
				reservation.getConfirmedAt(),
				reservation.getCancelledAt()
		);
	}
}
