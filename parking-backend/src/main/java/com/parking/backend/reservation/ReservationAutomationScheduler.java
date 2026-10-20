package com.parking.backend.reservation;

import com.parking.backend.notification.ReservationReminderService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReservationAutomationScheduler {

	private final ReservationService reservationService;
	private final ReservationReminderService reminderService;

	public ReservationAutomationScheduler(
			ReservationService reservationService,
			ReservationReminderService reminderService
	) {
		this.reservationService = reservationService;
		this.reminderService = reminderService;
	}

	@Scheduled(
			fixedDelayString = "${application.reservations.automation-delay:PT30S}",
			initialDelayString = "${application.reservations.automation-delay:PT30S}"
	)
	public void processReservationAutomation() {
		reservationService.expireHolds();
		reservationService.expireNoShows();
		reminderService.processDueReminders();
	}
}
