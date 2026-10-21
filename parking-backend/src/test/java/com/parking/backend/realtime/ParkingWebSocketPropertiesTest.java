package com.parking.backend.realtime;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ParkingWebSocketPropertiesTest {

	@Test
	void normalizesExplicitAllowedOrigins() {
		ParkingWebSocketProperties properties = new ParkingWebSocketProperties(List.of(
				" http://localhost:5173 ",
				"https://parking.example"
		));

		assertEquals(
				List.of("http://localhost:5173", "https://parking.example"),
				properties.allowedOrigins()
		);
	}

	@Test
	void rejectsWildcardOrigin() {
		assertThrows(
				IllegalStateException.class,
				() -> new ParkingWebSocketProperties(List.of("*"))
		);
	}
}
