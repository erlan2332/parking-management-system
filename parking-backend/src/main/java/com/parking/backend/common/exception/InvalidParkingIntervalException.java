package com.parking.backend.common.exception;

public class InvalidParkingIntervalException extends RuntimeException {

	public InvalidParkingIntervalException() {
		super("startAt must be earlier than endAt");
	}
}
