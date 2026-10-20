package com.parking.backend.notification;

import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Component
public class ReservationReminderMessageFactory {

	private static final String SUBJECT =
			"Parking Management System — бронирование скоро заканчивается";
	private static final ZoneId PARKING_ZONE = ZoneId.of("Asia/Bishkek");
	private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

	public ReservationReminderMessage create(ClaimedReservationReminder reminder) {
		String localEndTime = TIME_FORMAT.format(reminder.plannedEndAt().atZone(PARKING_ZONE));
		String body = """
				Здравствуйте!

				Ваше бронирование парковочного места %s скоро заканчивается.

				Автомобиль: %s

				Парковочное место: %s

				Плановое окончание: %s (Asia/Bishkek)

				Пожалуйста, учитывайте оставшееся время при планировании выезда.

				Parking Management System.
				""".formatted(
				reminder.spotCode(),
				reminder.vehiclePlate(),
				reminder.spotCode(),
				localEndTime
		);
		return new ReservationReminderMessage(reminder.recipient(), SUBJECT, body);
	}
}
