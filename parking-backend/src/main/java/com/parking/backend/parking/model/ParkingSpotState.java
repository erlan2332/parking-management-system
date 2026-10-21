package com.parking.backend.parking.model;

public record ParkingSpotState(
		Long id,
		String code,
		boolean active,
		ParkingSpotStatus status
) {
}
