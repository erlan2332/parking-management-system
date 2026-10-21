package com.parking.backend.barrier.controller;

import com.parking.backend.barrier.service.BarrierService;
import com.parking.backend.barrier.dto.BarrierEntryResponse;
import com.parking.backend.barrier.dto.BarrierExitResponse;
import com.parking.backend.barrier.dto.BarrierVehicleRequest;
import com.parking.backend.barrier.mapper.BarrierMapper;
import com.parking.backend.security.web.CurrentUserIdResolver;
import com.parking.backend.visit.entity.ParkingVisitEntity;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/barrier")
@RequiredArgsConstructor
public class BarrierController {

	private final BarrierService barrierService;
	private final BarrierMapper barrierMapper;
	private final CurrentUserIdResolver currentUserIdResolver;

	@PostMapping("/entry")
	public ResponseEntity<BarrierEntryResponse> enter(
			@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody BarrierVehicleRequest request
	) {
		Long userId = currentUserIdResolver.resolve(jwt);
		ParkingVisitEntity visit = barrierService.enter(userId, request.plateNumber());
		return ResponseEntity.status(HttpStatus.CREATED).body(barrierMapper.toEntryResponse(visit));
	}

	@PostMapping("/exit")
	public BarrierExitResponse exit(
			@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody BarrierVehicleRequest request
	) {
		Long userId = currentUserIdResolver.resolve(jwt);
		return barrierMapper.toExitResponse(barrierService.exit(userId, request.plateNumber()));
	}
}
