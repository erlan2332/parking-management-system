package com.parking.backend.barrier;

import com.parking.backend.barrier.dto.BarrierEntryResponse;
import com.parking.backend.barrier.dto.BarrierExitResponse;
import com.parking.backend.barrier.dto.BarrierVehicleRequest;
import com.parking.backend.security.CurrentUserIdResolver;
import com.parking.backend.visit.ParkingVisitEntity;
import jakarta.validation.Valid;
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
public class BarrierController {

	private final BarrierService barrierService;
	private final CurrentUserIdResolver currentUserIdResolver;

	public BarrierController(BarrierService barrierService, CurrentUserIdResolver currentUserIdResolver) {
		this.barrierService = barrierService;
		this.currentUserIdResolver = currentUserIdResolver;
	}

	@PostMapping("/entry")
	public ResponseEntity<BarrierEntryResponse> enter(
			@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody BarrierVehicleRequest request
	) {
		Long userId = currentUserIdResolver.resolve(jwt);
		ParkingVisitEntity visit = barrierService.enter(userId, request.plateNumber());
		return ResponseEntity.status(HttpStatus.CREATED).body(BarrierEntryResponse.from(visit));
	}

	@PostMapping("/exit")
	public BarrierExitResponse exit(
			@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody BarrierVehicleRequest request
	) {
		Long userId = currentUserIdResolver.resolve(jwt);
		return BarrierExitResponse.from(barrierService.exit(userId, request.plateNumber()));
	}
}
