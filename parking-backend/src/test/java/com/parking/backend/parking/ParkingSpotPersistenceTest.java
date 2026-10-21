package com.parking.backend.parking;

import com.parking.backend.parking.entity.ParkingSpotEntity;
import com.parking.backend.parking.model.ParkingSpotAvailability;
import com.parking.backend.parking.repository.ParkingSpotRepository;
import com.parking.backend.parking.service.ParkingSpotService;

import jakarta.persistence.EntityManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ParkingSpotPersistenceTest {

	@Autowired
	private ParkingSpotRepository parkingSpotRepository;

	@Autowired
	private ParkingSpotService parkingSpotService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private Flyway flyway;

	@Test
	void flywayCreatesParkingSpotTable() {
		Boolean tableExists = jdbcTemplate.queryForObject(
				"SELECT to_regclass('public.parking_spots') IS NOT NULL",
				Boolean.class
		);

		assertTrue(Boolean.TRUE.equals(tableExists));
	}

	@Test
	void containsTwentyUniqueSeededParkingSpots() {
		Integer total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM parking_spots", Integer.class);
		Integer uniqueCodes = jdbcTemplate.queryForObject(
				"SELECT COUNT(DISTINCT code) FROM parking_spots",
				Integer.class
		);

		assertEquals(20, total);
		assertEquals(total, uniqueCodes);
	}

	@Test
	void databaseRejectsDuplicateParkingSpotCode() {
		assertThrows(
				DataIntegrityViolationException.class,
				() -> jdbcTemplate.update("INSERT INTO parking_spots (code, active) VALUES ('A1', TRUE)")
		);
	}

	@Test
	void repeatedFlywayMigrationDoesNotDuplicateSeedData() {
		flyway.migrate();

		Integer total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM parking_spots", Integer.class);
		assertEquals(20, total);
	}

	@Test
	void repositoryReturnsSpotsInNaturalCodeOrder() {
		List<ParkingSpotEntity> spots = parkingSpotRepository.findAllInNaturalCodeOrder();

		assertEquals(20, spots.size());
		assertEquals(List.of("A1", "A2", "A3", "A4", "A5", "A6", "A7", "A8", "A9", "A10"),
				spots.subList(0, 10).stream().map(ParkingSpotEntity::getCode).toList());
		assertEquals("B1", spots.get(10).getCode());
		assertEquals("B10", spots.get(19).getCode());
	}

	@Test
	void repositoryFindsSpotByIdAndReturnsEmptyForUnknownId() {
		Long firstId = jdbcTemplate.queryForObject(
				"SELECT id FROM parking_spots WHERE code = 'A1'",
				Long.class
		);

		ParkingSpotEntity spot = parkingSpotRepository.findById(firstId).orElseThrow();

		assertEquals("A1", spot.getCode());
		assertTrue(parkingSpotRepository.findById(Long.MAX_VALUE).isEmpty());
	}

	@Test
	void inactiveSpotIsUnavailableForRequestedInterval() {
		jdbcTemplate.update("UPDATE parking_spots SET active = FALSE WHERE code = 'A1'");
		entityManager.clear();

		List<ParkingSpotAvailability> availability = parkingSpotService.getAvailability(
				OffsetDateTime.parse("2026-12-01T14:00:00+06:00"),
				OffsetDateTime.parse("2026-12-01T16:00:00+06:00")
		);

		ParkingSpotAvailability a1 = availability.stream()
				.filter(spot -> spot.code().equals("A1"))
				.findFirst()
				.orElseThrow();
		assertFalse(a1.available());
	}

	@Test
	void hibernateMappingIsAvailableAfterSchemaValidation() {
		assertNotNull(entityManager.getMetamodel().entity(ParkingSpotEntity.class));
	}
}
