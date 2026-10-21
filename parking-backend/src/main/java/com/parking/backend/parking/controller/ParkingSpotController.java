package com.parking.backend.parking.controller;

import com.parking.backend.parking.service.ParkingSpotService;

import com.parking.backend.parking.dto.ParkingAvailabilityResponse;
import com.parking.backend.parking.dto.ParkingSpotResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/parking")
public class ParkingSpotController {

	private final ParkingSpotService parkingSpotService;

	public ParkingSpotController(ParkingSpotService parkingSpotService) {
		this.parkingSpotService = parkingSpotService;
	}

	@GetMapping("/spots")
	public List<ParkingSpotResponse> getSpots() {
		return parkingSpotService.getCurrentSpotStates().stream()
				.map(ParkingSpotResponse::from)
				.toList();
	}

	@GetMapping("/spots/{id}")
	public ParkingSpotResponse getSpot(@PathVariable Long id) {
		return ParkingSpotResponse.from(parkingSpotService.getCurrentSpotState(id));
	}

	@GetMapping("/availability")
	public ParkingAvailabilityResponse getAvailability(
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startAt,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endAt
	) {
		return ParkingAvailabilityResponse.from(
				startAt,
				endAt,
				parkingSpotService.getAvailability(startAt, endAt)
		);
	}
}
