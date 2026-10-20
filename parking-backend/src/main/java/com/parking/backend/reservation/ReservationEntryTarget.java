package com.parking.backend.reservation;

import java.time.Instant;

public record ReservationEntryTarget(
		Long reservationId,
		Long spotId,
		Instant startAt,
		Instant endAt
) {
}
