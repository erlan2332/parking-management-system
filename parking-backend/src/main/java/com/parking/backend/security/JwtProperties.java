package com.parking.backend.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "application.security.jwt")
public record JwtProperties(
		String secretBase64,
		String issuer,
		Duration accessTokenTtl
) {
}
