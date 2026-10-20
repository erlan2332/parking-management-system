package com.parking.backend.realtime;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "application.realtime.websocket")
public record ParkingWebSocketProperties(List<String> allowedOrigins) {

	public ParkingWebSocketProperties {
		if (allowedOrigins == null || allowedOrigins.isEmpty()) {
			throw new IllegalStateException("At least one WebSocket origin must be configured");
		}
		allowedOrigins = allowedOrigins.stream()
				.map(origin -> origin == null ? "" : origin.strip())
				.toList();
		if (allowedOrigins.stream().anyMatch(String::isBlank)) {
			throw new IllegalStateException("WebSocket origins must not be blank");
		}
		if (allowedOrigins.contains("*")) {
			throw new IllegalStateException("Wildcard WebSocket origins are not allowed");
		}
	}
}
