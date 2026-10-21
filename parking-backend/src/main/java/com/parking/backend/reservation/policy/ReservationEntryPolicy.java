package com.parking.backend.reservation.policy;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
public class ReservationEntryPolicy {

	private static final Duration ENTRY_GRACE_PERIOD = Duration.ofMinutes(15);

	public boolean allowsEntry(Instant startAt, Instant endAt, Instant instant) {
		return !instant.isBefore(startAt) && instant.isBefore(deadline(startAt, endAt));
	}

	public boolean isExpiredAt(Instant startAt, Instant endAt, Instant instant) {
		return !instant.isBefore(deadline(startAt, endAt));
	}

	public Instant activeStartCutoff(Instant instant) {
		return instant.minus(ENTRY_GRACE_PERIOD);
	}

	public Instant deadline(Instant startAt, Instant endAt) {
		Instant graceDeadline = startAt.plus(ENTRY_GRACE_PERIOD);
		return endAt.isBefore(graceDeadline) ? endAt : graceDeadline;
	}
}
