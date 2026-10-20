package com.parking.backend.api;

import com.jayway.jsonpath.JsonPath;
import com.parking.backend.security.JwtService;
import com.parking.backend.user.UserEntity;
import com.parking.backend.user.UserRepository;
import com.parking.backend.vehicle.VehicleEntity;
import com.parking.backend.vehicle.VehicleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class BarrierApiIntegrationTest {

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private VehicleRepository vehicleRepository;
	@Autowired
	private JwtService jwtService;

	@Test
	void supportsSecuredWalkInExitAndVisitHistoryLifecycle() throws Exception {
		Driver driver = createDriver("barrier-api@example.com", "01KG701AAA");
		String token = bearerToken(driver.user());

		MvcResult entered = mockMvc.perform(post("/api/barrier/entry")
					.header("Authorization", token)
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"plateNumber\":\"01 kg-701 aaa\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.visitId").isNumber())
				.andExpect(jsonPath("$.plateNumber").value("01KG701AAA"))
				.andExpect(jsonPath("$.spotCode").value("A1"))
				.andExpect(jsonPath("$.entryType").value("WALK_IN"))
				.andExpect(jsonPath("$.status").value("ACTIVE"))
				.andReturn();
		long visitId = ((Number) JsonPath.read(entered.getResponse().getContentAsString(), "$.visitId")).longValue();

		mockMvc.perform(get("/api/parking/spots").header("Authorization", token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].code").value("A1"))
				.andExpect(jsonPath("$[0].status").value("OCCUPIED"));
		Instant futureStart = Instant.now().plus(30, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
		mockMvc.perform(get("/api/parking/availability")
					.header("Authorization", token)
					.queryParam("startAt", futureStart.toString())
					.queryParam("endAt", futureStart.plus(1, ChronoUnit.HOURS).toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.spots[0].status").value("OCCUPIED"))
				.andExpect(jsonPath("$.spots[0].available").value(false));

		mockMvc.perform(get("/api/visits/my").header("Authorization", token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].id").value(visitId))
				.andExpect(jsonPath("$[0].exitedAt").doesNotExist())
				.andExpect(jsonPath("$[0].durationSeconds").doesNotExist())
				.andExpect(jsonPath("$[0].status").value("ACTIVE"));

		MvcResult exited = mockMvc.perform(post("/api/barrier/exit")
					.header("Authorization", token)
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"plateNumber\":\"01KG701AAA\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.visitId").value(visitId))
				.andExpect(jsonPath("$.status").value("COMPLETED"))
				.andExpect(jsonPath("$.exitedAt").isString())
				.andExpect(jsonPath("$.durationSeconds").isNumber())
				.andReturn();
		Number duration = JsonPath.read(exited.getResponse().getContentAsString(), "$.durationSeconds");
		assertTrue(duration.longValue() >= 0);

		mockMvc.perform(post("/api/barrier/exit")
					.header("Authorization", token)
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"plateNumber\":\"01KG701AAA\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("NO_ACTIVE_VISIT"));
		mockMvc.perform(get("/api/parking/spots").header("Authorization", token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].status").value("FREE"));
	}

	@Test
	void enforcesJwtOwnershipAndRequestValidation() throws Exception {
		Driver owner = createDriver("barrier-owner@example.com", "01KG702AAA");
		Driver stranger = createDriver("barrier-stranger@example.com", "01KG703AAA");

		mockMvc.perform(post("/api/barrier/entry")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"plateNumber\":\"01KG702AAA\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
		mockMvc.perform(post("/api/barrier/entry")
					.header("Authorization", bearerToken(stranger.user()))
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"plateNumber\":\"01KG702AAA\"}"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("VEHICLE_NOT_FOUND"));
		mockMvc.perform(post("/api/barrier/entry")
					.header("Authorization", bearerToken(owner.user()))
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"plateNumber\":\" \"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		mockMvc.perform(get("/api/visits/my"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	private Driver createDriver(String email, String plate) {
		UserEntity user = userRepository.saveAndFlush(new UserEntity(email, "password-hash"));
		VehicleEntity vehicle = vehicleRepository.saveAndFlush(new VehicleEntity(user, plate, plate));
		return new Driver(user, vehicle);
	}

	private String bearerToken(UserEntity user) {
		return "Bearer " + jwtService.issueToken(user.getId()).value();
	}

	private record Driver(UserEntity user, VehicleEntity vehicle) {
	}
}
