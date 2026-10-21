package com.parking.backend.security.web;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

final class SecurityErrorWriter {

	private SecurityErrorWriter() {
	}

	static void write(HttpServletResponse response, int status, String code, String message) throws IOException {
		response.setStatus(status);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.getWriter().write(
				"{\"code\":\"%s\",\"message\":\"%s\",\"timestamp\":\"%s\"}"
						.formatted(code, message, Instant.now())
		);
	}
}
