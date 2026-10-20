package com.parking.backend.common.exception;

public class VehicleNotFoundException extends RuntimeException {

	public VehicleNotFoundException() {
		super("Vehicle was not found");
	}

	public VehicleNotFoundException(Long vehicleId) {
		super("Vehicle with id " + vehicleId + " was not found");
	}
}
