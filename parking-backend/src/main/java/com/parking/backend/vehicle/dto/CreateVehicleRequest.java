package com.parking.backend.vehicle.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateVehicleRequest(
		@NotBlank
		@Size(max = 64)
		String plateNumber
) {
}
