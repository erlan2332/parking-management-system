package com.parking.backend.notification.model;

import java.time.Instant;

public record ClaimedReservationReminder(
		Long notificationId,
		String recipient,
		String vehiclePlate,
		String spotCode,
		Instant plannedEndAt
) {
}
