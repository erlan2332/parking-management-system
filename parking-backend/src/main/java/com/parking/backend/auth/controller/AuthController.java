package com.parking.backend.auth.controller;

import com.parking.backend.auth.service.AuthService;
import com.parking.backend.auth.dto.AuthResponse;
import com.parking.backend.auth.dto.LoginRequest;
import com.parking.backend.auth.dto.RegisterRequest;
import com.parking.backend.auth.dto.RegisteredUserResponse;
import com.parking.backend.auth.mapper.AuthMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

	private final AuthService authService;
	private final AuthMapper authMapper;

	@PostMapping("/register")
	public ResponseEntity<RegisteredUserResponse> register(@Valid @RequestBody RegisterRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(authMapper.toRegisteredUserResponse(
				authService.register(request.email(), request.password())
		));
	}

	@PostMapping("/login")
	public AuthResponse login(@Valid @RequestBody LoginRequest request) {
		return authMapper.toAuthResponse(authService.login(request.email(), request.password()));
	}
}
