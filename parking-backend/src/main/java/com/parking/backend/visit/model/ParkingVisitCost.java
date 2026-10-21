package com.parking.backend.visit.model;

import com.parking.backend.pricing.model.ParkingCostBreakdown;
import com.parking.backend.visit.entity.ParkingVisitEntity;

public record ParkingVisitCost(
		ParkingVisitEntity visit,
		ParkingCostBreakdown cost
) {
}
