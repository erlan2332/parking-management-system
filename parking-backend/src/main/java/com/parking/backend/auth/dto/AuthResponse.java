package com.parking.backend.auth.dto;

public record AuthResponse(
		String accessToken,
		String tokenType,
		long expiresIn
) {
}
