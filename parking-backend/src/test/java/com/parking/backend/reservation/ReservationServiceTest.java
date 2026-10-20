package com.parking.backend.reservation;

import com.parking.backend.common.exception.InvalidReservationRequestException;
import com.parking.backend.common.exception.ReservationConflictException;
import com.parking.backend.parking.ParkingSpotEntity;
import com.parking.backend.parking.ParkingSpotRepository;
import com.parking.backend.user.UserEntity;
import com.parking.backend.vehicle.VehicleEntity;
import com.parking.backend.vehicle.VehicleRepository;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

	private static final Instant NOW = Instant.parse("2026-12-01T08:00:00Z");
	private static final Long USER_ID = 11L;
	private static final Long VEHICLE_ID = 22L;
	private static final Long SPOT_ID = 33L;

	@Mock
	private ReservationRepository reservationRepository;
	@Mock
	private ParkingSpotRepository parkingSpotRepository;
	@Mock
	private VehicleRepository vehicleRepository;
	@Mock
	private ParkingVisitRepository parkingVisitRepository;

	private ReservationService reservationService;
	private UserEntity user;
	private VehicleEntity vehicle;
	private ParkingSpotEntity spot;

	@BeforeEach
	void setUp() {
		reservationService = serviceAt(NOW);
		user = new UserEntity("driver@example.com", "password-hash");
		vehicle = new VehicleEntity(user, "01 KG 123 ABC", "01KG123ABC");
		spot = new ParkingSpotEntity("A1", true);
	}

	@Test
	void createsThreeMinuteHoldForLaterReservation() {
		stubAvailableResources();

		ReservationEntity hold = reservationService.createHold(
				USER_ID,
				VEHICLE_ID,
				SPOT_ID,
				at(NOW.plusSeconds(600)),
				at(NOW.plusSeconds(1200))
		);

		assertEquals(ReservationStatus.HELD, hold.getStatus());
		assertEquals(NOW.plusSeconds(180), hold.getHoldExpiresAt());
		assertEquals(NOW, hold.getCreatedAt());
	}

	@Test
	void capsHoldExpirationAtReservationStart() {
		stubAvailableResources();
		Instant start = NOW.plusSeconds(60);

		ReservationEntity hold = reservationService.createHold(
				USER_ID,
				VEHICLE_ID,
				SPOT_ID,
				at(start),
				at(start.plusSeconds(600))
		);

		assertEquals(start, hold.getHoldExpiresAt());
	}

	@Test
	void rejectsNonFutureAndReversedIntervalsBeforeDatabaseAccess() {
		assertThrows(
				InvalidReservationRequestException.class,
				() -> reservationService.createHold(USER_ID, VEHICLE_ID, SPOT_ID, at(NOW), at(NOW.plusSeconds(60)))
		);
		assertThrows(
				InvalidReservationRequestException.class,
				() -> reservationService.createHold(
						USER_ID,
						VEHICLE_ID,
						SPOT_ID,
						at(NOW.plusSeconds(120)),
						at(NOW.plusSeconds(60))
				)
		);

		verify(parkingSpotRepository, never()).findByIdForUpdate(anyLong());
		verify(vehicleRepository, never()).findOwnedByIdForUpdate(anyLong(), anyLong());
	}

	@Test
	void refusesConfirmationAtExactExpirationBoundary() {
		Instant expiresAt = NOW.plusSeconds(180);
		ReservationEntity hold = ReservationEntity.createHold(
				user,
				vehicle,
				spot,
				NOW.plusSeconds(600),
				NOW.plusSeconds(1200),
				expiresAt,
				NOW
		);
		when(reservationRepository.findOwnedByIdForUpdate(44L, USER_ID)).thenReturn(Optional.of(hold));

		ReservationConflictException exception = assertThrows(
				ReservationConflictException.class,
				() -> serviceAt(expiresAt).confirmHold(USER_ID, 44L)
		);

		assertEquals("Hold has expired", exception.getMessage());
		assertEquals(ReservationStatus.HOLD_EXPIRED, hold.getStatus());
	}

	private ReservationService serviceAt(Instant instant) {
		return new ReservationService(
				reservationRepository,
				parkingSpotRepository,
				vehicleRepository,
				parkingVisitRepository,
				Clock.fixed(instant, ZoneOffset.UTC)
		);
	}

	private void stubAvailableResources() {
		when(parkingSpotRepository.findByIdForUpdate(SPOT_ID)).thenReturn(Optional.of(spot));
		when(vehicleRepository.findOwnedByIdForUpdate(VEHICLE_ID, USER_ID)).thenReturn(Optional.of(vehicle));
		when(reservationRepository.findBlockingConflicts(
				anyLong(),
				anyLong(),
				any(),
				any(),
				any(),
				any(),
				anyCollection()
		)).thenReturn(List.of());
		when(reservationRepository.saveAndFlush(any(ReservationEntity.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));
	}

	private static OffsetDateTime at(Instant instant) {
		return instant.atOffset(ZoneOffset.UTC);
	}
}
