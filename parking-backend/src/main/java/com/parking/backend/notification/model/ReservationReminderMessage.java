package com.parking.backend.notification.model;

public record ReservationReminderMessage(
		String recipient,
		String subject,
		String body
) {
}
