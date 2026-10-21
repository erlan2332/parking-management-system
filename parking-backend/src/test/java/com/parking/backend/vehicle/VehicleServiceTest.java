package com.parking.backend.vehicle;

import com.parking.backend.vehicle.entity.VehicleEntity;
import com.parking.backend.vehicle.repository.VehicleRepository;
import com.parking.backend.vehicle.service.PlateNormalizer;
import com.parking.backend.vehicle.service.VehicleService;

import com.parking.backend.common.exception.DuplicateVehiclePlateException;
import com.parking.backend.common.exception.UserNotFoundException;
import com.parking.backend.user.entity.UserEntity;
import com.parking.backend.user.repository.UserRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VehicleServiceTest {

	private static final long USER_ID = 10L;

	@Mock
	private UserRepository userRepository;

	@Mock
	private VehicleRepository vehicleRepository;

	private VehicleService vehicleService;

	@BeforeEach
	void setUp() {
		vehicleService = new VehicleService(userRepository, vehicleRepository, new PlateNormalizer());
	}

	@Test
	void addsNormalizedVehicleToExistingUser() {
		UserEntity user = user();
		when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
		when(vehicleRepository.existsByNormalizedPlate("01KG123ABC")).thenReturn(false);
		when(vehicleRepository.saveAndFlush(any(VehicleEntity.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		VehicleEntity vehicle = vehicleService.addVehicle(USER_ID, " 01-kg-123-abc ");

		assertSame(user, vehicle.getUser());
		assertEquals("01-kg-123-abc", vehicle.getPlateNumber());
		assertEquals("01KG123ABC", vehicle.getNormalizedPlate());
	}

	@Test
	void rejectsVehicleForMissingUser() {
		when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

		assertThrows(
				UserNotFoundException.class,
				() -> vehicleService.addVehicle(USER_ID, "01KG123ABC")
		);
	}

	@Test
	void rejectsAlreadyRegisteredPlate() {
		when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
		when(vehicleRepository.existsByNormalizedPlate("01KG123ABC")).thenReturn(true);

		assertThrows(
				DuplicateVehiclePlateException.class,
				() -> vehicleService.addVehicle(USER_ID, "01 kg 123 abc")
		);
	}

	@Test
	void translatesConcurrentDatabaseConflict() {
		when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
		when(vehicleRepository.existsByNormalizedPlate("01KG123ABC")).thenReturn(false);
		ConstraintViolationException uniquePlateViolation = new ConstraintViolationException(
				"duplicate normalized plate",
				new SQLException("unique constraint violation"),
				"INSERT INTO vehicles ...",
				"uk_vehicles_normalized_plate"
		);
		when(vehicleRepository.saveAndFlush(any(VehicleEntity.class)))
				.thenThrow(new DataIntegrityViolationException("unique constraint violation", uniquePlateViolation));

		assertThrows(
				DuplicateVehiclePlateException.class,
				() -> vehicleService.addVehicle(USER_ID, "01KG123ABC")
		);
	}

	@Test
	void doesNotMaskUnrelatedDatabaseIntegrityViolation() {
		when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
		when(vehicleRepository.existsByNormalizedPlate("01KG123ABC")).thenReturn(false);
		DataIntegrityViolationException databaseException =
				new DataIntegrityViolationException("foreign key constraint violation");
		when(vehicleRepository.saveAndFlush(any(VehicleEntity.class))).thenThrow(databaseException);

		DataIntegrityViolationException thrownException = assertThrows(
				DataIntegrityViolationException.class,
				() -> vehicleService.addVehicle(USER_ID, "01KG123ABC")
		);

		assertSame(databaseException, thrownException);
	}

	@Test
	void returnsVehicleOwnedByRequestedUser() {
		VehicleEntity vehicle = new VehicleEntity(user(), "A100AA", "A100AA");
		when(vehicleRepository.findByIdAndUser_Id(100L, USER_ID)).thenReturn(Optional.of(vehicle));

		assertSame(vehicle, vehicleService.getVehicleByIdForUser(100L, USER_ID));
		verify(vehicleRepository).findByIdAndUser_Id(100L, USER_ID);
	}

	private static UserEntity user() {
		return new UserEntity("driver@example.com", "prepared-password-hash");
	}
}
