package com.parking.backend.notification.sender;

import com.parking.backend.notification.model.ReservationReminderMessage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
		prefix = "application.notifications.mail",
		name = "enabled",
		havingValue = "false",
		matchIfMissing = true
)
public class DisabledReservationEmailSender implements ReservationEmailSender {

	@Override
	public boolean isEnabled() {
		return false;
	}

	@Override
	public void send(ReservationReminderMessage message) {
		throw new IllegalStateException("Reservation email notifications are disabled");
	}
}
