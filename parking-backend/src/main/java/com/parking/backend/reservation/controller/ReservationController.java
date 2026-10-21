package com.parking.backend.reservation.controller;

import com.parking.backend.reservation.entity.ReservationEntity;
import com.parking.backend.reservation.mapper.ReservationMapper;
import com.parking.backend.reservation.service.ReservationService;

import com.parking.backend.reservation.dto.CreateHoldRequest;
import com.parking.backend.reservation.dto.HoldResponse;
import com.parking.backend.reservation.dto.ReservationResponse;
import com.parking.backend.security.web.CurrentUserIdResolver;
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
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
public class ReservationController {

	private final ReservationService reservationService;
	private final ReservationMapper reservationMapper;
	private final CurrentUserIdResolver currentUserIdResolver;

	@PostMapping("/holds")
	public ResponseEntity<HoldResponse> createHold(
			@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody CreateHoldRequest request
	) {
		Long userId = currentUserIdResolver.resolve(jwt);
		ReservationEntity hold = reservationService.createHold(
				userId,
				request.vehicleId(),
				request.spotId(),
				request.startAt(),
				request.endAt()
		);
		return ResponseEntity.status(HttpStatus.CREATED).body(reservationMapper.toHoldResponse(hold));
	}

	@PostMapping("/holds/{id}/confirm")
	public ReservationResponse confirmHold(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
		Long userId = currentUserIdResolver.resolve(jwt);
		return reservationMapper.toResponse(reservationService.confirmHold(userId, id));
	}

	@PostMapping("/holds/{id}/release")
	public HoldResponse releaseHold(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
		Long userId = currentUserIdResolver.resolve(jwt);
		return reservationMapper.toHoldResponse(reservationService.releaseHold(userId, id));
	}

	@GetMapping("/holds/my")
	public List<HoldResponse> getMyHolds(@AuthenticationPrincipal Jwt jwt) {
		Long userId = currentUserIdResolver.resolve(jwt);
		return reservationMapper.toHoldResponses(reservationService.getMyActiveHolds(userId));
	}

	@GetMapping("/my")
	public List<ReservationResponse> getMyReservations(@AuthenticationPrincipal Jwt jwt) {
		Long userId = currentUserIdResolver.resolve(jwt);
		return reservationMapper.toResponses(reservationService.getMyReservations(userId));
	}

	@PostMapping("/{id}/cancel")
	public ReservationResponse cancelReservation(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
		Long userId = currentUserIdResolver.resolve(jwt);
		return reservationMapper.toResponse(reservationService.cancelReservation(userId, id));
	}
}
