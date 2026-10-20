package com.parking.backend.visit;

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
}
