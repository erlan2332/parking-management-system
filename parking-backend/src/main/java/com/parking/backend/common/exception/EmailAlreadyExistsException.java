package com.parking.backend.common.exception;

public class EmailAlreadyExistsException extends RuntimeException {

	public EmailAlreadyExistsException() {
		super("A user with this email already exists");
	}

	public EmailAlreadyExistsException(Throwable cause) {
		super("A user with this email already exists", cause);
	}
}
