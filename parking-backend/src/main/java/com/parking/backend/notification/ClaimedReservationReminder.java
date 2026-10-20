package com.parking.backend.notification;

import java.time.Instant;

public record ClaimedReservationReminder(
		Long notificationId,
		String recipient,
		String vehiclePlate,
		String spotCode,
		Instant plannedEndAt
) {
}
