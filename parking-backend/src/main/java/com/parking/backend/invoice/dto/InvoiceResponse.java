package com.parking.backend.invoice.dto;

import com.parking.backend.invoice.model.InvoiceStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record InvoiceResponse(
		Long id,
		String invoiceNumber,
		Long visitId,
		InvoiceStatus status,
		Instant issuedAt,
		String vehiclePlate,
		String spotCode,
		Instant enteredAt,
		Instant exitedAt,
		String pricingZoneId,
		long totalDurationSeconds,
		long dayDurationSeconds,
		long nightDurationSeconds,
		BigDecimal dayRatePerMinute,
		BigDecimal nightRatePerMinute,
		BigDecimal dayCost,
		BigDecimal nightCost,
		BigDecimal totalAmount,
		String currency
) {
}
