package com.parking.backend.realtime;

import java.time.OffsetDateTime;
import java.util.Objects;

public record ParkingAvailabilityChangedEvent(
		String type,
		ParkingChangeReason reason,
		Long spotId,
		String spotCode,
		OffsetDateTime occurredAt
) {

	public static final String TYPE = "PARKING_AVAILABILITY_CHANGED";

	public ParkingAvailabilityChangedEvent {
		if (!TYPE.equals(type)) {
			throw new IllegalArgumentException("Unsupported parking event type");
		}
		Objects.requireNonNull(reason, "Parking change reason must not be null");
		Objects.requireNonNull(spotId, "Parking spot ID must not be null");
		if (spotCode == null || spotCode.isBlank()) {
			throw new IllegalArgumentException("Parking spot code must not be blank");
		}
		Objects.requireNonNull(occurredAt, "Parking event time must not be null");
	}
}
