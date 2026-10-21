package com.parking.backend.parking.service;

import com.parking.backend.parking.entity.ParkingSpotEntity;
import com.parking.backend.parking.model.ParkingSpotAvailability;
import com.parking.backend.parking.model.ParkingSpotState;
import com.parking.backend.parking.model.ParkingSpotStatus;
import com.parking.backend.parking.repository.ParkingSpotRepository;

import com.parking.backend.common.exception.InvalidParkingIntervalException;
import com.parking.backend.common.exception.ParkingSpotNotFoundException;
import com.parking.backend.reservation.entity.ReservationEntity;
import com.parking.backend.reservation.policy.ReservationEntryPolicy;
import com.parking.backend.reservation.repository.ReservationRepository;
import com.parking.backend.reservation.model.ReservationStatus;
import com.parking.backend.visit.entity.ParkingVisitEntity;
import com.parking.backend.visit.repository.ParkingVisitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ParkingSpotService {

	private static final Set<ReservationStatus> PERSISTENT_BLOCKING_STATUSES = Set.of(
			ReservationStatus.CHECKED_IN
	);

	private final ParkingSpotRepository parkingSpotRepository;
	private final ReservationRepository reservationRepository;
	private final ParkingVisitRepository parkingVisitRepository;
	private final ReservationEntryPolicy entryPolicy;
	private final Clock clock;

	@Transactional(readOnly = true)
	public List<ParkingSpotEntity> getAllSpots() {
		return List.copyOf(parkingSpotRepository.findAllInNaturalCodeOrder());
	}

	@Transactional(readOnly = true)
	public ParkingSpotEntity getSpotById(Long id) {
		return parkingSpotRepository.findById(id)
				.orElseThrow(() -> new ParkingSpotNotFoundException(id));
	}

	public ParkingSpotStatus determineStatus(ParkingSpotEntity parkingSpot) {
		return parkingSpot.isActive() ? ParkingSpotStatus.FREE : ParkingSpotStatus.UNAVAILABLE;
	}

	@Transactional(readOnly = true)
	public List<ParkingSpotState> getCurrentSpotStates() {
		Instant now = clock.instant();
		List<ParkingSpotEntity> spots = parkingSpotRepository.findAllInNaturalCodeOrder();
		Map<Long, List<ReservationEntity>> blockersBySpot = blockersBySpot(now, now.plusNanos(1), now);
		Set<Long> occupiedSpotIds = occupiedSpotIds();
		return spots.stream()
				.map(spot -> toState(
						spot,
						blockersBySpot.getOrDefault(spot.getId(), List.of()),
						isOccupied(spot, occupiedSpotIds)
				))
				.toList();
	}

	@Transactional(readOnly = true)
	public ParkingSpotState getCurrentSpotState(Long id) {
		ParkingSpotEntity spot = getSpotById(id);
		Instant now = clock.instant();
		Map<Long, List<ReservationEntity>> blockersBySpot = blockersBySpot(now, now.plusNanos(1), now);
		return toState(
				spot,
				blockersBySpot.getOrDefault(spot.getId(), List.of()),
				isOccupied(spot, occupiedSpotIds())
		);
	}

	@Transactional(readOnly = true)
	public List<ParkingSpotAvailability> getAvailability(OffsetDateTime startAt, OffsetDateTime endAt) {
		validateInterval(startAt, endAt);
		Instant start = startAt.toInstant();
		Instant end = endAt.toInstant();
		Instant now = clock.instant();
		Map<Long, List<ReservationEntity>> blockersBySpot = blockersBySpot(start, end, now);
		Set<Long> occupiedSpotIds = occupiedSpotIds();
		return parkingSpotRepository.findAllInNaturalCodeOrder().stream()
				.map(spot -> {
					ParkingSpotStatus status = statusFor(
							spot,
							blockersBySpot.getOrDefault(spot.getId(), List.of()),
							isOccupied(spot, occupiedSpotIds)
					);
					return new ParkingSpotAvailability(
							spot.getId(),
							spot.getCode(),
							status == ParkingSpotStatus.FREE,
							status
					);
				})
				.toList();
	}

	private Map<Long, List<ReservationEntity>> blockersBySpot(Instant startAt, Instant endAt, Instant now) {
		return reservationRepository.findBlockingForInterval(
				startAt,
				endAt,
				now,
				entryPolicy.activeStartCutoff(now),
				ReservationStatus.HELD,
				ReservationStatus.ACTIVE,
				PERSISTENT_BLOCKING_STATUSES
		).stream().collect(Collectors.groupingBy(reservation -> reservation.getSpot().getId()));
	}

	private static ParkingSpotState toState(
			ParkingSpotEntity spot,
			Collection<ReservationEntity> blockers,
			boolean occupied
	) {
		return new ParkingSpotState(spot.getId(), spot.getCode(), spot.isActive(), statusFor(spot, blockers, occupied));
	}

	private static ParkingSpotStatus statusFor(
			ParkingSpotEntity spot,
			Collection<ReservationEntity> blockers,
			boolean occupied
	) {
		if (!spot.isActive()) {
			return ParkingSpotStatus.UNAVAILABLE;
		}
		if (occupied) {
			return ParkingSpotStatus.OCCUPIED;
		}

		EnumSet<ReservationStatus> statuses = blockers.stream()
				.map(ReservationEntity::getStatus)
				.collect(Collectors.toCollection(() -> EnumSet.noneOf(ReservationStatus.class)));
		if (statuses.contains(ReservationStatus.CHECKED_IN)) {
			return ParkingSpotStatus.OCCUPIED;
		}
		if (statuses.contains(ReservationStatus.ACTIVE)) {
			return ParkingSpotStatus.RESERVED;
		}
		if (statuses.contains(ReservationStatus.HELD)) {
			return ParkingSpotStatus.HELD;
		}
		return ParkingSpotStatus.FREE;
	}

	private Set<Long> occupiedSpotIds() {
		return parkingVisitRepository.findAllByExitedAtIsNull().stream()
				.map(ParkingVisitEntity::getSpot)
				.map(ParkingSpotEntity::getId)
				.collect(Collectors.toUnmodifiableSet());
	}

	private static boolean isOccupied(ParkingSpotEntity spot, Set<Long> occupiedSpotIds) {
		return spot.getId() != null && occupiedSpotIds.contains(spot.getId());
	}

	private static void validateInterval(OffsetDateTime startAt, OffsetDateTime endAt) {
		if (startAt == null || endAt == null || !startAt.toInstant().isBefore(endAt.toInstant())) {
			throw new InvalidParkingIntervalException();
		}
	}
}
