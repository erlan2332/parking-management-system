package com.parking.backend.parking;

import com.parking.backend.common.exception.InvalidParkingIntervalException;
import com.parking.backend.common.exception.ParkingSpotNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ParkingSpotServiceTest {

	@Mock
	private ParkingSpotRepository parkingSpotRepository;

	private ParkingSpotService parkingSpotService;

	@BeforeEach
	void setUp() {
		parkingSpotService = new ParkingSpotService(parkingSpotRepository);
	}

	@Test
	void returnsFreeForActiveSpot() {
		ParkingSpotEntity spot = new ParkingSpotEntity("A1", true);

		assertEquals(ParkingSpotStatus.FREE, parkingSpotService.determineStatus(spot));
	}

	@Test
	void returnsUnavailableForInactiveSpot() {
		ParkingSpotEntity spot = new ParkingSpotEntity("A1", false);

		assertEquals(ParkingSpotStatus.UNAVAILABLE, parkingSpotService.determineStatus(spot));
	}

	@Test
	void throwsDomainExceptionForMissingSpot() {
		when(parkingSpotRepository.findById(999L)).thenReturn(Optional.empty());

		assertThrows(ParkingSpotNotFoundException.class, () -> parkingSpotService.getSpotById(999L));
	}

	@Test
	void acceptsIntervalWhenStartIsEarlierThanEnd() {
		when(parkingSpotRepository.findAllInNaturalCodeOrder())
				.thenReturn(List.of(new ParkingSpotEntity("A1", true)));

		List<ParkingSpotAvailability> result = parkingSpotService.getAvailability(
				OffsetDateTime.parse("2026-12-01T14:00:00+06:00"),
				OffsetDateTime.parse("2026-12-01T16:00:00+06:00")
		);

		assertEquals(1, result.size());
		assertTrue(result.getFirst().available());
	}

	@Test
	void rejectsZeroLengthInterval() {
		OffsetDateTime boundary = OffsetDateTime.parse("2026-12-01T14:00:00+06:00");

		assertThrows(
				InvalidParkingIntervalException.class,
				() -> parkingSpotService.getAvailability(boundary, boundary)
		);
		verifyNoInteractions(parkingSpotRepository);
	}

	@Test
	void rejectsIntervalWhenStartIsLaterThanEnd() {
		assertThrows(
				InvalidParkingIntervalException.class,
				() -> parkingSpotService.getAvailability(
						OffsetDateTime.parse("2026-12-01T16:00:00+06:00"),
						OffsetDateTime.parse("2026-12-01T14:00:00+06:00")
				)
		);
		verifyNoInteractions(parkingSpotRepository);
	}

	@Test
	void comparesIntervalsByInstantAcrossDifferentOffsets() {
		when(parkingSpotRepository.findAllInNaturalCodeOrder()).thenReturn(List.of());

		parkingSpotService.getAvailability(
				OffsetDateTime.parse("2026-12-01T14:00:00+06:00"),
				OffsetDateTime.parse("2026-12-01T09:00:00Z")
		);

		verify(parkingSpotRepository).findAllInNaturalCodeOrder();
	}

	@Test
	void readOperationsDoNotModifyParkingSpotData() {
		ParkingSpotEntity spot = new ParkingSpotEntity("B10", false);
		when(parkingSpotRepository.findAllInNaturalCodeOrder()).thenReturn(List.of(spot));

		List<ParkingSpotEntity> result = parkingSpotService.getAllSpots();

		assertEquals(List.of(spot), result);
		assertEquals("B10", spot.getCode());
		assertFalse(spot.isActive());
		verify(parkingSpotRepository).findAllInNaturalCodeOrder();
		verifyNoMoreInteractions(parkingSpotRepository);
	}
}
