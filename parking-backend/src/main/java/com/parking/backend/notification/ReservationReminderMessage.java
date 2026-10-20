package com.parking.backend.notification;

public record ReservationReminderMessage(
		String recipient,
		String subject,
		String body
) {
}
