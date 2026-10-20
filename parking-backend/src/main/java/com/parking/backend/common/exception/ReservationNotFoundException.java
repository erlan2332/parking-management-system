package com.parking.backend.common.exception;

public class ReservationNotFoundException extends RuntimeException {

	public ReservationNotFoundException(Long reservationId) {
		super("Reservation was not found: " + reservationId);
	}
}
