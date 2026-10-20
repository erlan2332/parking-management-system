package com.parking.backend.reservation;

import com.parking.backend.common.exception.InvalidReservationRequestException;
import com.parking.backend.common.exception.ParkingSpotNotFoundException;
import com.parking.backend.common.exception.ReservationConflictException;
import com.parking.backend.common.exception.ReservationNotFoundException;
import com.parking.backend.common.exception.VehicleNotFoundException;
import com.parking.backend.common.persistence.DatabaseConstraintViolationDetector;
import com.parking.backend.parking.ParkingSpotEntity;
import com.parking.backend.parking.ParkingSpotRepository;
import com.parking.backend.vehicle.VehicleEntity;
import com.parking.backend.vehicle.VehicleRepository;
import com.parking.backend.visit.ParkingVisitRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

@Service
public class ReservationService {

	private static final Duration HOLD_DURATION = Duration.ofMinutes(3);
	private static final String SPOT_INTERVAL_CONSTRAINT = "ex_reservations_spot_interval";
	private static final String VEHICLE_INTERVAL_CONSTRAINT = "ex_reservations_vehicle_interval";
	private static final Set<ReservationStatus> PERSISTENT_BLOCKING_STATUSES = Set.of(
			ReservationStatus.ACTIVE,
			ReservationStatus.CHECKED_IN
	);
	private static final Set<ReservationStatus> CONFIRMED_HISTORY_STATUSES = Set.of(
			ReservationStatus.ACTIVE,
			ReservationStatus.CANCELLED,
			ReservationStatus.CHECKED_IN,
			ReservationStatus.COMPLETED
	);

	private final ReservationRepository reservationRepository;
	private final ParkingSpotRepository parkingSpotRepository;
	private final VehicleRepository vehicleRepository;
	private final ParkingVisitRepository parkingVisitRepository;
	private final Clock clock;

	public ReservationService(
			ReservationRepository reservationRepository,
			ParkingSpotRepository parkingSpotRepository,
			VehicleRepository vehicleRepository,
			ParkingVisitRepository parkingVisitRepository,
			Clock clock
	) {
		this.reservationRepository = reservationRepository;
		this.parkingSpotRepository = parkingSpotRepository;
		this.vehicleRepository = vehicleRepository;
		this.parkingVisitRepository = parkingVisitRepository;
		this.clock = clock;
	}

	@Transactional
	public ReservationEntity createHold(
			Long userId,
			Long vehicleId,
			Long spotId,
			OffsetDateTime startAt,
			OffsetDateTime endAt
	) {
		Instant now = clock.instant();
		Instant start = toInstant(startAt, "startAt is required");
		Instant end = toInstant(endAt, "endAt is required");
		validateNewHoldInterval(start, end, now);

		// Every hold creation acquires locks in the same order: spot, then vehicle.
		ParkingSpotEntity spot = parkingSpotRepository.findByIdForUpdate(spotId)
				.orElseThrow(() -> new ParkingSpotNotFoundException(spotId));
		if (!spot.isActive()) {
			throw new ReservationConflictException("Parking spot is unavailable");
		}
		if (parkingVisitRepository.existsBySpot_IdAndExitedAtIsNull(spotId)) {
			throw new ReservationConflictException("Parking spot is physically occupied");
		}

		VehicleEntity vehicle = vehicleRepository.findOwnedByIdForUpdate(vehicleId, userId)
				.orElseThrow(() -> new VehicleNotFoundException(vehicleId));

		reservationRepository.expireConflictingHolds(spotId, vehicleId, start, end, now);
		if (!reservationRepository.findBlockingConflicts(
				spotId,
				vehicleId,
				start,
				end,
				now,
				ReservationStatus.HELD,
				PERSISTENT_BLOCKING_STATUSES
		).isEmpty()) {
			throw new ReservationConflictException("Parking spot or vehicle is already reserved for this interval");
		}

		Instant holdExpiresAt = earlierOf(now.plus(HOLD_DURATION), start);
		ReservationEntity hold = ReservationEntity.createHold(
				vehicle.getUser(),
				vehicle,
				spot,
				start,
				end,
				holdExpiresAt,
				now
		);

		try {
			return reservationRepository.saveAndFlush(hold);
		} catch (DataIntegrityViolationException exception) {
			if (isExclusionConflict(exception)) {
				throw new ReservationConflictException(
						"Parking spot or vehicle is already reserved for this interval",
						exception
				);
			}
			throw exception;
		}
	}

