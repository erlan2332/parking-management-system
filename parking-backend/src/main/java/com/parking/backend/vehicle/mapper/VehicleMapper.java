package com.parking.backend.vehicle.mapper;

import com.parking.backend.vehicle.dto.VehicleResponse;
import com.parking.backend.vehicle.entity.VehicleEntity;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class VehicleMapper {

	public VehicleResponse toResponse(VehicleEntity vehicle) {
		return new VehicleResponse(
				vehicle.getId(),
				vehicle.getPlateNumber(),
				vehicle.getNormalizedPlate()
		);
	}

	public List<VehicleResponse> toResponses(List<VehicleEntity> vehicles) {
		return vehicles.stream().map(this::toResponse).toList();
	}
}
