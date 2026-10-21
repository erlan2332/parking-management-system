package com.parking.backend.realtime.config;

import com.parking.backend.realtime.security.ParkingStompChannelInterceptor;
import lombok.RequiredArgsConstructor;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration(proxyBeanMethods = false)
@EnableWebSocketMessageBroker
@EnableConfigurationProperties(ParkingWebSocketProperties.class)
@RequiredArgsConstructor
public class ParkingWebSocketConfig implements WebSocketMessageBrokerConfigurer {

	public static final String ENDPOINT = "/ws/parking";
	public static final String CHANGES_TOPIC = "/topic/parking/changes";

	private final ParkingWebSocketProperties properties;
	private final ParkingStompChannelInterceptor channelInterceptor;

	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry) {
		registry.addEndpoint(ENDPOINT)
				.setAllowedOrigins(properties.allowedOrigins().toArray(String[]::new));
	}

	@Override
	public void configureMessageBroker(MessageBrokerRegistry registry) {
		registry.enableSimpleBroker("/topic");
		registry.setApplicationDestinationPrefixes("/app");
	}

	@Override
	public void configureClientInboundChannel(ChannelRegistration registration) {
		registration.interceptors(channelInterceptor);
	}
}
