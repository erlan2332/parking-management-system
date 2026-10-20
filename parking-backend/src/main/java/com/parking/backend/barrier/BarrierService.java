package com.parking.backend.barrier;

import com.parking.backend.common.exception.NoActiveVisitException;
import com.parking.backend.common.exception.NoAvailableParkingSpotException;
import com.parking.backend.common.exception.ReservationEntryConflictException;
import com.parking.backend.common.exception.VehicleAlreadyParkedException;
import com.parking.backend.common.exception.VehicleNotFoundException;
import com.parking.backend.common.persistence.DatabaseConstraintViolationDetector;
import com.parking.backend.parking.ParkingSpotEntity;
import com.parking.backend.parking.ParkingSpotRepository;
import com.parking.backend.reservation.ReservationEntity;
import com.parking.backend.reservation.ReservationRepository;
import com.parking.backend.reservation.ReservationStatus;
import com.parking.backend.vehicle.PlateNormalizer;
import com.parking.backend.vehicle.VehicleEntity;
import com.parking.backend.vehicle.VehicleRepository;
import com.parking.backend.visit.ParkingVisitEntity;
import com.parking.backend.visit.ParkingVisitRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

@Service
public class BarrierService {

	private static final Duration RESERVATION_ENTRY_GRACE_PERIOD = Duration.ofMinutes(15);
	private static final String ACTIVE_VEHICLE_CONSTRAINT = "uk_parking_visits_active_vehicle";
	private static final String ACTIVE_SPOT_CONSTRAINT = "uk_parking_visits_active_spot";
	private static final String RESERVATION_VISIT_CONSTRAINT = "uk_parking_visits_reservation";
	private static final Set<ReservationStatus> VEHICLE_OBLIGATION_STATUSES = Set.of(
			ReservationStatus.ACTIVE,
			ReservationStatus.CHECKED_IN
	);

	private final PlateNormalizer plateNormalizer;
	private final VehicleRepository vehicleRepository;
	private final ParkingSpotRepository parkingSpotRepository;
	private final ReservationRepository reservationRepository;
	private final ParkingVisitRepository parkingVisitRepository;
	private final Clock clock;

	public BarrierService(
			PlateNormalizer plateNormalizer,
			VehicleRepository vehicleRepository,
			ParkingSpotRepository parkingSpotRepository,
			ReservationRepository reservationRepository,
			ParkingVisitRepository parkingVisitRepository,
			Clock clock
	) {
		this.plateNormalizer = plateNormalizer;
		this.vehicleRepository = vehicleRepository;
		this.parkingSpotRepository = parkingSpotRepository;
		this.reservationRepository = reservationRepository;
		this.parkingVisitRepository = parkingVisitRepository;
		this.clock = clock;
	}

	@Transactional
	public ParkingVisitEntity enter(Long userId, String plateNumber) {
		VehicleEntity vehicle = findOwnedVehicle(userId, plateNumber);
		if (parkingVisitRepository.existsByVehicle_IdAndExitedAtIsNull(vehicle.getId())) {
			throw new VehicleAlreadyParkedException();
		}

		Instant now = clock.instant();
		List<ReservationEntity> activeReservations = reservationRepository
				.findAllByVehicle_IdAndStatusOrderByStartAtAscIdAsc(vehicle.getId(), ReservationStatus.ACTIVE);
		ReservationEntity applicableReservation = activeReservations.stream()
				.filter(reservation -> isWithinEntryWindow(reservation, now))
				.findFirst()
				.orElse(null);
		if (applicableReservation != null) {
			return enterWithReservation(userId, vehicle, applicableReservation);
		}
		if (!activeReservations.isEmpty()) {
			throw new ReservationEntryConflictException("Reservation is outside its permitted entry window");
		}
		if (hasVehicleObligation(vehicle.getId(), now)) {
			throw new ReservationEntryConflictException(
					"Vehicle has a hold or reservation that prevents an open-ended walk-in visit"
			);
		}
		return enterWalkIn(userId, vehicle);
	}

