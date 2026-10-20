package com.parking.backend.common.exception;

public class ReservationEntryConflictException extends RuntimeException {

	public ReservationEntryConflictException(String message) {
		super(message);
	}

	public ReservationEntryConflictException(String message, Throwable cause) {
		super(message, cause);
	}
}
