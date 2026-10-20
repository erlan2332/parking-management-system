package com.parking.backend.common.exception;

public class ReservationNoShowExpiredException extends ReservationEntryConflictException {

	public ReservationNoShowExpiredException() {
		super("Reservation entry window has expired");
	}
}
