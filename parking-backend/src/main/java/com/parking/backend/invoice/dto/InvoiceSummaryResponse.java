package com.parking.backend.invoice.dto;

import com.parking.backend.invoice.InvoiceEntity;
import com.parking.backend.invoice.InvoiceStatus;

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

	public static InvoiceSummaryResponse from(InvoiceEntity invoice) {
		return new InvoiceSummaryResponse(
				invoice.getId(),
				invoice.getInvoiceNumber(),
				invoice.getVisit().getId(),
				invoice.getVehiclePlate(),
				invoice.getSpotCode(),
				invoice.getIssuedAt(),
				invoice.getStatus(),
				invoice.getTotalAmount(),
				invoice.getCurrency()
		);
	}
}
