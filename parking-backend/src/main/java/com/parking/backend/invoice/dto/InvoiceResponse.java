package com.parking.backend.invoice.dto;

import com.parking.backend.invoice.entity.InvoiceEntity;
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

	public static InvoiceResponse from(InvoiceEntity invoice) {
		return new InvoiceResponse(
				invoice.getId(),
				invoice.getInvoiceNumber(),
				invoice.getVisit().getId(),
				invoice.getStatus(),
				invoice.getIssuedAt(),
				invoice.getVehiclePlate(),
				invoice.getSpotCode(),
				invoice.getEnteredAt(),
				invoice.getExitedAt(),
				invoice.getPricingZoneId(),
				invoice.getTotalDurationSeconds(),
				invoice.getDayDurationSeconds(),
				invoice.getNightDurationSeconds(),
				invoice.getDayRatePerMinute(),
				invoice.getNightRatePerMinute(),
				invoice.getDayCost(),
				invoice.getNightCost(),
				invoice.getTotalAmount(),
				invoice.getCurrency()
		);
	}
}
