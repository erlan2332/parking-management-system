package com.parking.backend.barrier.model;

import com.parking.backend.invoice.entity.InvoiceEntity;
import com.parking.backend.visit.entity.ParkingVisitEntity;

public record BarrierExitResult(ParkingVisitEntity visit, InvoiceEntity invoice) {
}
