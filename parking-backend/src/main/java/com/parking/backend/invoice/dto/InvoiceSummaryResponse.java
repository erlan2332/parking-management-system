package com.parking.backend.invoice.dto;

import com.parking.backend.invoice.model.InvoiceStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record InvoiceSummaryResponse(
		Long id,
		String invoiceNumber,
		Long visitId,
		String vehiclePlate,
		String spotCode,
		Instant issuedAt,
		InvoiceStatus status,
		BigDecimal totalAmount,
		String currency
) {
}
