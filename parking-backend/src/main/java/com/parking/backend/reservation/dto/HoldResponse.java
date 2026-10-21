package com.parking.backend.reservation.dto;

import com.parking.backend.reservation.entity.ReservationEntity;
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

	public static HoldResponse from(ReservationEntity hold) {
		return new HoldResponse(
				hold.getId(),
				hold.getSpot().getId(),
				hold.getSpot().getCode(),
				hold.getVehicle().getId(),
				hold.getStartAt(),
				hold.getEndAt(),
				hold.getStatus(),
				hold.getHoldExpiresAt()
		);
	}
}
