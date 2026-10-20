package com.parking.backend.common.exception;

public class NoActiveVisitException extends RuntimeException {

	public NoActiveVisitException() {
		super("Vehicle does not have an active parking visit");
	}
}
