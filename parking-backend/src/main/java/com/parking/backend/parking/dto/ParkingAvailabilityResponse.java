package com.parking.backend.parking.dto;

import com.parking.backend.parking.model.ParkingSpotAvailability;
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

	public static ParkingAvailabilityResponse from(
			OffsetDateTime startAt,
			OffsetDateTime endAt,
			List<ParkingSpotAvailability> spots
	) {
		return new ParkingAvailabilityResponse(
				startAt,
				endAt,
				spots.stream().map(SpotAvailabilityResponse::from).toList()
		);
	}

	public record SpotAvailabilityResponse(
			Long id,
			String code,
			boolean available,
			ParkingSpotStatus status
	) {

		private static SpotAvailabilityResponse from(ParkingSpotAvailability spot) {
			return new SpotAvailabilityResponse(spot.id(), spot.code(), spot.available(), spot.status());
		}
	}
}
