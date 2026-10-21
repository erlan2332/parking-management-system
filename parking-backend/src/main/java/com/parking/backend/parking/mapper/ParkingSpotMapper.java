package com.parking.backend.parking.mapper;

import com.parking.backend.parking.dto.ParkingAvailabilityResponse;
import com.parking.backend.parking.dto.ParkingSpotResponse;
import com.parking.backend.parking.model.ParkingSpotAvailability;
import com.parking.backend.parking.model.ParkingSpotState;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

@Component
public class ParkingSpotMapper {

	public ParkingSpotResponse toResponse(ParkingSpotState spot) {
		return new ParkingSpotResponse(spot.id(), spot.code(), spot.active(), spot.status());
	}

	public List<ParkingSpotResponse> toResponses(List<ParkingSpotState> spots) {
		return spots.stream().map(this::toResponse).toList();
	}

	public ParkingAvailabilityResponse toAvailabilityResponse(
			OffsetDateTime startAt,
			OffsetDateTime endAt,
			List<ParkingSpotAvailability> spots
	) {
		return new ParkingAvailabilityResponse(
				startAt,
				endAt,
				spots.stream().map(this::toSpotAvailabilityResponse).toList()
		);
	}

	private ParkingAvailabilityResponse.SpotAvailabilityResponse toSpotAvailabilityResponse(
			ParkingSpotAvailability spot
	) {
		return new ParkingAvailabilityResponse.SpotAvailabilityResponse(
				spot.id(),
				spot.code(),
				spot.available(),
				spot.status()
		);
	}
}
