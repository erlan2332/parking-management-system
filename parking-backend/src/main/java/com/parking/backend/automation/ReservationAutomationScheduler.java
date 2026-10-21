package com.parking.backend.automation;

import com.parking.backend.reservation.service.ReservationService;
import com.parking.backend.notification.service.ReservationReminderService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReservationAutomationScheduler {

	private final ReservationService reservationService;
	private final ReservationReminderService reminderService;

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
