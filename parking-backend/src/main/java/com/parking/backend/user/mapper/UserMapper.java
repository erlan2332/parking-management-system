package com.parking.backend.user.mapper;

import com.parking.backend.user.dto.UserResponse;
import com.parking.backend.user.entity.UserEntity;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

	public UserResponse toResponse(UserEntity user) {
		return new UserResponse(user.getId(), user.getEmail(), user.getCreatedAt());
	}
}
