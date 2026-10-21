package com.parking.backend.api;

import com.parking.backend.parking.entity.ParkingSpotEntity;
import com.parking.backend.parking.repository.ParkingSpotRepository;
import com.parking.backend.security.service.JwtService;
import com.parking.backend.user.entity.UserEntity;
import com.parking.backend.user.repository.UserRepository;
import com.parking.backend.vehicle.entity.VehicleEntity;
import com.parking.backend.vehicle.repository.VehicleRepository;
import com.parking.backend.visit.entity.ParkingVisitEntity;
import com.parking.backend.visit.repository.ParkingVisitRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ParkingVisitCostApiIntegrationTest {

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private VehicleRepository vehicleRepository;
	@Autowired
	private ParkingSpotRepository parkingSpotRepository;
	@Autowired
	private ParkingVisitRepository parkingVisitRepository;
	@Autowired
	private JwtService jwtService;

	@Test
	@Tag("smoke")
	void calculatesCompletedVisitCostAcrossDayAndNightTariffs() throws Exception {
		Driver owner = createDriver("pricing-owner@example.com", "01KG801AAA");
		ParkingVisitEntity visit = completedVisit(
				owner.vehicle(),
				spot(),
				"2026-12-01T19:30:00+06:00",
				"2026-12-01T20:30:00+06:00"
		);

		mockMvc.perform(get("/api/visits/{id}/cost", visit.getId())
					.header("Authorization", bearerToken(owner.user())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.visitId").value(visit.getId()))
				.andExpect(jsonPath("$.currency").value("KGS"))
				.andExpect(jsonPath("$.enteredAt").isString())
				.andExpect(jsonPath("$.exitedAt").isString())
				.andExpect(jsonPath("$.totalDurationSeconds").value(3_600))
				.andExpect(jsonPath("$.dayDurationSeconds").value(1_800))
				.andExpect(jsonPath("$.nightDurationSeconds").value(1_800))
				.andExpect(jsonPath("$.dayRatePerMinute").value(2.0))
				.andExpect(jsonPath("$.nightRatePerMinute").value(1.0))
				.andExpect(jsonPath("$.dayCost").value(60.0))
				.andExpect(jsonPath("$.nightCost").value(30.0))
				.andExpect(jsonPath("$.totalCost").value(90.0));
	}

	@Test
	void hidesAnotherUsersVisitAndRequiresAuthentication() throws Exception {
		Driver owner = createDriver("pricing-private-owner@example.com", "01KG802AAA");
		Driver stranger = createDriver("pricing-private-stranger@example.com", "01KG803AAA");
		ParkingVisitEntity visit = completedVisit(
				owner.vehicle(),
				spot(),
				"2026-12-01T14:00:00+06:00",
				"2026-12-01T15:00:00+06:00"
		);

		mockMvc.perform(get("/api/visits/{id}/cost", visit.getId()))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
		mockMvc.perform(get("/api/visits/{id}/cost", visit.getId())
					.header("Authorization", bearerToken(stranger.user())))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("PARKING_VISIT_NOT_FOUND"));
	}

	@Test
	void rejectsCostCalculationForActiveVisit() throws Exception {
		Driver owner = createDriver("pricing-active@example.com", "01KG804AAA");
		ParkingVisitEntity visit = parkingVisitRepository.saveAndFlush(ParkingVisitEntity.walkIn(
				owner.vehicle(),
				spot(),
				OffsetDateTime.parse("2026-12-01T19:30:00+06:00").toInstant()
		));

		mockMvc.perform(get("/api/visits/{id}/cost", visit.getId())
					.header("Authorization", bearerToken(owner.user())))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("PARKING_VISIT_NOT_COMPLETED"));
	}

	private Driver createDriver(String email, String plate) {
		UserEntity user = userRepository.saveAndFlush(new UserEntity(email, "password-hash"));
		VehicleEntity vehicle = vehicleRepository.saveAndFlush(new VehicleEntity(user, plate, plate));
		return new Driver(user, vehicle);
	}

	private ParkingVisitEntity completedVisit(
			VehicleEntity vehicle,
			ParkingSpotEntity spot,
			String enteredAt,
			String exitedAt
	) {
		ParkingVisitEntity visit = ParkingVisitEntity.walkIn(
				vehicle,
				spot,
				OffsetDateTime.parse(enteredAt).toInstant()
		);
		visit.complete(OffsetDateTime.parse(exitedAt).toInstant());
		return parkingVisitRepository.saveAndFlush(visit);
	}

	private ParkingSpotEntity spot() {
		return parkingSpotRepository.findAllInNaturalCodeOrder().getFirst();
	}

	private String bearerToken(UserEntity user) {
		return "Bearer " + jwtService.issueToken(user.getId()).value();
	}

	private record Driver(UserEntity user, VehicleEntity vehicle) {
	}
}
