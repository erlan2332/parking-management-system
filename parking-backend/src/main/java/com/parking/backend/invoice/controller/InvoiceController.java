package com.parking.backend.invoice.controller;

import com.parking.backend.invoice.service.InvoiceService;

import com.parking.backend.invoice.dto.InvoiceResponse;
import com.parking.backend.invoice.dto.InvoiceSummaryResponse;
import com.parking.backend.invoice.mapper.InvoiceMapper;
import com.parking.backend.security.web.CurrentUserIdResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/invoices")
@RequiredArgsConstructor
public class InvoiceController {

	private final InvoiceService invoiceService;
	private final InvoiceMapper invoiceMapper;
	private final CurrentUserIdResolver currentUserIdResolver;

	@GetMapping("/my")
	public List<InvoiceSummaryResponse> getMyInvoices(@AuthenticationPrincipal Jwt jwt) {
		Long userId = currentUserIdResolver.resolve(jwt);
		return invoiceMapper.toSummaryResponses(invoiceService.getMyInvoices(userId));
	}

	@GetMapping("/{id}")
	public InvoiceResponse getInvoice(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
		Long userId = currentUserIdResolver.resolve(jwt);
		return invoiceMapper.toResponse(invoiceService.getOwnedInvoice(id, userId));
	}
}
