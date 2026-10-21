package com.parking.backend.auth;

import com.parking.backend.auth.service.AuthService;

import com.parking.backend.auth.dto.AuthResponse;
import com.parking.backend.auth.dto.LoginRequest;
import com.parking.backend.auth.dto.RegisterRequest;
import com.parking.backend.auth.dto.RegisteredUserResponse;
import com.parking.backend.common.exception.EmailAlreadyExistsException;
import com.parking.backend.common.exception.InvalidCredentialsException;
import com.parking.backend.security.service.JwtService;
import com.parking.backend.user.entity.UserEntity;
import com.parking.backend.user.repository.UserRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.SQLException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Mock
	private JwtService jwtService;

	private AuthService authService;

	@BeforeEach
	void setUp() {
		authService = new AuthService(userRepository, passwordEncoder, jwtService);
	}

	@Test
	void registersNormalizedEmailAndEncodedPassword() {
		when(userRepository.existsByEmailIgnoreCase("driver@example.com")).thenReturn(false);
		when(passwordEncoder.encode("SecurePassword123!")).thenReturn("bcrypt-hash");
		when(userRepository.saveAndFlush(org.mockito.ArgumentMatchers.any(UserEntity.class)))
				.thenAnswer(invocation -> {
					UserEntity user = invocation.getArgument(0);
					ReflectionTestUtils.setField(user, "id", 42L);
					return user;
				});

		RegisteredUserResponse response = authService.register(
				new RegisterRequest(" Driver@Example.COM ", "SecurePassword123!")
		);

		ArgumentCaptor<UserEntity> userCaptor = ArgumentCaptor.forClass(UserEntity.class);
		verify(userRepository).saveAndFlush(userCaptor.capture());
		assertEquals(42L, response.id());
		assertEquals("driver@example.com", response.email());
		assertEquals("bcrypt-hash", userCaptor.getValue().getPasswordHash());
	}

	@Test
	void rejectsKnownDuplicateEmail() {
		when(userRepository.existsByEmailIgnoreCase("driver@example.com")).thenReturn(true);

		assertThrows(
				EmailAlreadyExistsException.class,
				() -> authService.register(new RegisterRequest("driver@example.com", "SecurePassword123!"))
		);
	}

	@Test
	void translatesConcurrentEmailConstraintViolation() {
		when(userRepository.existsByEmailIgnoreCase("driver@example.com")).thenReturn(false);
		when(passwordEncoder.encode("SecurePassword123!")).thenReturn("bcrypt-hash");
		ConstraintViolationException uniqueEmailViolation = new ConstraintViolationException(
				"duplicate email",
				new SQLException("unique constraint violation"),
				"INSERT INTO users ...",
				"uk_users_email_lower"
		);
		when(userRepository.saveAndFlush(org.mockito.ArgumentMatchers.any(UserEntity.class)))
				.thenThrow(new DataIntegrityViolationException("duplicate email", uniqueEmailViolation));

		assertThrows(
				EmailAlreadyExistsException.class,
				() -> authService.register(new RegisterRequest("driver@example.com", "SecurePassword123!"))
		);
	}

	@Test
	void doesNotMaskUnrelatedDatabaseViolation() {
		when(userRepository.existsByEmailIgnoreCase("driver@example.com")).thenReturn(false);
		when(passwordEncoder.encode("SecurePassword123!")).thenReturn("bcrypt-hash");
		DataIntegrityViolationException databaseException =
				new DataIntegrityViolationException("unexpected database constraint");
		when(userRepository.saveAndFlush(org.mockito.ArgumentMatchers.any(UserEntity.class)))
				.thenThrow(databaseException);

		DataIntegrityViolationException thrownException = assertThrows(
				DataIntegrityViolationException.class,
				() -> authService.register(new RegisterRequest("driver@example.com", "SecurePassword123!"))
		);

		assertSame(databaseException, thrownException);
	}

	@Test
	void logsInWithValidCredentials() {
		UserEntity user = new UserEntity("driver@example.com", "bcrypt-hash");
		ReflectionTestUtils.setField(user, "id", 42L);
		when(userRepository.findByEmailIgnoreCase("driver@example.com")).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("SecurePassword123!", "bcrypt-hash")).thenReturn(true);
		when(jwtService.issueToken(42L)).thenReturn(new JwtService.IssuedToken("signed-token", 3600));

		AuthResponse response = authService.login(
				new LoginRequest(" Driver@Example.COM ", "SecurePassword123!")
		);

		assertEquals("signed-token", response.accessToken());
		assertEquals("Bearer", response.tokenType());
		assertEquals(3600, response.expiresIn());
	}

	@Test
	void usesSameErrorForMissingUserAndWrongPassword() {
		when(userRepository.findByEmailIgnoreCase("missing@example.com")).thenReturn(Optional.empty());

		assertThrows(
				InvalidCredentialsException.class,
				() -> authService.login(new LoginRequest("missing@example.com", "WrongPassword123!"))
		);

		UserEntity user = new UserEntity("driver@example.com", "bcrypt-hash");
		when(userRepository.findByEmailIgnoreCase("driver@example.com")).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("WrongPassword123!", "bcrypt-hash")).thenReturn(false);

		assertThrows(
				InvalidCredentialsException.class,
				() -> authService.login(new LoginRequest("driver@example.com", "WrongPassword123!"))
		);
	}
}
