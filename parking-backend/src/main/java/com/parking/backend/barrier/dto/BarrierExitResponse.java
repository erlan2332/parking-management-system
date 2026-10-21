package com.parking.backend.barrier.dto;

import com.parking.backend.visit.model.ParkingEntryType;
import com.parking.backend.visit.model.ParkingVisitStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record BarrierExitResponse(
		Long visitId,
		String plateNumber,
		String spotCode,
		ParkingEntryType entryType,
		Instant enteredAt,
		Instant exitedAt,
		long durationSeconds,
		ParkingVisitStatus status,
		Long invoiceId,
		String invoiceNumber,
		BigDecimal totalAmount,
		String currency
) {
}
