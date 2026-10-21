package com.parking.backend.notification;

import com.parking.backend.notification.config.ReservationMailProperties;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReservationMailPropertiesTest {

	@Test
	void disabledMailNeedsNoSmtpConfiguration() {
		ReservationMailProperties properties = new ReservationMailProperties();

		assertDoesNotThrow(properties::afterPropertiesSet);
	}

	@Test
	void enabledMailReportsMissingRequiredSettings() {
		ReservationMailProperties properties = new ReservationMailProperties();
		properties.setEnabled(true);

		IllegalStateException exception = assertThrows(
				IllegalStateException.class,
				properties::afterPropertiesSet
		);

		assertEquals("MAIL_HOST must be configured when MAIL_ENABLED=true", exception.getMessage());
	}
}
