package com.parking.backend.notification;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "application.notifications.mail")
public class ReservationMailProperties implements InitializingBean {

	private boolean enabled;
	private String host = "";
	private int port = 587;
	private String username = "";
	private String password = "";
	private String from = "";
	private boolean startTlsEnabled;
	private Duration connectTimeout = Duration.ofSeconds(5);
	private Duration readTimeout = Duration.ofSeconds(5);
	private Duration writeTimeout = Duration.ofSeconds(5);

	@Override
	public void afterPropertiesSet() {
		if (!enabled) {
			return;
		}
		host = normalized(host);
		from = normalized(from);
		username = normalized(username);
		password = normalized(password);
		if (host.isBlank()) {
			throw new IllegalStateException("MAIL_HOST must be configured when MAIL_ENABLED=true");
		}
		if (from.isBlank()) {
			throw new IllegalStateException("MAIL_FROM must be configured when MAIL_ENABLED=true");
		}
		if (port <= 0 || port > 65_535) {
			throw new IllegalStateException("MAIL_PORT must be between 1 and 65535");
		}
		if (isBlank(username) != isBlank(password)) {
			throw new IllegalStateException("MAIL_USERNAME and MAIL_PASSWORD must be configured together");
		}
		requirePositive(connectTimeout, "MAIL_CONNECT_TIMEOUT");
		requirePositive(readTimeout, "MAIL_READ_TIMEOUT");
		requirePositive(writeTimeout, "MAIL_WRITE_TIMEOUT");
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public String getHost() {
		return host;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public String getFrom() {
		return from;
	}

	public void setFrom(String from) {
		this.from = from;
	}

	public boolean isStartTlsEnabled() {
		return startTlsEnabled;
	}

	public void setStartTlsEnabled(boolean startTlsEnabled) {
		this.startTlsEnabled = startTlsEnabled;
	}

	public Duration getConnectTimeout() {
		return connectTimeout;
	}

	public void setConnectTimeout(Duration connectTimeout) {
		this.connectTimeout = connectTimeout;
	}

	public Duration getReadTimeout() {
		return readTimeout;
	}

	public void setReadTimeout(Duration readTimeout) {
		this.readTimeout = readTimeout;
	}

	public Duration getWriteTimeout() {
		return writeTimeout;
	}

	public void setWriteTimeout(Duration writeTimeout) {
		this.writeTimeout = writeTimeout;
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	private static String normalized(String value) {
		return value == null ? "" : value.strip();
	}

	private static void requirePositive(Duration duration, String propertyName) {
		if (duration == null || duration.isZero() || duration.isNegative()) {
			throw new IllegalStateException(propertyName + " must be positive");
		}
	}
}
