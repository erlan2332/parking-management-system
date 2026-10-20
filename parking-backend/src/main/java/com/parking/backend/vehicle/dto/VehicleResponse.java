package com.parking.backend.vehicle.dto;

import com.parking.backend.vehicle.VehicleEntity;

public record VehicleResponse(
		Long id,
		String plateNumber,
		String normalizedPlate
) {

	public static VehicleResponse from(VehicleEntity vehicle) {
		return new VehicleResponse(
				vehicle.getId(),
				vehicle.getPlateNumber(),
				vehicle.getNormalizedPlate()
		);
	}
}
