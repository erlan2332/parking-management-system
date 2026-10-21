package com.parking.backend.parking.dto;

import com.parking.backend.parking.model.ParkingSpotStatus;

import java.time.OffsetDateTime;
import java.util.List;

public record ParkingAvailabilityResponse(
		OffsetDateTime startAt,
		OffsetDateTime endAt,
		List<SpotAvailabilityResponse> spots
) {

	public ParkingAvailabilityResponse {
		spots = List.copyOf(spots);
	}

	public record SpotAvailabilityResponse(
			Long id,
			String code,
			boolean available,
			ParkingSpotStatus status
	) {
	}
}
