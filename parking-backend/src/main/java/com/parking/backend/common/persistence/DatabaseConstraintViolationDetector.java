package com.parking.backend.common.persistence;

import org.hibernate.exception.ConstraintViolationException;

public final class DatabaseConstraintViolationDetector {

	private DatabaseConstraintViolationDetector() {
	}

	public static boolean hasConstraint(Throwable exception, String constraintName) {
		Throwable currentCause = exception;
		while (currentCause != null) {
			if (currentCause instanceof ConstraintViolationException constraintViolation) {
				return constraintName.equals(constraintViolation.getConstraintName());
			}
			currentCause = currentCause.getCause();
		}
		return false;
	}
}
