package com.parking.backend.notification.sender;

import com.parking.backend.notification.model.ReservationReminderMessage;

public interface ReservationEmailSender {

	boolean isEnabled();

	void send(ReservationReminderMessage message);
}
