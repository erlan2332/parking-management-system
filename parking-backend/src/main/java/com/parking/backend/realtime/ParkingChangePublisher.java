package com.parking.backend.realtime;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;

@Component
public class ParkingChangePublisher {

	private static final ZoneId PARKING_ZONE = ZoneId.of("Asia/Bishkek");

	private final ApplicationEventPublisher eventPublisher;
	private final Clock clock;

	public ParkingChangePublisher(ApplicationEventPublisher eventPublisher, Clock clock) {
		this.eventPublisher = eventPublisher;
		this.clock = clock;
	}

	public void publish(ParkingChangeReason reason, Long spotId, String spotCode) {
		eventPublisher.publishEvent(new ParkingAvailabilityChangedEvent(
				ParkingAvailabilityChangedEvent.TYPE,
				reason,
				spotId,
				spotCode,
				OffsetDateTime.ofInstant(clock.instant(), PARKING_ZONE)
		));
	}
}
