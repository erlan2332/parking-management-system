package com.parking.backend.notification.sender;

import com.parking.backend.notification.config.ReservationMailProperties;
import com.parking.backend.notification.model.ReservationReminderMessage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
		prefix = "application.notifications.mail",
		name = "enabled",
		havingValue = "true"
)
public class SmtpReservationEmailSender implements ReservationEmailSender {

	private final JavaMailSender mailSender;
	private final ReservationMailProperties properties;

	public SmtpReservationEmailSender(JavaMailSender mailSender, ReservationMailProperties properties) {
		this.mailSender = mailSender;
		this.properties = properties;
	}

	@Override
	public boolean isEnabled() {
		return true;
	}

	@Override
	public void send(ReservationReminderMessage message) {
		SimpleMailMessage mail = new SimpleMailMessage();
		mail.setFrom(properties.getFrom());
		mail.setTo(message.recipient());
		mail.setSubject(message.subject());
		mail.setText(message.body());
		mailSender.send(mail);
	}
}
