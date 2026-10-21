package com.parking.backend.reservation.service;

import com.parking.backend.realtime.event.ParkingChangeReason;
import com.parking.backend.realtime.service.ParkingChangePublisher;
import com.parking.backend.reservation.entity.ReservationEntity;
import com.parking.backend.reservation.model.ReservationStatus;
import com.parking.backend.reservation.policy.ReservationEntryPolicy;
import com.parking.backend.reservation.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReservationExpirationService {

	private static final int HOLD_EXPIRY_BATCH_SIZE = 100;
	private static final int NO_SHOW_BATCH_SIZE = 100;

	private final ReservationRepository reservationRepository;
	private final ReservationEntryPolicy entryPolicy;
	private final ParkingChangePublisher changePublisher;
	private final Clock clock;

	@Transactional
	public int expireDueHolds() {
		return expireHolds(reservationRepository.findDueHoldIdsForUpdate(
				clock.instant(),
				HOLD_EXPIRY_BATCH_SIZE
		));
	}

	@Transactional
	public int expireDueNoShows() {
		Instant now = clock.instant();
		return expireNoShows(reservationRepository.findDueNoShowIdsForUpdate(
				now,
				entryPolicy.activeStartCutoff(now),
				NO_SHOW_BATCH_SIZE
		));
	}

	public int expireHolds(List<Long> reservationIds) {
		int expiredCount = 0;
		for (ReservationEntity reservation : reservationRepository.findAllById(reservationIds)) {
			if (reservation.getStatus() == ReservationStatus.HELD) {
				reservation.expireHold();
				publish(ParkingChangeReason.HOLD_EXPIRED, reservation);
				expiredCount++;
			}
		}
		return expiredCount;
	}

	public int expireNoShows(List<Long> reservationIds) {
		int expiredCount = 0;
		for (ReservationEntity reservation : reservationRepository.findAllById(reservationIds)) {
			if (reservation.getStatus() == ReservationStatus.ACTIVE) {
				reservation.expireNoShow();
				publish(ParkingChangeReason.RESERVATION_EXPIRED, reservation);
				expiredCount++;
			}
		}
		return expiredCount;
	}

	private void publish(ParkingChangeReason reason, ReservationEntity reservation) {
		changePublisher.publish(reason, reservation.getSpot().getId(), reservation.getSpot().getCode());
	}
}
