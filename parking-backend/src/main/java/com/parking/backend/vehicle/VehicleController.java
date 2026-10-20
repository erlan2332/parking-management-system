package com.parking.backend.vehicle;

import com.parking.backend.security.CurrentUserIdResolver;
import com.parking.backend.vehicle.dto.CreateVehicleRequest;
import com.parking.backend.vehicle.dto.VehicleResponse;
import jakarta.validation.Valid;
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
public class VehicleController {

	private final VehicleService vehicleService;
	private final CurrentUserIdResolver currentUserIdResolver;

	public VehicleController(VehicleService vehicleService, CurrentUserIdResolver currentUserIdResolver) {
		this.vehicleService = vehicleService;
		this.currentUserIdResolver = currentUserIdResolver;
	}

	@PostMapping
	public ResponseEntity<VehicleResponse> createVehicle(
			@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody CreateVehicleRequest request
	) {
		Long userId = currentUserIdResolver.resolve(jwt);
		VehicleEntity vehicle = vehicleService.addVehicle(userId, request.plateNumber());
		return ResponseEntity.status(HttpStatus.CREATED).body(VehicleResponse.from(vehicle));
	}

	@GetMapping
	public List<VehicleResponse> getVehicles(@AuthenticationPrincipal Jwt jwt) {
		Long userId = currentUserIdResolver.resolve(jwt);
		return vehicleService.getVehiclesByUserId(userId).stream()
				.map(VehicleResponse::from)
				.toList();
	}

	@GetMapping("/{id}")
	public VehicleResponse getVehicle(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
		Long userId = currentUserIdResolver.resolve(jwt);
		return VehicleResponse.from(vehicleService.getVehicleByIdForUser(id, userId));
	}
}
