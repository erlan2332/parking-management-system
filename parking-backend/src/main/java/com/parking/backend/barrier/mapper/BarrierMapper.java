package com.parking.backend.barrier.mapper;

import com.parking.backend.barrier.dto.BarrierEntryResponse;
import com.parking.backend.barrier.dto.BarrierExitResponse;
import com.parking.backend.barrier.model.BarrierExitResult;
import com.parking.backend.invoice.entity.InvoiceEntity;
import com.parking.backend.visit.entity.ParkingVisitEntity;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class BarrierMapper {

	public BarrierEntryResponse toEntryResponse(ParkingVisitEntity visit) {
		return new BarrierEntryResponse(
				visit.getId(),
				visit.getVehicle().getNormalizedPlate(),
				visit.getSpot().getId(),
				visit.getSpot().getCode(),
				visit.getEntryType(),
				visit.getEnteredAt(),
				visit.getStatus()
		);
	}

	public BarrierExitResponse toExitResponse(BarrierExitResult result) {
		ParkingVisitEntity visit = result.visit();
		InvoiceEntity invoice = result.invoice();
		return new BarrierExitResponse(
				visit.getId(),
				visit.getVehicle().getNormalizedPlate(),
				visit.getSpot().getCode(),
				visit.getEntryType(),
				visit.getEnteredAt(),
				visit.getExitedAt(),
				Duration.between(visit.getEnteredAt(), visit.getExitedAt()).getSeconds(),
				visit.getStatus(),
				invoice.getId(),
				invoice.getInvoiceNumber(),
				invoice.getTotalAmount(),
				invoice.getCurrency()
		);
	}
}
