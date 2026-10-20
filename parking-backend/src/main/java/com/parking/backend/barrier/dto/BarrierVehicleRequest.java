package com.parking.backend.barrier.dto;

import jakarta.validation.constraints.NotBlank;

public record BarrierVehicleRequest(
		@NotBlank String plateNumber
) {
}
