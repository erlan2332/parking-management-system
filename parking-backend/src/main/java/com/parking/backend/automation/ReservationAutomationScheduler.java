package com.parking.backend.automation;

import com.parking.backend.notification.service.ReservationReminderService;
import com.parking.backend.reservation.service.ReservationExpirationService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReservationAutomationScheduler {

	private final ReservationExpirationService expirationService;
	private final ReservationReminderService reminderService;

	@Scheduled(
			fixedDelayString = "${application.reservations.automation-delay:PT30S}",
			initialDelayString = "${application.reservations.automation-delay:PT30S}"
	)
	public void processReservationAutomation() {
		expirationService.expireDueHolds();
		expirationService.expireDueNoShows();
		reminderService.processDueReminders();
	}
}
