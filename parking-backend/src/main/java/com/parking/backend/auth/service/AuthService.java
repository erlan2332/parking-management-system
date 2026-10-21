package com.parking.backend.auth.service;

import com.parking.backend.common.exception.EmailAlreadyExistsException;
import com.parking.backend.common.exception.InvalidCredentialsException;
import com.parking.backend.common.exception.InvalidPasswordException;
import com.parking.backend.common.persistence.DatabaseConstraintViolationDetector;
import com.parking.backend.security.service.JwtService;
import com.parking.backend.user.entity.UserEntity;
import com.parking.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {

	private static final int MIN_PASSWORD_CHARACTERS = 10;
	private static final int MAX_BCRYPT_PASSWORD_BYTES = 72;
	private static final String UNIQUE_EMAIL_CONSTRAINT = "uk_users_email_lower";

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;

	@Transactional
	public UserEntity register(String rawEmail, String password) {
		String email = normalizeEmail(rawEmail);
		validateRegistrationPassword(password);

		if (userRepository.existsByEmailIgnoreCase(email)) {
			throw new EmailAlreadyExistsException();
		}

		UserEntity user = new UserEntity(email, passwordEncoder.encode(password));
		try {
			return userRepository.saveAndFlush(user);
		} catch (DataIntegrityViolationException exception) {
			if (DatabaseConstraintViolationDetector.hasConstraint(exception, UNIQUE_EMAIL_CONSTRAINT)) {
				throw new EmailAlreadyExistsException(exception);
			}
			throw exception;
		}
	}

	@Transactional(readOnly = true)
	public JwtService.IssuedToken login(String rawEmail, String password) {
		String email = normalizeEmail(rawEmail);
		validateLoginPassword(password);
		UserEntity user = userRepository.findByEmailIgnoreCase(email)
				.orElseThrow(InvalidCredentialsException::new);

		if (!passwordEncoder.matches(password, user.getPasswordHash())) {
			throw new InvalidCredentialsException();
		}

		return jwtService.issueToken(user.getId());
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
