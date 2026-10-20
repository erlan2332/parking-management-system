package com.parking.backend.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.time.Clock;
import java.util.Base64;

import static org.springframework.http.HttpMethod.POST;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

	private static final int MIN_SECRET_BYTES = 32;

	@Bean
	SecurityFilterChain securityFilterChain(
			HttpSecurity http,
			RestAuthenticationEntryPoint authenticationEntryPoint,
			RestAccessDeniedHandler accessDeniedHandler
	) throws Exception {
		http
				.csrf(csrf -> csrf.disable())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers(POST, "/api/auth/register", "/api/auth/login").permitAll()
						.anyRequest().authenticated()
				)
				.exceptionHandling(exceptions -> exceptions
						.authenticationEntryPoint(authenticationEntryPoint)
						.accessDeniedHandler(accessDeniedHandler)
				)
				.oauth2ResourceServer(resourceServer -> resourceServer
						.jwt(Customizer.withDefaults())
						.authenticationEntryPoint(authenticationEntryPoint)
						.accessDeniedHandler(accessDeniedHandler)
				);

		return http.build();
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	SecretKey jwtSecretKey(JwtProperties properties) {
		if (properties.issuer() == null || properties.issuer().isBlank()) {
			throw new IllegalStateException("JWT issuer must be configured");
		}
		String configuredSecret = properties.secretBase64();
		if (configuredSecret == null || configuredSecret.isBlank()) {
			throw new IllegalStateException("JWT_SECRET_BASE64 must be configured");
		}

		byte[] secretBytes;
		try {
			secretBytes = Base64.getDecoder().decode(configuredSecret);
		} catch (IllegalArgumentException exception) {
			throw new IllegalStateException("JWT_SECRET_BASE64 must contain valid Base64", exception);
		}

		if (secretBytes.length < MIN_SECRET_BYTES) {
			throw new IllegalStateException("JWT_SECRET_BASE64 must decode to at least 32 bytes");
		}
		return new SecretKeySpec(secretBytes, "HmacSHA256");
	}

	@Bean
	JwtEncoder jwtEncoder(SecretKey secretKey) {
		return NimbusJwtEncoder.withSecretKey(secretKey)
				.algorithm(MacAlgorithm.HS256)
				.build();
	}

	@Bean
	JwtDecoder jwtDecoder(SecretKey secretKey, JwtProperties properties) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey)
				.macAlgorithm(MacAlgorithm.HS256)
				.build();
		OAuth2TokenValidator<Jwt> issuerAndTimeValidator =
				JwtValidators.createDefaultWithIssuer(properties.issuer());
		OAuth2TokenValidator<Jwt> subjectValidator = this::validateSubject;
		decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuerAndTimeValidator, subjectValidator));
		return decoder;
	}

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

	private OAuth2TokenValidatorResult validateSubject(Jwt jwt) {
		try {
			if (Long.parseLong(jwt.getSubject()) > 0) {
				return OAuth2TokenValidatorResult.success();
			}
		} catch (NumberFormatException | NullPointerException ignored) {
			// The generic authentication response intentionally hides token validation details.
		}

		OAuth2Error error = new OAuth2Error("invalid_token", "JWT subject must be a positive user ID", null);
		return OAuth2TokenValidatorResult.failure(error);
	}
}
