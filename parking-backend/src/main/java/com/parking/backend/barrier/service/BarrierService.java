package com.parking.backend.barrier.service;

import com.parking.backend.barrier.model.BarrierExitResult;
import com.parking.backend.common.exception.ReservationNoShowExpiredException;
import com.parking.backend.visit.entity.ParkingVisitEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BarrierService {

	private final BarrierEntryService entryService;
	private final BarrierExitService exitService;

	@Transactional(noRollbackFor = ReservationNoShowExpiredException.class)
	public ParkingVisitEntity enter(Long userId, String plateNumber) {
		return entryService.enter(userId, plateNumber);
	}

	@Transactional
	public BarrierExitResult exit(Long userId, String plateNumber) {
		return exitService.exit(userId, plateNumber);
	}
}
