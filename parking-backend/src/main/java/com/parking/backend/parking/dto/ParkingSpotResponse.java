package com.parking.backend.parking.dto;

import com.parking.backend.parking.ParkingSpotEntity;
import com.parking.backend.parking.ParkingSpotState;
import com.parking.backend.parking.ParkingSpotStatus;

public record ParkingSpotResponse(
		Long id,
		String code,
		boolean active,
		ParkingSpotStatus status
) {

	public static ParkingSpotResponse from(ParkingSpotEntity spot, ParkingSpotStatus status) {
		return new ParkingSpotResponse(spot.getId(), spot.getCode(), spot.isActive(), status);
	}

	public static ParkingSpotResponse from(ParkingSpotState spot) {
		return new ParkingSpotResponse(spot.id(), spot.code(), spot.active(), spot.status());
	}
}
