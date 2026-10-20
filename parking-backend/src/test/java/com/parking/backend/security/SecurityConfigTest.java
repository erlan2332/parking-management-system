package com.parking.backend.security;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SecurityConfigTest {

	private final SecurityConfig securityConfig = new SecurityConfig();

	@Test
	void rejectsMissingJwtSecret() {
		IllegalStateException exception = assertThrows(
				IllegalStateException.class,
				() -> securityConfig.jwtSecretKey(properties(""))
		);

		assertEquals("JWT_SECRET_BASE64 must be configured", exception.getMessage());
	}

	@Test
	void rejectsInvalidBase64JwtSecret() {
		assertThrows(IllegalStateException.class, () -> securityConfig.jwtSecretKey(properties("not-base64")));
	}

	@Test
	void rejectsJwtSecretShorterThan256Bits() {
		assertThrows(IllegalStateException.class, () -> securityConfig.jwtSecretKey(properties("c2hvcnQ=")));
	}

	private static JwtProperties properties(String secret) {
		return new JwtProperties(secret, "parking-management-system", Duration.ofHours(1));
	}
}
