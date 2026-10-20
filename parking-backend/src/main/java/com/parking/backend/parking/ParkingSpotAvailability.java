package com.parking.backend.parking;

public record ParkingSpotAvailability(
		Long id,
		String code,
		boolean available
) {
}
