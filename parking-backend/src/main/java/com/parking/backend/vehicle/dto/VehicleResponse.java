package com.parking.backend.vehicle.dto;

public record VehicleResponse(
		Long id,
		String plateNumber,
		String normalizedPlate
) {
}
