package com.parking.backend.vehicle;

import com.parking.backend.user.UserEntity;
import com.parking.backend.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserVehiclePersistenceTest {

	private static final String PASSWORD_HASH = "prepared-password-hash";

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private VehicleRepository vehicleRepository;

	@Autowired
	private VehicleService vehicleService;

	@Autowired
	private PlateNormalizer plateNormalizer;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void createsUserWithDatabaseGeneratedValues() {
		UserEntity user = saveUser("driver@example.com");

		assertNotNull(user.getId());
		assertNotNull(user.getCreatedAt());
		assertEquals("driver@example.com", user.getEmail());
	}

	@Test
	void savesVehicleWithExistingOwner() {
		UserEntity user = saveUser("owner@example.com");
		VehicleEntity vehicle = vehicleRepository.saveAndFlush(new VehicleEntity(
				user,
				"01-KG-123-ABC",
				plateNormalizer.normalize("01-KG-123-ABC")
		));

		entityManager.clear();
		VehicleEntity savedVehicle = vehicleRepository.findById(vehicle.getId()).orElseThrow();

		assertEquals(user.getId(), savedVehicle.getUser().getId());
		assertEquals("01KG123ABC", savedVehicle.getNormalizedPlate());
		assertNotNull(savedVehicle.getCreatedAt());
	}

	@Test
	void returnsOnlyVehiclesOwnedByRequestedUser() {
		UserEntity firstUser = saveUser("first@example.com");
		UserEntity secondUser = saveUser("second@example.com");
		VehicleEntity firstVehicle = saveVehicle(firstUser, "A111AA");
		VehicleEntity secondVehicle = saveVehicle(firstUser, "B222BB");
		saveVehicle(secondUser, "C333CC");

		List<VehicleEntity> vehicles = vehicleService.getVehiclesByUserId(firstUser.getId());

		assertEquals(List.of(firstVehicle.getId(), secondVehicle.getId()),
				vehicles.stream().map(VehicleEntity::getId).toList());
	}

	@Test
	void enforcesCaseInsensitiveEmailUniqueness() {
		saveUser("Driver@Example.com");

		assertThrows(
				DataIntegrityViolationException.class,
				() -> saveUser("driver@example.com")
		);
	}

	@Test
	void enforcesGlobalNormalizedPlateUniqueness() {
		UserEntity firstUser = saveUser("plate-first@example.com");
		UserEntity secondUser = saveUser("plate-second@example.com");
		saveVehicle(firstUser, "01 KG 123 ABC");

		assertThrows(
				DataIntegrityViolationException.class,
				() -> saveVehicle(secondUser, "01-KG-123-ABC")
		);
	}

	@Test
	void rejectsVehicleWithMissingOwner() {
		UserEntity missingUser = entityManager.getReference(UserEntity.class, Long.MAX_VALUE);
		VehicleEntity vehicle = new VehicleEntity(missingUser, "A123BC", "A123BC");

		assertThrows(
				DataIntegrityViolationException.class,
				() -> vehicleRepository.saveAndFlush(vehicle)
		);
	}

	@Test
	void findsVehicleUsingTheSamePlateNormalizationAsCreation() {
		UserEntity user = saveUser("search@example.com");
		VehicleEntity createdVehicle = vehicleService.addVehicle(user.getId(), "01-KG-123-ABC");

		Optional<VehicleEntity> foundVehicle = vehicleService.findByPlate(" 01 kg 123 abc ");

		assertTrue(foundVehicle.isPresent());
		assertEquals(createdVehicle.getId(), foundVehicle.orElseThrow().getId());
		assertTrue(vehicleService.plateExists("01 kg 123 abc"));
		assertTrue(vehicleService.belongsToUser(createdVehicle.getId(), user.getId()));
	}

	@Test
	void preservesParkingSpotSchemaAndSeedData() {
		Integer parkingSpotCount = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM parking_spots",
				Integer.class
		);

		assertEquals(20, parkingSpotCount);
	}

	private UserEntity saveUser(String email) {
		return userRepository.saveAndFlush(new UserEntity(email, PASSWORD_HASH));
	}

	private VehicleEntity saveVehicle(UserEntity user, String plateNumber) {
		String normalizedPlate = plateNormalizer.normalize(plateNumber);
		return vehicleRepository.saveAndFlush(new VehicleEntity(user, plateNumber, normalizedPlate));
	}
}
