package com.parking.backend.common.exception;

public class VehicleAlreadyParkedException extends RuntimeException {

	public VehicleAlreadyParkedException() {
		super("Vehicle already has an active parking visit");
	}

	public VehicleAlreadyParkedException(Throwable cause) {
		super("Vehicle already has an active parking visit", cause);
	}
}
