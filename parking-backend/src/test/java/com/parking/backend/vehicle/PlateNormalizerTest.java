package com.parking.backend.vehicle;

import com.parking.backend.vehicle.service.PlateNormalizer;

import com.parking.backend.common.exception.InvalidPlateNumberException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlateNormalizerTest {

	private final PlateNormalizer plateNormalizer = new PlateNormalizer();

	@Test
	void normalizesSpacesAndLetterCase() {
		assertEquals("01KG123ABC", plateNormalizer.normalize(" 01 kg 123 abc "));
	}

	@Test
	void removesHyphens() {
		assertEquals("01KG123ABC", plateNormalizer.normalize("01-KG-123-ABC"));
	}

	@Test
	void handlesRepeatedSeparators() {
		assertEquals("01KG123ABC", plateNormalizer.normalize("01--  KG---123   ABC"));
	}

	@Test
	void rejectsNullBlankAndSeparatorOnlyValues() {
		assertThrows(InvalidPlateNumberException.class, () -> plateNormalizer.normalize(null));
		assertThrows(InvalidPlateNumberException.class, () -> plateNormalizer.normalize("   "));
		assertThrows(InvalidPlateNumberException.class, () -> plateNormalizer.normalize(" -- - "));
	}

	@Test
	void rejectsUnsupportedCharacters() {
		assertThrows(
				InvalidPlateNumberException.class,
				() -> plateNormalizer.normalize("01/KG/123@ABC")
		);
	}

	@Test
	void doesNotConvertCyrillicLettersToLatin() {
		String latinPlate = plateNormalizer.normalize("A123BC");
		String cyrillicPlate = plateNormalizer.normalize("А123ВС");

		assertNotEquals(latinPlate, cyrillicPlate);
	}
}
