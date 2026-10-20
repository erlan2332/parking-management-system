package com.parking.backend.invoice;

import com.parking.backend.invoice.dto.InvoiceResponse;
import com.parking.backend.invoice.dto.InvoiceSummaryResponse;
import com.parking.backend.security.CurrentUserIdResolver;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {

	private final InvoiceService invoiceService;
	private final CurrentUserIdResolver currentUserIdResolver;

	public InvoiceController(InvoiceService invoiceService, CurrentUserIdResolver currentUserIdResolver) {
		this.invoiceService = invoiceService;
		this.currentUserIdResolver = currentUserIdResolver;
	}

	@GetMapping("/my")
	public List<InvoiceSummaryResponse> getMyInvoices(@AuthenticationPrincipal Jwt jwt) {
		Long userId = currentUserIdResolver.resolve(jwt);
		return invoiceService.getMyInvoices(userId).stream()
				.map(InvoiceSummaryResponse::from)
				.toList();
	}

	@GetMapping("/{id}")
	public InvoiceResponse getInvoice(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
		Long userId = currentUserIdResolver.resolve(jwt);
		return InvoiceResponse.from(invoiceService.getOwnedInvoice(id, userId));
	}
}
