package com.parking.backend.parking.model;

public record ParkingSpotAvailability(
		Long id,
		String code,
		boolean available,
		ParkingSpotStatus status
) {
}
