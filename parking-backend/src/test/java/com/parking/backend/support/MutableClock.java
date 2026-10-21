package com.parking.backend.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class MutableClock extends Clock {

	private final AtomicReference<Instant> instant;

	public MutableClock(Instant instant) {
		this.instant = new AtomicReference<>(Objects.requireNonNull(instant, "Instant must not be null"));
	}

	public void set(Instant value) {
		instant.set(Objects.requireNonNull(value, "Instant must not be null"));
	}

	public void advance(Duration duration) {
		instant.updateAndGet(current -> current.plus(Objects.requireNonNull(duration, "Duration must not be null")));
	}

	@Override
	public ZoneId getZone() {
		return ZoneOffset.UTC;
	}

	@Override
	public Clock withZone(ZoneId zone) {
		return ZoneOffset.UTC.equals(zone) ? this : Clock.fixed(instant(), zone);
	}

	@Override
	public Instant instant() {
		return instant.get();
	}
}
