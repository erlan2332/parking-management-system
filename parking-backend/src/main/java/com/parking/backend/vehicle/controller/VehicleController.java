package com.parking.backend.vehicle.controller;

import com.parking.backend.vehicle.entity.VehicleEntity;
import com.parking.backend.vehicle.service.VehicleService;

import com.parking.backend.security.web.CurrentUserIdResolver;
import com.parking.backend.vehicle.dto.CreateVehicleRequest;
import com.parking.backend.vehicle.dto.VehicleResponse;
import com.parking.backend.vehicle.mapper.VehicleMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/vehicles")
@RequiredArgsConstructor
public class VehicleController {

	private final VehicleService vehicleService;
	private final VehicleMapper vehicleMapper;
	private final CurrentUserIdResolver currentUserIdResolver;

	@PostMapping
	public ResponseEntity<VehicleResponse> createVehicle(
			@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody CreateVehicleRequest request
	) {
		Long userId = currentUserIdResolver.resolve(jwt);
		VehicleEntity vehicle = vehicleService.addVehicle(userId, request.plateNumber());
		return ResponseEntity.status(HttpStatus.CREATED).body(vehicleMapper.toResponse(vehicle));
	}

	@GetMapping
	public List<VehicleResponse> getVehicles(@AuthenticationPrincipal Jwt jwt) {
		Long userId = currentUserIdResolver.resolve(jwt);
		return vehicleMapper.toResponses(vehicleService.getVehiclesByUserId(userId));
	}

	@GetMapping("/{id}")
	public VehicleResponse getVehicle(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
		Long userId = currentUserIdResolver.resolve(jwt);
		return vehicleMapper.toResponse(vehicleService.getVehicleByIdForUser(id, userId));
	}
}
