package com.parking.backend.notification.service;

import com.parking.backend.notification.entity.ReservationNotificationEntity;
import com.parking.backend.notification.model.ClaimedReservationReminder;
import com.parking.backend.notification.repository.ReservationNotificationRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Service
public class ReservationNotificationStore {

	private static final Duration REMINDER_LEAD_TIME = Duration.ofMinutes(10);

	private final ReservationNotificationRepository notificationRepository;

	public ReservationNotificationStore(ReservationNotificationRepository notificationRepository) {
		this.notificationRepository = notificationRepository;
	}

	@Transactional
	public int createDueReminders(Instant now) {
		return notificationRepository.createDueReminders(now, now.plus(REMINDER_LEAD_TIME));
	}

	@Transactional
	public Optional<ClaimedReservationReminder> claimNext(
			Instant now,
			Instant retryBefore,
			Instant staleBefore
	) {
		Optional<Long> notificationId = notificationRepository.findNextClaimableId(
				now,
				now.plus(REMINDER_LEAD_TIME),
				retryBefore,
				staleBefore
		);
		if (notificationId.isEmpty()) {
			return Optional.empty();
		}

		ReservationNotificationEntity notification = notificationRepository
				.findWithReservationById(notificationId.get())
				.orElseThrow();
		notification.startSending(now);
		return Optional.of(new ClaimedReservationReminder(
				notification.getId(),
				notification.getReservation().getUser().getEmail(),
				notification.getReservation().getVehicle().getNormalizedPlate(),
				notification.getReservation().getSpot().getCode(),
				notification.getReservation().getEndAt()
		));
	}

	@Transactional(readOnly = true)
	public boolean isStillEligible(Long notificationId, Instant now) {
		return notificationRepository.isStillEligible(
				notificationId,
				now,
				now.plus(REMINDER_LEAD_TIME)
		);
	}

	@Transactional
	public void markSent(Long notificationId, Instant sentAt) {
		ReservationNotificationEntity notification = notificationRepository.findByIdForUpdate(notificationId)
				.orElseThrow();
		notification.markSent(sentAt);
	}

	@Transactional
	public void markFailed(Long notificationId, String safeError) {
		ReservationNotificationEntity notification = notificationRepository.findByIdForUpdate(notificationId)
				.orElseThrow();
		notification.markFailed(safeError);
	}
}
