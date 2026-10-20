package com.parking.backend.parking;

public record ParkingSpotState(
		Long id,
		String code,
		boolean active,
		ParkingSpotStatus status
) {
}
