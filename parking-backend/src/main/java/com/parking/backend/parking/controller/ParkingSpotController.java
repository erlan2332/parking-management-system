package com.parking.backend.parking.controller;

import com.parking.backend.parking.service.ParkingSpotService;
import lombok.RequiredArgsConstructor;

import com.parking.backend.parking.dto.ParkingAvailabilityResponse;
import com.parking.backend.parking.dto.ParkingSpotResponse;
import com.parking.backend.parking.mapper.ParkingSpotMapper;
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
@RequiredArgsConstructor
public class ParkingSpotController {

	private final ParkingSpotService parkingSpotService;
	private final ParkingSpotMapper parkingSpotMapper;

	@GetMapping("/spots")
	public List<ParkingSpotResponse> getSpots() {
		return parkingSpotMapper.toResponses(parkingSpotService.getCurrentSpotStates());
	}

	@GetMapping("/spots/{id}")
	public ParkingSpotResponse getSpot(@PathVariable Long id) {
		return parkingSpotMapper.toResponse(parkingSpotService.getCurrentSpotState(id));
	}

	@GetMapping("/availability")
	public ParkingAvailabilityResponse getAvailability(
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startAt,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endAt
	) {
		return parkingSpotMapper.toAvailabilityResponse(
				startAt,
				endAt,
				parkingSpotService.getAvailability(startAt, endAt)
		);
	}
}
