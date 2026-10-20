package com.parking.backend.common.exception;

public class ParkingVisitNotFoundException extends RuntimeException {

	public ParkingVisitNotFoundException() {
		super("Parking visit was not found");
	}
}
