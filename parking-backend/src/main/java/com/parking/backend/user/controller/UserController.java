package com.parking.backend.user.controller;

import com.parking.backend.user.service.UserService;
import lombok.RequiredArgsConstructor;

import com.parking.backend.security.web.CurrentUserIdResolver;
import com.parking.backend.user.dto.UserResponse;
import com.parking.backend.user.mapper.UserMapper;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

	private final UserService userService;
	private final UserMapper userMapper;
	private final CurrentUserIdResolver currentUserIdResolver;

	@GetMapping("/me")
	public UserResponse getCurrentUser(@AuthenticationPrincipal Jwt jwt) {
		Long userId = currentUserIdResolver.resolve(jwt);
		return userMapper.toResponse(userService.getById(userId));
	}
}
