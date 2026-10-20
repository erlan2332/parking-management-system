package com.parking.backend.common.exception;

public class ParkingVisitNotCompletedException extends RuntimeException {

	public ParkingVisitNotCompletedException() {
		super("Parking visit is still active");
	}
}
