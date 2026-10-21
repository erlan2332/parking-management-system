package com.parking.backend.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "application.security.cors")
public record RestCorsProperties(List<String> allowedOrigins) {

	public RestCorsProperties {
		if (allowedOrigins == null || allowedOrigins.isEmpty()) {
			throw new IllegalStateException("At least one REST API origin must be configured");
		}
		allowedOrigins = allowedOrigins.stream()
				.map(origin -> origin == null ? "" : origin.strip())
				.toList();
		if (allowedOrigins.stream().anyMatch(String::isBlank)) {
			throw new IllegalStateException("REST API origins must not be blank");
		}
		if (allowedOrigins.contains("*")) {
			throw new IllegalStateException("Wildcard REST API origins are not allowed");
		}
	}
}
