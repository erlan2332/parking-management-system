package com.parking.backend.realtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ParkingChangeEventListener {

	private static final Logger LOGGER = LoggerFactory.getLogger(ParkingChangeEventListener.class);

	private final SimpMessagingTemplate messagingTemplate;

	public ParkingChangeEventListener(SimpMessagingTemplate messagingTemplate) {
		this.messagingTemplate = messagingTemplate;
	}

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
