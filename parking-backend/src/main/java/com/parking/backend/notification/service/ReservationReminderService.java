package com.parking.backend.notification.service;

import com.parking.backend.notification.model.ClaimedReservationReminder;
import com.parking.backend.notification.sender.ReservationEmailSender;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Service
public class ReservationReminderService {

	private static final int PROCESSING_BATCH_SIZE = 20;
	private static final Duration STALE_CLAIM_AGE = Duration.ofMinutes(2);
	private static final String NO_LONGER_ELIGIBLE = "Reservation is no longer eligible for a reminder";

	private final ReservationNotificationStore notificationStore;
	private final ReservationEmailSender emailSender;
	private final ReservationReminderMessageFactory messageFactory;
	private final Clock clock;

	public ReservationReminderService(
			ReservationNotificationStore notificationStore,
			ReservationEmailSender emailSender,
			ReservationReminderMessageFactory messageFactory,
			Clock clock
	) {
		this.notificationStore = notificationStore;
		this.emailSender = emailSender;
		this.messageFactory = messageFactory;
		this.clock = clock;
	}

	public int processDueReminders() {
		if (!emailSender.isEnabled()) {
			return 0;
		}

		Instant runStartedAt = clock.instant();
		notificationStore.createDueReminders(runStartedAt);
		int sentCount = 0;
		for (int processed = 0; processed < PROCESSING_BATCH_SIZE; processed++) {
			Instant now = clock.instant();
			Optional<ClaimedReservationReminder> claimed = notificationStore.claimNext(
					now,
					runStartedAt,
					now.minus(STALE_CLAIM_AGE)
			);
			if (claimed.isEmpty()) {
				break;
			}

			ClaimedReservationReminder reminder = claimed.get();
			Instant eligibilityCheckTime = clock.instant();
			if (!notificationStore.isStillEligible(reminder.notificationId(), eligibilityCheckTime)) {
				notificationStore.markFailed(reminder.notificationId(), NO_LONGER_ELIGIBLE);
				continue;
			}

			try {
				emailSender.send(messageFactory.create(reminder));
				notificationStore.markSent(reminder.notificationId(), clock.instant());
				sentCount++;
			} catch (RuntimeException exception) {
				notificationStore.markFailed(
						reminder.notificationId(),
						"Email delivery failed: " + exception.getClass().getSimpleName()
				);
			}
		}
		return sentCount;
	}
}
