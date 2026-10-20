package com.parking.backend.visit;

import com.parking.backend.security.CurrentUserIdResolver;
import com.parking.backend.visit.dto.ParkingVisitResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/visits")
public class ParkingVisitController {

	private final ParkingVisitService parkingVisitService;
	private final CurrentUserIdResolver currentUserIdResolver;

	public ParkingVisitController(
			ParkingVisitService parkingVisitService,
			CurrentUserIdResolver currentUserIdResolver
	) {
		this.parkingVisitService = parkingVisitService;
		this.currentUserIdResolver = currentUserIdResolver;
	}

	@GetMapping("/my")
	public List<ParkingVisitResponse> getMyVisits(@AuthenticationPrincipal Jwt jwt) {
		Long userId = currentUserIdResolver.resolve(jwt);
		return parkingVisitService.getMyVisits(userId).stream()
				.map(ParkingVisitResponse::from)
				.toList();
	}
}
