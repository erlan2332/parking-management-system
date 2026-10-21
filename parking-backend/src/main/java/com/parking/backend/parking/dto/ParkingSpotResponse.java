package com.parking.backend.parking.dto;

import com.parking.backend.parking.model.ParkingSpotStatus;

public record ParkingSpotResponse(
		Long id,
		String code,
		boolean active,
		ParkingSpotStatus status
) {
}
