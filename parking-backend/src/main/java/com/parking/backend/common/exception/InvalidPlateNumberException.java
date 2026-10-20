package com.parking.backend.common.exception;

public class InvalidPlateNumberException extends IllegalArgumentException {

	public InvalidPlateNumberException(String message) {
		super(message);
	}
}
