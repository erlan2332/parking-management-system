package com.parking.backend.security;

import com.parking.backend.security.config.JwtProperties;
import com.parking.backend.security.service.JwtService;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtServiceTest {

	@Test
	void createsOneHourTokenWithRequiredClaims() {
		Instant now = Instant.parse("2026-10-20T10:00:00Z");
		JwtEncoder encoder = mock(JwtEncoder.class);
		Jwt encodedJwt = Jwt.withTokenValue("signed-token")
				.header("alg", "HS256")
				.subject("42")
				.build();
		when(encoder.encode(org.mockito.ArgumentMatchers.any(JwtEncoderParameters.class))).thenReturn(encodedJwt);
		JwtProperties properties = new JwtProperties("unused-in-this-test", "parking-management-system", Duration.ofHours(1));
		JwtService jwtService = new JwtService(encoder, properties, Clock.fixed(now, ZoneOffset.UTC));

		JwtService.IssuedToken token = jwtService.issueToken(42L);

		ArgumentCaptor<JwtEncoderParameters> parametersCaptor =
				ArgumentCaptor.forClass(JwtEncoderParameters.class);
		verify(encoder).encode(parametersCaptor.capture());
		assertEquals("42", parametersCaptor.getValue().getClaims().getSubject());
		assertEquals(
				"parking-management-system",
				parametersCaptor.getValue().getClaims().getClaimAsString("iss")
		);
		assertEquals(now, parametersCaptor.getValue().getClaims().getIssuedAt());
		assertEquals(now.plusSeconds(3600), parametersCaptor.getValue().getClaims().getExpiresAt());
		assertEquals("signed-token", token.value());
		assertEquals(3600, token.expiresInSeconds());
	}
}