	@Transactional
	public ParkingVisitEntity exit(Long userId, String plateNumber) {
		VehicleEntity vehicle = findOwnedVehicle(userId, plateNumber);
		ParkingVisitEntity activeVisit = parkingVisitRepository
				.findByVehicle_IdAndExitedAtIsNull(vehicle.getId())
				.orElseThrow(NoActiveVisitException::new);

		// All mutating barrier flows keep the same lock order: spot, vehicle, related record.
		parkingSpotRepository.findByIdForUpdate(activeVisit.getSpot().getId())
				.orElseThrow(NoActiveVisitException::new);
		vehicleRepository.findOwnedByIdForUpdate(vehicle.getId(), userId)
				.orElseThrow(VehicleNotFoundException::new);
		ParkingVisitEntity lockedVisit = parkingVisitRepository.findByIdForUpdate(activeVisit.getId())
				.filter(visit -> visit.getExitedAt() == null)
				.orElseThrow(NoActiveVisitException::new);

		lockedVisit.complete(clock.instant());
		ReservationEntity reservation = lockedVisit.getReservation();
		if (reservation != null) {
			ReservationEntity lockedReservation = reservationRepository
					.findOwnedByIdForUpdate(reservation.getId(), userId)
					.orElseThrow(() -> new ReservationEntryConflictException("Visit reservation was not found"));
			if (lockedReservation.getStatus() != ReservationStatus.CHECKED_IN) {
				throw new ReservationEntryConflictException("Visit reservation is not checked in");
			}
			lockedReservation.complete();
		}
		return lockedVisit;
	}

	private ParkingVisitEntity enterWithReservation(
			Long userId,
			VehicleEntity unlockedVehicle,
			ReservationEntity reservationCandidate
	) {
		ParkingSpotEntity spot = parkingSpotRepository.findByIdForUpdate(reservationCandidate.getSpot().getId())
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
				.findOwnedByIdForUpdate(reservationCandidate.getId(), userId)
				.orElseThrow(() -> new ReservationEntryConflictException("Reservation was not found"));
		Instant enteredAt = clock.instant();
		if (reservation.getStatus() != ReservationStatus.ACTIVE
				|| !reservation.getVehicle().getId().equals(vehicle.getId())
				|| !reservation.getSpot().getId().equals(spot.getId())
				|| !isWithinEntryWindow(reservation, enteredAt)) {
			throw new ReservationEntryConflictException("Reservation is not eligible for entry");
		}

		reservation.checkIn();
		return saveVisit(ParkingVisitEntity.forReservation(vehicle, spot, reservation, enteredAt));
	}

	private ParkingVisitEntity enterWalkIn(Long userId, VehicleEntity unlockedVehicle) {
		Instant selectionTime = clock.instant();
		ParkingSpotEntity spot = lockFirstAvailableWalkInSpot(selectionTime);
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
		for (Long candidateId : parkingSpotRepository.findWalkInCandidateIds(selectionTime)) {
			ParkingSpotEntity candidate = parkingSpotRepository.findByIdForUpdate(candidateId)
					.orElseThrow(NoAvailableParkingSpotException::new);
			Instant recheckTime = clock.instant();
			if (candidate.isActive()
					&& !parkingVisitRepository.existsBySpot_IdAndExitedAtIsNull(candidateId)
					&& !reservationRepository.existsBlockingObligationForSpot(
							candidateId,
							recheckTime,
							ReservationStatus.HELD,
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
				ReservationStatus.HELD,
				VEHICLE_OBLIGATION_STATUSES
		);
	}

	private ParkingVisitEntity saveVisit(ParkingVisitEntity visit) {
		try {
			return parkingVisitRepository.saveAndFlush(visit);
		} catch (DataIntegrityViolationException exception) {
			if (DatabaseConstraintViolationDetector.hasConstraint(exception, ACTIVE_VEHICLE_CONSTRAINT)) {
				throw new VehicleAlreadyParkedException(exception);
			}
			if (DatabaseConstraintViolationDetector.hasConstraint(exception, ACTIVE_SPOT_CONSTRAINT)) {
				throw new NoAvailableParkingSpotException(exception);
			}
			if (DatabaseConstraintViolationDetector.hasConstraint(exception, RESERVATION_VISIT_CONSTRAINT)) {
				throw new ReservationEntryConflictException("Reservation has already been used for entry", exception);
			}
			throw exception;
		}
	}

	private static boolean isWithinEntryWindow(ReservationEntity reservation, Instant instant) {
		Instant deadline = earlierOf(
				reservation.getEndAt(),
				reservation.getStartAt().plus(RESERVATION_ENTRY_GRACE_PERIOD)
		);
		return !instant.isBefore(reservation.getStartAt()) && instant.isBefore(deadline);
	}

	private static Instant earlierOf(Instant first, Instant second) {
		return first.isBefore(second) ? first : second;
	}
}
