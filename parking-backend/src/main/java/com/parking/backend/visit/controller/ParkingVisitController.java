package com.parking.backend.visit.controller;

import com.parking.backend.visit.service.ParkingVisitService;
import lombok.RequiredArgsConstructor;

import com.parking.backend.invoice.mapper.InvoiceMapper;
import com.parking.backend.invoice.service.InvoiceService;
import com.parking.backend.invoice.dto.InvoiceResponse;
import com.parking.backend.security.web.CurrentUserIdResolver;
import com.parking.backend.visit.dto.ParkingVisitCostResponse;
import com.parking.backend.visit.dto.ParkingVisitResponse;
import com.parking.backend.visit.mapper.ParkingVisitMapper;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/visits")
@RequiredArgsConstructor
public class ParkingVisitController {

	private final ParkingVisitService parkingVisitService;
	private final InvoiceService invoiceService;
	private final ParkingVisitMapper parkingVisitMapper;
	private final InvoiceMapper invoiceMapper;
	private final CurrentUserIdResolver currentUserIdResolver;

	@GetMapping("/my")
	public List<ParkingVisitResponse> getMyVisits(@AuthenticationPrincipal Jwt jwt) {
		Long userId = currentUserIdResolver.resolve(jwt);
		return parkingVisitMapper.toResponses(parkingVisitService.getMyVisits(userId));
	}

	@GetMapping("/{id}/cost")
	public ParkingVisitCostResponse getVisitCost(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable Long id
	) {
		Long userId = currentUserIdResolver.resolve(jwt);
		return parkingVisitMapper.toCostResponse(parkingVisitService.getVisitCost(id, userId));
	}

	@GetMapping("/{id}/invoice")
	public InvoiceResponse getVisitInvoice(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
		Long userId = currentUserIdResolver.resolve(jwt);
		parkingVisitService.getCompletedVisit(id, userId);
		return invoiceMapper.toResponse(invoiceService.getOwnedInvoiceForVisit(id, userId));
	}
}
