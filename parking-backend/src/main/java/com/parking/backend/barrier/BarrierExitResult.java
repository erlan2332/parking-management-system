package com.parking.backend.barrier;

import com.parking.backend.invoice.InvoiceEntity;
import com.parking.backend.visit.ParkingVisitEntity;

public record BarrierExitResult(ParkingVisitEntity visit, InvoiceEntity invoice) {
}