	@Transactional(noRollbackFor = ReservationConflictException.class)
	public ReservationEntity confirmHold(Long userId, Long holdId) {
		ReservationEntity hold = findOwnedForUpdate(holdId, userId);
		if (hold.getStatus() == ReservationStatus.ACTIVE) {
			return hold;
		}
		if (hold.getStatus() != ReservationStatus.HELD) {
			throw new ReservationConflictException("Reservation is not an active hold");
		}

		Instant now = clock.instant();
		if (hold.isExpiredHoldAt(now) || !now.isBefore(hold.getStartAt())) {
			hold.expireHold();
			throw new ReservationConflictException("Hold has expired");
		}

		hold.confirm(now);
		return hold;
	}

	@Transactional(noRollbackFor = ReservationConflictException.class)
	public ReservationEntity releaseHold(Long userId, Long holdId) {
		ReservationEntity hold = findOwnedForUpdate(holdId, userId);
		if (hold.getStatus() == ReservationStatus.HOLD_RELEASED) {
			return hold;
		}
		if (hold.getStatus() != ReservationStatus.HELD) {
			throw new ReservationConflictException("Reservation is not an active hold");
		}

		Instant now = clock.instant();
		if (hold.isExpiredHoldAt(now)) {
			hold.expireHold();
			throw new ReservationConflictException("Hold has expired");
		}

		hold.release(now);
		return hold;
	}

	@Transactional
	public List<ReservationEntity> getMyActiveHolds(Long userId) {
		reservationRepository.expireHoldsForUser(userId, clock.instant());
		return reservationRepository.findAllByUser_IdAndStatusOrderByCreatedAtAscIdAsc(
				userId,
				ReservationStatus.HELD
		);
	}

	@Transactional(readOnly = true)
	public List<ReservationEntity> getMyReservations(Long userId) {
		return reservationRepository.findAllByUser_IdAndStatusInOrderByStartAtAscIdAsc(
				userId,
				CONFIRMED_HISTORY_STATUSES
		);
	}

	@Transactional
	public ReservationEntity cancelReservation(Long userId, Long reservationId) {
		ReservationEntity reservation = findOwnedForUpdate(reservationId, userId);
		if (reservation.getStatus() == ReservationStatus.CANCELLED) {
			return reservation;
		}
		if (reservation.getStatus() != ReservationStatus.ACTIVE) {
			throw new ReservationConflictException("Reservation is not active");
		}

		Instant now = clock.instant();
		if (!now.isBefore(reservation.getStartAt())) {
			throw new ReservationConflictException("Reservation cannot be cancelled after it starts");
		}

		reservation.cancel(now);
		return reservation;
	}

	@Transactional
	public int expireHolds() {
		return reservationRepository.expireAllHolds(clock.instant());
	}

	private ReservationEntity findOwnedForUpdate(Long reservationId, Long userId) {
		return reservationRepository.findOwnedByIdForUpdate(reservationId, userId)
				.orElseThrow(() -> new ReservationNotFoundException(reservationId));
	}

	private static void validateNewHoldInterval(Instant startAt, Instant endAt, Instant now) {
		if (!startAt.isBefore(endAt)) {
			throw new InvalidReservationRequestException("startAt must be earlier than endAt");
		}
		if (!startAt.isAfter(now)) {
			throw new InvalidReservationRequestException("startAt must be in the future");
		}
	}

	private static Instant toInstant(OffsetDateTime value, String message) {
		if (value == null) {
			throw new InvalidReservationRequestException(message);
		}
		return value.toInstant();
	}

	private static Instant earlierOf(Instant first, Instant second) {
		return first.isBefore(second) ? first : second;
	}

	private static boolean isExclusionConflict(DataIntegrityViolationException exception) {
		return DatabaseConstraintViolationDetector.hasConstraint(exception, SPOT_INTERVAL_CONSTRAINT)
				|| DatabaseConstraintViolationDetector.hasConstraint(exception, VEHICLE_INTERVAL_CONSTRAINT);
	}
}
