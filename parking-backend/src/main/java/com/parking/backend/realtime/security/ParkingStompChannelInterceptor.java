package com.parking.backend.realtime.security;

import com.parking.backend.realtime.config.ParkingWebSocketConfig;

import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ParkingStompChannelInterceptor implements ChannelInterceptor {

	private static final String BEARER_PREFIX = "Bearer ";

	private final JwtDecoder jwtDecoder;

	public ParkingStompChannelInterceptor(JwtDecoder jwtDecoder) {
		this.jwtDecoder = jwtDecoder;
	}

	@Override
	public Message<?> preSend(Message<?> message, MessageChannel channel) {
		StompHeaderAccessor accessor = StompHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
		if (accessor == null || accessor.getCommand() == null) {
			return message;
		}

		StompCommand command = accessor.getCommand();
		if (command == StompCommand.CONNECT || command == StompCommand.STOMP) {
			accessor.setUser(authenticate(accessor.getFirstNativeHeader(HttpHeaders.AUTHORIZATION)));
			return message;
		}
		if (command == StompCommand.SUBSCRIBE) {
			requireAuthenticated(accessor.getUser());
			if (!ParkingWebSocketConfig.CHANGES_TOPIC.equals(accessor.getDestination())) {
				throw new AccessDeniedException("Subscription destination is not allowed");
			}
			return message;
		}
		if (command == StompCommand.UNSUBSCRIBE) {
			requireAuthenticated(accessor.getUser());
			return message;
		}
		if (command == StompCommand.DISCONNECT) {
			return message;
		}

		throw new AccessDeniedException("Client STOMP command is not allowed");
	}

	private Authentication authenticate(String authorization) {
		if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
			throw new BadCredentialsException("A Bearer token is required for STOMP CONNECT");
		}
		String tokenValue = authorization.substring(BEARER_PREFIX.length()).strip();
		if (tokenValue.isEmpty()) {
			throw new BadCredentialsException("A Bearer token is required for STOMP CONNECT");
		}

		try {
			Jwt jwt = jwtDecoder.decode(tokenValue);
			return new JwtAuthenticationToken(jwt, List.of(), jwt.getSubject());
		} catch (JwtException | IllegalArgumentException exception) {
			throw new BadCredentialsException("The STOMP Bearer token is invalid", exception);
		}
	}

	private static void requireAuthenticated(java.security.Principal principal) {
		if (!(principal instanceof Authentication authentication) || !authentication.isAuthenticated()) {
			throw new AccessDeniedException("An authenticated STOMP session is required");
		}
	}
}
