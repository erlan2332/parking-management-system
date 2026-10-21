package com.parking.backend.visit.service;

import com.parking.backend.visit.entity.ParkingVisitEntity;
import com.parking.backend.visit.model.ParkingVisitCost;
import com.parking.backend.visit.repository.ParkingVisitRepository;
import lombok.RequiredArgsConstructor;

import com.parking.backend.common.exception.ParkingVisitNotCompletedException;
import com.parking.backend.common.exception.ParkingVisitNotFoundException;
import com.parking.backend.invoice.entity.InvoiceEntity;
import com.parking.backend.invoice.service.InvoiceService;
import com.parking.backend.pricing.service.ParkingPricingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ParkingVisitService {

	private final ParkingVisitRepository parkingVisitRepository;
	private final InvoiceService invoiceService;
	private final ParkingPricingService parkingPricingService;

	@Transactional(readOnly = true)
	public List<ParkingVisitEntity> getMyVisits(Long userId) {
		return parkingVisitRepository.findAllByVehicle_User_IdOrderByEnteredAtDescIdDesc(userId);
	}

	@Transactional(readOnly = true)
	public ParkingVisitEntity getCompletedVisit(Long visitId, Long userId) {
		ParkingVisitEntity visit = parkingVisitRepository.findByIdAndVehicle_User_Id(visitId, userId)
				.orElseThrow(ParkingVisitNotFoundException::new);
		if (visit.getExitedAt() == null) {
			throw new ParkingVisitNotCompletedException();
		}
		return visit;
	}

	@Transactional(readOnly = true)
	public ParkingVisitCost getVisitCost(Long visitId, Long userId) {
		ParkingVisitEntity visit = getCompletedVisit(visitId, userId);
		return new ParkingVisitCost(
				visit,
				invoiceService.findByVisitId(visitId)
						.map(InvoiceEntity::toCostBreakdown)
						.orElseGet(() -> parkingPricingService.calculate(visit.getEnteredAt(), visit.getExitedAt()))
		);
	}
}
