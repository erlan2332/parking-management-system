package com.parking.backend.barrier.service;

import com.parking.backend.common.exception.NoAvailableParkingSpotException;
import com.parking.backend.common.exception.ReservationEntryConflictException;
import com.parking.backend.common.exception.ReservationNoShowExpiredException;
import com.parking.backend.common.exception.VehicleAlreadyParkedException;
import com.parking.backend.common.exception.VehicleNotFoundException;
import com.parking.backend.common.persistence.DatabaseConstraintViolationDetector;
import com.parking.backend.parking.entity.ParkingSpotEntity;
import com.parking.backend.parking.repository.ParkingSpotRepository;
import com.parking.backend.realtime.event.ParkingChangeReason;
import com.parking.backend.realtime.service.ParkingChangePublisher;
import com.parking.backend.reservation.entity.ReservationEntity;
import com.parking.backend.reservation.model.ReservationEntryTarget;
import com.parking.backend.reservation.model.ReservationStatus;
import com.parking.backend.reservation.policy.ReservationEntryPolicy;
import com.parking.backend.reservation.repository.ReservationRepository;
import com.parking.backend.vehicle.entity.VehicleEntity;
import com.parking.backend.vehicle.repository.VehicleRepository;
import com.parking.backend.vehicle.service.PlateNormalizer;
import com.parking.backend.visit.entity.ParkingVisitEntity;
import com.parking.backend.visit.repository.ParkingVisitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class BarrierEntryService {

	private static final String ACTIVE_VEHICLE_CONSTRAINT = "uk_parking_visits_active_vehicle";
	private static final String ACTIVE_SPOT_CONSTRAINT = "uk_parking_visits_active_spot";
	private static final String RESERVATION_VISIT_CONSTRAINT = "uk_parking_visits_reservation";
	private static final Set<ReservationStatus> VEHICLE_OBLIGATION_STATUSES = Set.of(
			ReservationStatus.CHECKED_IN
	);

	private final PlateNormalizer plateNormalizer;
	private final VehicleRepository vehicleRepository;
	private final ParkingSpotRepository parkingSpotRepository;
	private final ReservationRepository reservationRepository;
	private final ParkingVisitRepository parkingVisitRepository;
	private final ReservationEntryPolicy entryPolicy;
	private final ParkingChangePublisher changePublisher;
	private final Clock clock;

	public ParkingVisitEntity enter(Long userId, String plateNumber) {
		VehicleEntity vehicle = findOwnedVehicle(userId, plateNumber);
		if (parkingVisitRepository.existsByVehicle_IdAndExitedAtIsNull(vehicle.getId())) {
			throw new VehicleAlreadyParkedException();
		}

		Instant now = clock.instant();
		List<ReservationEntryTarget> activeReservations = reservationRepository
				.findActiveEntryTargetsByVehicleId(vehicle.getId(), ReservationStatus.ACTIVE);
		ReservationEntryTarget applicableReservation = activeReservations.stream()
				.filter(reservation -> entryPolicy.allowsEntry(reservation.startAt(), reservation.endAt(), now))
				.findFirst()
				.orElse(null);
		if (applicableReservation != null) {
			return enterWithReservation(userId, vehicle, applicableReservation);
		}

		ReservationEntryTarget expiredReservation = activeReservations.stream()
				.filter(reservation -> entryPolicy.isExpiredAt(reservation.startAt(), reservation.endAt(), now))
				.findFirst()
				.orElse(null);
		if (expiredReservation != null) {
			expireLateReservation(userId, vehicle, expiredReservation);
		}
		if (!activeReservations.isEmpty()) {
			throw new ReservationEntryConflictException("Reservation is outside its permitted entry window");
		}
		if (reservationRepository.existsCurrentNoShowForVehicle(
				vehicle.getId(),
				ReservationStatus.EXPIRED,
				now
		)) {
			throw new ReservationNoShowExpiredException();
		}
		if (hasVehicleObligation(vehicle.getId(), now)) {
			throw new ReservationEntryConflictException(
					"Vehicle has a hold or reservation that prevents an open-ended walk-in visit"
			);
		}
		return enterWalkIn(userId, vehicle);
	}

	private ParkingVisitEntity enterWithReservation(
			Long userId,
			VehicleEntity unlockedVehicle,
			ReservationEntryTarget reservationCandidate
	) {
		ParkingSpotEntity spot = parkingSpotRepository.findByIdForUpdate(reservationCandidate.spotId())
				.orElseThrow(() -> new ReservationEntryConflictException("Reserved parking spot was not found"));
		if (!spot.isActive()) {
			throw new ReservationEntryConflictException("Reserved parking spot is unavailable");
		}

		VehicleEntity vehicle = vehicleRepository.findOwnedByIdForUpdate(unlockedVehicle.getId(), userId)
				.orElseThrow(VehicleNotFoundException::new);
		assertVehicleIsNotParked(vehicle.getId());
		if (parkingVisitRepository.existsBySpot_IdAndExitedAtIsNull(spot.getId())) {
			throw new ReservationEntryConflictException("Reserved parking spot is physically occupied");
		}

		ReservationEntity reservation = reservationRepository
				.findOwnedByIdForUpdate(reservationCandidate.reservationId(), userId)
				.orElseThrow(() -> new ReservationEntryConflictException("Reservation was not found"));
		Instant enteredAt = clock.instant();
		if (reservation.getStatus() == ReservationStatus.ACTIVE
				&& entryPolicy.isExpiredAt(reservation.getStartAt(), reservation.getEndAt(), enteredAt)) {
			reservation.expireNoShow();
			publish(ParkingChangeReason.RESERVATION_EXPIRED, reservation);
			throw new ReservationNoShowExpiredException();
		}
		if (reservation.getStatus() != ReservationStatus.ACTIVE
				|| !reservation.getVehicle().getId().equals(vehicle.getId())
				|| !reservation.getSpot().getId().equals(spot.getId())
				|| !entryPolicy.allowsEntry(reservation.getStartAt(), reservation.getEndAt(), enteredAt)) {
			throw new ReservationEntryConflictException("Reservation is not eligible for entry");
		}

		reservation.checkIn();
		return saveVisit(ParkingVisitEntity.forReservation(vehicle, spot, reservation, enteredAt));
	}

	private ParkingVisitEntity enterWalkIn(Long userId, VehicleEntity unlockedVehicle) {
		ParkingSpotEntity spot = lockFirstAvailableWalkInSpot(clock.instant());
		VehicleEntity vehicle = vehicleRepository.findOwnedByIdForUpdate(unlockedVehicle.getId(), userId)
				.orElseThrow(VehicleNotFoundException::new);
		assertVehicleIsNotParked(vehicle.getId());
		Instant enteredAt = clock.instant();
		if (hasVehicleObligation(vehicle.getId(), enteredAt)) {
			throw new ReservationEntryConflictException(
					"Vehicle has a hold or reservation that prevents an open-ended walk-in visit"
			);
		}
		return saveVisit(ParkingVisitEntity.walkIn(vehicle, spot, enteredAt));
	}

	private ParkingSpotEntity lockFirstAvailableWalkInSpot(Instant selectionTime) {
		for (Long candidateId : parkingSpotRepository.findWalkInCandidateIds(
				selectionTime,
				entryPolicy.activeStartCutoff(selectionTime)
		)) {
			ParkingSpotEntity candidate = parkingSpotRepository.findByIdForUpdate(candidateId)
					.orElseThrow(NoAvailableParkingSpotException::new);
			Instant recheckTime = clock.instant();
			if (candidate.isActive()
					&& !parkingVisitRepository.existsBySpot_IdAndExitedAtIsNull(candidateId)
					&& !reservationRepository.existsBlockingObligationForSpot(
							candidateId,
							recheckTime,
							entryPolicy.activeStartCutoff(recheckTime),
							ReservationStatus.HELD,
							ReservationStatus.ACTIVE,
							VEHICLE_OBLIGATION_STATUSES
					)) {
				return candidate;
			}
		}
		throw new NoAvailableParkingSpotException();
	}

	private VehicleEntity findOwnedVehicle(Long userId, String plateNumber) {
		String normalizedPlate = plateNormalizer.normalize(plateNumber);
		return vehicleRepository.findByNormalizedPlateAndUser_Id(normalizedPlate, userId)
				.orElseThrow(VehicleNotFoundException::new);
	}

	private void assertVehicleIsNotParked(Long vehicleId) {
		if (parkingVisitRepository.existsByVehicle_IdAndExitedAtIsNull(vehicleId)) {
			throw new VehicleAlreadyParkedException();
		}
	}

	private boolean hasVehicleObligation(Long vehicleId, Instant now) {
		return reservationRepository.existsBlockingObligationForVehicle(
				vehicleId,
				now,
				entryPolicy.activeStartCutoff(now),
				ReservationStatus.HELD,
				ReservationStatus.ACTIVE,
				VEHICLE_OBLIGATION_STATUSES
		);
	}

	private void expireLateReservation(Long userId, VehicleEntity unlockedVehicle, ReservationEntryTarget target) {
		parkingSpotRepository.findByIdForUpdate(target.spotId())
				.orElseThrow(() -> new ReservationEntryConflictException("Reserved parking spot was not found"));
		vehicleRepository.findOwnedByIdForUpdate(unlockedVehicle.getId(), userId)
				.orElseThrow(VehicleNotFoundException::new);
		ReservationEntity reservation = reservationRepository.findOwnedByIdForUpdate(target.reservationId(), userId)
				.orElseThrow(() -> new ReservationEntryConflictException("Reservation was not found"));
		Instant now = clock.instant();
		if (reservation.getStatus() == ReservationStatus.ACTIVE
				&& entryPolicy.isExpiredAt(reservation.getStartAt(), reservation.getEndAt(), now)) {
			reservation.expireNoShow();
			publish(ParkingChangeReason.RESERVATION_EXPIRED, reservation);
		}
		throw new ReservationNoShowExpiredException();
	}

	private ParkingVisitEntity saveVisit(ParkingVisitEntity visit) {
		try {
			ParkingVisitEntity savedVisit = parkingVisitRepository.saveAndFlush(visit);
			changePublisher.publish(
					ParkingChangeReason.VEHICLE_ENTERED,
					savedVisit.getSpot().getId(),
					savedVisit.getSpot().getCode()
			);
			return savedVisit;
		} catch (DataIntegrityViolationException exception) {
			if (DatabaseConstraintViolationDetector.hasConstraint(exception, ACTIVE_VEHICLE_CONSTRAINT)) {
				throw new VehicleAlreadyParkedException(exception);
			}
			if (DatabaseConstraintViolationDetector.hasConstraint(exception, ACTIVE_SPOT_CONSTRAINT)) {
				throw new NoAvailableParkingSpotException(exception);
			}
			if (DatabaseConstraintViolationDetector.hasConstraint(exception, RESERVATION_VISIT_CONSTRAINT)) {
				throw new ReservationEntryConflictException(
						"Reservation has already been used for entry",
						exception
				);
			}
			throw exception;
		}
	}

	private void publish(ParkingChangeReason reason, ReservationEntity reservation) {
		changePublisher.publish(reason, reservation.getSpot().getId(), reservation.getSpot().getCode());
	}
}
