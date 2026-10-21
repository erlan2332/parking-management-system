package com.parking.backend.invoice.mapper;

import com.parking.backend.invoice.dto.InvoiceResponse;
import com.parking.backend.invoice.dto.InvoiceSummaryResponse;
import com.parking.backend.invoice.entity.InvoiceEntity;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class InvoiceMapper {

	public InvoiceResponse toResponse(InvoiceEntity invoice) {
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

	public InvoiceSummaryResponse toSummaryResponse(InvoiceEntity invoice) {
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

	public List<InvoiceSummaryResponse> toSummaryResponses(List<InvoiceEntity> invoices) {
		return invoices.stream().map(this::toSummaryResponse).toList();
	}
}
