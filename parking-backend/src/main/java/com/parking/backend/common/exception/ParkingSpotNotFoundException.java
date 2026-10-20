package com.parking.backend.common.exception;

public class ParkingSpotNotFoundException extends RuntimeException {

	public ParkingSpotNotFoundException(Long parkingSpotId) {
		super("Parking spot was not found: " + parkingSpotId);
	}
}
