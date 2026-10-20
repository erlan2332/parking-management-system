package com.parking.backend.parking;

import com.parking.backend.common.exception.InvalidParkingIntervalException;
import com.parking.backend.common.exception.ParkingSpotNotFoundException;
import com.parking.backend.reservation.ReservationRepository;
import com.parking.backend.reservation.ReservationStatus;
import com.parking.backend.visit.ParkingVisitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
class ParkingSpotServiceTest {

	@Mock
	private ParkingSpotRepository parkingSpotRepository;
	@Mock
	private ReservationRepository reservationRepository;
	@Mock
	private ParkingVisitRepository parkingVisitRepository;

	private ParkingSpotService parkingSpotService;

	@BeforeEach
	void setUp() {
		Clock clock = Clock.fixed(Instant.parse("2026-12-01T08:00:00Z"), ZoneOffset.UTC);
		parkingSpotService = new ParkingSpotService(
				parkingSpotRepository,
				reservationRepository,
				parkingVisitRepository,
				clock
		);
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
		stubNoBlockingReservations();

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
		verifyNoInteractions(reservationRepository);
		verifyNoInteractions(parkingVisitRepository);
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
		verifyNoInteractions(reservationRepository);
		verifyNoInteractions(parkingVisitRepository);
	}

	@Test
	void comparesIntervalsByInstantAcrossDifferentOffsets() {
		when(parkingSpotRepository.findAllInNaturalCodeOrder()).thenReturn(List.of());
		stubNoBlockingReservations();

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

	private void stubNoBlockingReservations() {
		when(reservationRepository.findBlockingForInterval(
				any(),
				any(),
				any(),
				eq(ReservationStatus.HELD),
				anyCollection()
		)).thenReturn(List.of());
		when(parkingVisitRepository.findAllByExitedAtIsNull()).thenReturn(List.of());
	}
}
