package com.parking.backend.common.exception;

public class InvalidReservationRequestException extends RuntimeException {

	public InvalidReservationRequestException(String message) {
		super(message);
	}
}
