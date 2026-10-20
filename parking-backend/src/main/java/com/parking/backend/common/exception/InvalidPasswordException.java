package com.parking.backend.common.exception;

public class InvalidPasswordException extends IllegalArgumentException {

	public InvalidPasswordException(String message) {
		super(message);
	}
}
