package com.parking.backend.notification;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.nio.charset.StandardCharsets;
import java.util.Properties;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ReservationMailProperties.class)
public class ReservationMailConfiguration {

	@Bean
	@ConditionalOnProperty(
			prefix = "application.notifications.mail",
			name = "enabled",
			havingValue = "true"
	)
	JavaMailSender reservationJavaMailSender(ReservationMailProperties properties) {
		JavaMailSenderImpl sender = new JavaMailSenderImpl();
		sender.setHost(properties.getHost());
		sender.setPort(properties.getPort());
		sender.setUsername(properties.getUsername());
		sender.setPassword(properties.getPassword());
		sender.setDefaultEncoding(StandardCharsets.UTF_8.name());

		Properties mailProperties = sender.getJavaMailProperties();
		mailProperties.setProperty("mail.smtp.auth", Boolean.toString(!properties.getUsername().isBlank()));
		mailProperties.setProperty(
				"mail.smtp.starttls.enable",
				Boolean.toString(properties.isStartTlsEnabled())
		);
		mailProperties.setProperty(
				"mail.smtp.connectiontimeout",
				Long.toString(properties.getConnectTimeout().toMillis())
		);
		mailProperties.setProperty(
				"mail.smtp.timeout",
				Long.toString(properties.getReadTimeout().toMillis())
		);
		mailProperties.setProperty(
				"mail.smtp.writetimeout",
				Long.toString(properties.getWriteTimeout().toMillis())
		);
		return sender;
	}
}
