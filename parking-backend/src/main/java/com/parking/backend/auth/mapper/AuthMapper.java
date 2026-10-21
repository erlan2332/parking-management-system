package com.parking.backend.auth.mapper;

import com.parking.backend.auth.dto.AuthResponse;
import com.parking.backend.auth.dto.RegisteredUserResponse;
import com.parking.backend.security.service.JwtService;
import com.parking.backend.user.entity.UserEntity;
import org.springframework.stereotype.Component;

@Component
public class AuthMapper {

	public RegisteredUserResponse toRegisteredUserResponse(UserEntity user) {
		return new RegisteredUserResponse(user.getId(), user.getEmail());
	}

	public AuthResponse toAuthResponse(JwtService.IssuedToken token) {
		return new AuthResponse(token.value(), "Bearer", token.expiresInSeconds());
	}
}
