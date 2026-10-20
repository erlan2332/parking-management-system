package com.parking.backend.common.exception;

public class DuplicateVehiclePlateException extends RuntimeException {

	public DuplicateVehiclePlateException(String normalizedPlate) {
		super("A vehicle with plate " + normalizedPlate + " already exists");
	}

	public DuplicateVehiclePlateException(String normalizedPlate, Throwable cause) {
		super("A vehicle with plate " + normalizedPlate + " already exists", cause);
	}
}
