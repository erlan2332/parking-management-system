package com.parking.backend.common.exception;

public class NoAvailableParkingSpotException extends RuntimeException {

	public NoAvailableParkingSpotException() {
		super("No parking spot is currently available for walk-in entry");
	}

	public NoAvailableParkingSpotException(Throwable cause) {
		super("No parking spot is currently available for walk-in entry", cause);
	}
}
