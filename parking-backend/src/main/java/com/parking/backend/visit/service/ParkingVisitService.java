package com.parking.backend.visit.service;

import com.parking.backend.visit.entity.ParkingVisitEntity;
import com.parking.backend.visit.repository.ParkingVisitRepository;

import com.parking.backend.common.exception.ParkingVisitNotCompletedException;
import com.parking.backend.common.exception.ParkingVisitNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ParkingVisitService {

	private final ParkingVisitRepository parkingVisitRepository;

	public ParkingVisitService(ParkingVisitRepository parkingVisitRepository) {
		this.parkingVisitRepository = parkingVisitRepository;
	}

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
}
