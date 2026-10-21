package com.parking.backend.barrier.service;

import com.parking.backend.barrier.model.BarrierExitResult;
import com.parking.backend.common.exception.NoActiveVisitException;
import com.parking.backend.common.exception.ReservationEntryConflictException;
import com.parking.backend.common.exception.VehicleNotFoundException;
import com.parking.backend.invoice.entity.InvoiceEntity;
import com.parking.backend.invoice.service.InvoiceService;
import com.parking.backend.parking.repository.ParkingSpotRepository;
import com.parking.backend.realtime.event.ParkingChangeReason;
import com.parking.backend.realtime.service.ParkingChangePublisher;
import com.parking.backend.reservation.entity.ReservationEntity;
import com.parking.backend.reservation.model.ReservationStatus;
import com.parking.backend.reservation.repository.ReservationRepository;
import com.parking.backend.vehicle.entity.VehicleEntity;
import com.parking.backend.vehicle.repository.VehicleRepository;
import com.parking.backend.vehicle.service.PlateNormalizer;
import com.parking.backend.visit.entity.ParkingVisitEntity;
import com.parking.backend.visit.model.ParkingVisitLockTarget;
import com.parking.backend.visit.repository.ParkingVisitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class BarrierExitService {

	private final PlateNormalizer plateNormalizer;
	private final VehicleRepository vehicleRepository;
	private final ParkingSpotRepository parkingSpotRepository;
	private final ReservationRepository reservationRepository;
	private final ParkingVisitRepository parkingVisitRepository;
	private final InvoiceService invoiceService;
	private final ParkingChangePublisher changePublisher;
	private final Clock clock;

	public BarrierExitResult exit(Long userId, String plateNumber) {
		VehicleEntity vehicle = findOwnedVehicle(userId, plateNumber);
		ParkingVisitLockTarget lockTarget = parkingVisitRepository
				.findActiveLockTargetByVehicleId(vehicle.getId())
				.orElseThrow(NoActiveVisitException::new);

		// Keep the established lock order: spot, vehicle, visit, then reservation.
		parkingSpotRepository.findByIdForUpdate(lockTarget.spotId())
				.orElseThrow(NoActiveVisitException::new);
		vehicleRepository.findOwnedByIdForUpdate(vehicle.getId(), userId)
				.orElseThrow(VehicleNotFoundException::new);
		ParkingVisitEntity lockedVisit = parkingVisitRepository.findByIdForUpdate(lockTarget.visitId())
				.filter(visit -> visit.getExitedAt() == null)
				.orElseThrow(NoActiveVisitException::new);

		Instant exitedAt = clock.instant();
		lockedVisit.complete(exitedAt);
		completeReservation(userId, lockedVisit);

		InvoiceEntity invoice = invoiceService.issueForVisit(lockedVisit, exitedAt);
		changePublisher.publish(
				ParkingChangeReason.VEHICLE_EXITED,
				lockedVisit.getSpot().getId(),
				lockedVisit.getSpot().getCode()
		);
		return new BarrierExitResult(lockedVisit, invoice);
	}

	private void completeReservation(Long userId, ParkingVisitEntity visit) {
		ReservationEntity reservation = visit.getReservation();
		if (reservation == null) {
			return;
		}
		ReservationEntity lockedReservation = reservationRepository
				.findOwnedByIdForUpdate(reservation.getId(), userId)
				.orElseThrow(() -> new ReservationEntryConflictException("Visit reservation was not found"));
		if (lockedReservation.getStatus() != ReservationStatus.CHECKED_IN) {
			throw new ReservationEntryConflictException("Visit reservation is not checked in");
		}
		lockedReservation.complete();
	}

	private VehicleEntity findOwnedVehicle(Long userId, String plateNumber) {
		String normalizedPlate = plateNormalizer.normalize(plateNumber);
		return vehicleRepository.findByNormalizedPlateAndUser_Id(normalizedPlate, userId)
				.orElseThrow(VehicleNotFoundException::new);
	}
}
