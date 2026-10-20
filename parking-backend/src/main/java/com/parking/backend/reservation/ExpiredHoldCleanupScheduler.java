package com.parking.backend.reservation;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ExpiredHoldCleanupScheduler {

	private final ReservationService reservationService;

	public ExpiredHoldCleanupScheduler(ReservationService reservationService) {
		this.reservationService = reservationService;
	}

	@Scheduled(
			fixedDelayString = "${application.reservations.hold-cleanup-delay:PT20S}",
			initialDelayString = "${application.reservations.hold-cleanup-delay:PT20S}"
	)
	public void expireHolds() {
		reservationService.expireHolds();
	}
}
