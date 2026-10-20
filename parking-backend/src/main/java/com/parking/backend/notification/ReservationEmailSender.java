package com.parking.backend.notification;

public interface ReservationEmailSender {

	boolean isEnabled();

	void send(ReservationReminderMessage message);
}
