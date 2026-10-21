package com.parking.backend.realtime;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class ParkingChangeEventListenerTest {

	@Test
	void brokerFailureDoesNotEscapeAfterCommitListener() {
		SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
		ParkingChangeEventListener listener = new ParkingChangeEventListener(messagingTemplate);
		ParkingAvailabilityChangedEvent event = new ParkingAvailabilityChangedEvent(
				ParkingAvailabilityChangedEvent.TYPE,
				ParkingChangeReason.HOLD_CREATED,
				1L,
				"A1",
				OffsetDateTime.parse("2026-12-01T14:00:00+06:00")
		);
		doThrow(new IllegalStateException("broker unavailable"))
				.when(messagingTemplate)
				.convertAndSend(
						eq(ParkingWebSocketConfig.CHANGES_TOPIC),
						any(ParkingAvailabilityChangedEvent.class)
				);

		assertDoesNotThrow(() -> listener.publishAfterCommit(event));
	}
}
