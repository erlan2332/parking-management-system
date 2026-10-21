package com.parking.backend.security.service;

import com.parking.backend.security.config.JwtProperties;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Service
public class JwtService {

	private final JwtEncoder jwtEncoder;
	private final JwtProperties jwtProperties;
	private final Clock clock;

	public JwtService(JwtEncoder jwtEncoder, JwtProperties jwtProperties, Clock clock) {
		this.jwtEncoder = jwtEncoder;
		this.jwtProperties = jwtProperties;
		this.clock = clock;

		Duration tokenTtl = jwtProperties.accessTokenTtl();
		if (tokenTtl == null || tokenTtl.isZero() || tokenTtl.isNegative()) {
			throw new IllegalStateException("JWT access token TTL must be positive");
		}
	}

	public IssuedToken issueToken(Long userId) {
		Instant issuedAt = clock.instant();
		Instant expiresAt = issuedAt.plus(jwtProperties.accessTokenTtl());
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.subject(userId.toString())
				.issuedAt(issuedAt)
				.expiresAt(expiresAt)
				.issuer(jwtProperties.issuer())
				.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256)
				.type("JWT")
				.build();
		String tokenValue = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

		return new IssuedToken(tokenValue, jwtProperties.accessTokenTtl().toSeconds());
	}

	public record IssuedToken(String value, long expiresInSeconds) {
	}
}
