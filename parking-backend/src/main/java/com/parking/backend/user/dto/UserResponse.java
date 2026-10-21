package com.parking.backend.user.dto;

import com.parking.backend.user.entity.UserEntity;

import java.time.Instant;

public record UserResponse(
		Long id,
		String email,
		Instant createdAt
) {

	public static UserResponse from(UserEntity user) {
		return new UserResponse(user.getId(), user.getEmail(), user.getCreatedAt());
	}
}
