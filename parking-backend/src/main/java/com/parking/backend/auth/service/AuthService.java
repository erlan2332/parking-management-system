package com.parking.backend.auth.service;

import com.parking.backend.auth.dto.AuthResponse;
import com.parking.backend.auth.dto.LoginRequest;
import com.parking.backend.auth.dto.RegisterRequest;
import com.parking.backend.auth.dto.RegisteredUserResponse;
import com.parking.backend.common.exception.EmailAlreadyExistsException;
import com.parking.backend.common.exception.InvalidCredentialsException;
import com.parking.backend.common.exception.InvalidPasswordException;
import com.parking.backend.common.persistence.DatabaseConstraintViolationDetector;
import com.parking.backend.security.service.JwtService;
import com.parking.backend.user.entity.UserEntity;
import com.parking.backend.user.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
public class AuthService {

	private static final int MIN_PASSWORD_CHARACTERS = 10;
	private static final int MAX_BCRYPT_PASSWORD_BYTES = 72;
	private static final String UNIQUE_EMAIL_CONSTRAINT = "uk_users_email_lower";

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;

	public AuthService(
			UserRepository userRepository,
			PasswordEncoder passwordEncoder,
			JwtService jwtService
	) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
	}

	@Transactional
	public RegisteredUserResponse register(RegisterRequest request) {
		String email = normalizeEmail(request.email());
		validateRegistrationPassword(request.password());

		if (userRepository.existsByEmailIgnoreCase(email)) {
			throw new EmailAlreadyExistsException();
		}

		UserEntity user = new UserEntity(email, passwordEncoder.encode(request.password()));
		try {
			UserEntity savedUser = userRepository.saveAndFlush(user);
			return new RegisteredUserResponse(savedUser.getId(), savedUser.getEmail());
		} catch (DataIntegrityViolationException exception) {
			if (DatabaseConstraintViolationDetector.hasConstraint(exception, UNIQUE_EMAIL_CONSTRAINT)) {
				throw new EmailAlreadyExistsException(exception);
			}
			throw exception;
		}
	}

	@Transactional(readOnly = true)
	public AuthResponse login(LoginRequest request) {
		String email = normalizeEmail(request.email());
		validateLoginPassword(request.password());
		UserEntity user = userRepository.findByEmailIgnoreCase(email)
				.orElseThrow(InvalidCredentialsException::new);

		if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
			throw new InvalidCredentialsException();
		}

		JwtService.IssuedToken token = jwtService.issueToken(user.getId());
		return new AuthResponse(token.value(), "Bearer", token.expiresInSeconds());
	}

	private static String normalizeEmail(String email) {
		if (email == null || email.isBlank()) {
			throw new InvalidCredentialsException();
		}
		return email.strip().toLowerCase(Locale.ROOT);
	}

	private static void validateRegistrationPassword(String password) {
		if (password == null || password.isBlank()) {
			throw new InvalidPasswordException("Password must not be blank");
		}

		int characterCount = password.codePointCount(0, password.length());
		if (characterCount < MIN_PASSWORD_CHARACTERS) {
			throw new InvalidPasswordException("Password must contain at least 10 characters");
		}
		if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BCRYPT_PASSWORD_BYTES) {
			throw new InvalidPasswordException("Password must not exceed 72 UTF-8 bytes");
		}
	}

	private static void validateLoginPassword(String password) {
		if (password == null
				|| password.isBlank()
				|| password.getBytes(StandardCharsets.UTF_8).length > MAX_BCRYPT_PASSWORD_BYTES) {
			throw new InvalidCredentialsException();
		}
	}
}
