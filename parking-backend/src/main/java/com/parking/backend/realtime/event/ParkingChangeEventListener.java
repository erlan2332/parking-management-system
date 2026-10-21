package com.parking.backend.realtime.event;

import com.parking.backend.realtime.config.ParkingWebSocketConfig;
import lombok.RequiredArgsConstructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ParkingChangeEventListener {

	private static final Logger LOGGER = LoggerFactory.getLogger(ParkingChangeEventListener.class);

	private final SimpMessagingTemplate messagingTemplate;

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void publishAfterCommit(ParkingAvailabilityChangedEvent event) {
		try {
			messagingTemplate.convertAndSend(ParkingWebSocketConfig.CHANGES_TOPIC, event);
		} catch (RuntimeException exception) {
			LOGGER.warn(
					"Could not publish parking change {} for spot {}",
					event.reason(),
					event.spotId(),
					exception
			);
		}
	}
}
