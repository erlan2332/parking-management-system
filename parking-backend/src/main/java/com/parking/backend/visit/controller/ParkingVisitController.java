package com.parking.backend.visit.controller;

import com.parking.backend.visit.entity.ParkingVisitEntity;
import com.parking.backend.visit.service.ParkingVisitService;

import com.parking.backend.invoice.entity.InvoiceEntity;
import com.parking.backend.invoice.service.InvoiceService;
import com.parking.backend.invoice.dto.InvoiceResponse;
import com.parking.backend.pricing.model.ParkingCostBreakdown;
import com.parking.backend.pricing.service.ParkingPricingService;
import com.parking.backend.security.web.CurrentUserIdResolver;
import com.parking.backend.visit.dto.ParkingVisitCostResponse;
import com.parking.backend.visit.dto.ParkingVisitResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/visits")
public class ParkingVisitController {

	private final ParkingVisitService parkingVisitService;
	private final ParkingPricingService parkingPricingService;
	private final InvoiceService invoiceService;
	private final CurrentUserIdResolver currentUserIdResolver;

	public ParkingVisitController(
			ParkingVisitService parkingVisitService,
			ParkingPricingService parkingPricingService,
			InvoiceService invoiceService,
			CurrentUserIdResolver currentUserIdResolver
	) {
		this.parkingVisitService = parkingVisitService;
		this.parkingPricingService = parkingPricingService;
		this.invoiceService = invoiceService;
		this.currentUserIdResolver = currentUserIdResolver;
	}

	@GetMapping("/my")
	public List<ParkingVisitResponse> getMyVisits(@AuthenticationPrincipal Jwt jwt) {
		Long userId = currentUserIdResolver.resolve(jwt);
		return parkingVisitService.getMyVisits(userId).stream()
				.map(ParkingVisitResponse::from)
				.toList();
	}

	@GetMapping("/{id}/cost")
	public ParkingVisitCostResponse getVisitCost(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable Long id
	) {
		Long userId = currentUserIdResolver.resolve(jwt);
		ParkingVisitEntity visit = parkingVisitService.getCompletedVisit(id, userId);
		ParkingCostBreakdown cost = invoiceService.findByVisitId(visit.getId())
				.map(InvoiceEntity::toCostBreakdown)
				.orElseGet(() -> parkingPricingService.calculate(visit.getEnteredAt(), visit.getExitedAt()));
		return ParkingVisitCostResponse.from(visit, cost);
	}

	@GetMapping("/{id}/invoice")
	public InvoiceResponse getVisitInvoice(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
		Long userId = currentUserIdResolver.resolve(jwt);
		parkingVisitService.getCompletedVisit(id, userId);
		return InvoiceResponse.from(invoiceService.getOwnedInvoiceForVisit(id, userId));
	}
}
