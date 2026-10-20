package com.parking.backend.reservation;

import com.parking.backend.reservation.dto.CreateHoldRequest;
import com.parking.backend.reservation.dto.HoldResponse;
import com.parking.backend.reservation.dto.ReservationResponse;
import com.parking.backend.security.CurrentUserIdResolver;
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
@RequestMapping("/api/reservations")
public class ReservationController {

	private final ReservationService reservationService;
	private final CurrentUserIdResolver currentUserIdResolver;

	public ReservationController(
			ReservationService reservationService,
			CurrentUserIdResolver currentUserIdResolver
	) {
		this.reservationService = reservationService;
		this.currentUserIdResolver = currentUserIdResolver;
	}

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
		return ResponseEntity.status(HttpStatus.CREATED).body(HoldResponse.from(hold));
	}

	@PostMapping("/holds/{id}/confirm")
	public ReservationResponse confirmHold(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
		Long userId = currentUserIdResolver.resolve(jwt);
		return ReservationResponse.from(reservationService.confirmHold(userId, id));
	}

	@PostMapping("/holds/{id}/release")
	public HoldResponse releaseHold(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
		Long userId = currentUserIdResolver.resolve(jwt);
		return HoldResponse.from(reservationService.releaseHold(userId, id));
	}

	@GetMapping("/holds/my")
	public List<HoldResponse> getMyHolds(@AuthenticationPrincipal Jwt jwt) {
		Long userId = currentUserIdResolver.resolve(jwt);
		return reservationService.getMyActiveHolds(userId).stream()
				.map(HoldResponse::from)
				.toList();
	}

	@GetMapping("/my")
	public List<ReservationResponse> getMyReservations(@AuthenticationPrincipal Jwt jwt) {
		Long userId = currentUserIdResolver.resolve(jwt);
		return reservationService.getMyReservations(userId).stream()
				.map(ReservationResponse::from)
				.toList();
	}

	@PostMapping("/{id}/cancel")
	public ReservationResponse cancelReservation(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
		Long userId = currentUserIdResolver.resolve(jwt);
		return ReservationResponse.from(reservationService.cancelReservation(userId, id));
	}
}
