package com.parking.backend.vehicle.service;

import com.parking.backend.common.exception.InvalidPlateNumberException;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public final class PlateNormalizer {

	public static final int MAX_NORMALIZED_LENGTH = 32;

	public String normalize(String plateNumber) {
		if (plateNumber == null || plateNumber.isBlank()) {
			throw new InvalidPlateNumberException("Plate number must not be blank");
		}

		String upperCasePlate = plateNumber.strip().toUpperCase(Locale.ROOT);
		StringBuilder normalizedPlate = new StringBuilder(upperCasePlate.length());

		upperCasePlate.codePoints().forEach(codePoint -> {
			if (Character.isLetterOrDigit(codePoint)) {
				normalizedPlate.appendCodePoint(codePoint);
			} else if (!isAllowedSeparator(codePoint)) {
				throw new InvalidPlateNumberException("Plate number contains unsupported characters");
			}
		});

		int normalizedLength = normalizedPlate.codePointCount(0, normalizedPlate.length());
		if (normalizedLength == 0) {
			throw new InvalidPlateNumberException("Plate number must contain letters or digits");
		}
		if (normalizedLength > MAX_NORMALIZED_LENGTH) {
			throw new InvalidPlateNumberException("Normalized plate number must not exceed 32 characters");
		}

		return normalizedPlate.toString();
	}

	private static boolean isAllowedSeparator(int codePoint) {
		return codePoint == '-' || Character.isWhitespace(codePoint);
	}
}
