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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ReservationApiIntegrationTest {

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private VehicleRepository vehicleRepository;
	@Autowired
	private JwtService jwtService;
	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void supportsCompleteHoldConfirmCancelHttpLifecycle() throws Exception {
		Driver driver = createDriver("api-lifecycle@example.com", "01KG100AAA");
		Long spotId = spotId("A1");
		Instant start = Instant.now().plus(Duration.ofDays(30)).truncatedTo(ChronoUnit.SECONDS);
		Instant end = start.plus(Duration.ofHours(2));
		String token = bearerToken(driver.user());

		MvcResult created = mockMvc.perform(post("/api/reservations/holds")
					.header("Authorization", token)
					.contentType(MediaType.APPLICATION_JSON)
					.content(holdRequest(driver.vehicle().getId(), spotId, start, end)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.vehicleId").value(driver.vehicle().getId()))
				.andExpect(jsonPath("$.spotId").value(spotId))
				.andExpect(jsonPath("$.spotCode").value("A1"))
				.andExpect(jsonPath("$.status").value("HELD"))
				.andExpect(jsonPath("$.holdExpiresAt").isString())
				.andReturn();
		long holdId = ((Number) JsonPath.read(created.getResponse().getContentAsString(), "$.id")).longValue();

		assertAvailability(token, start, end, "HELD", false);
		mockMvc.perform(get("/api/reservations/holds/my").header("Authorization", token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].id").value(holdId));

		mockMvc.perform(post("/api/reservations/holds/{id}/confirm", holdId).header("Authorization", token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ACTIVE"))
				.andExpect(jsonPath("$.confirmedAt").isString());
		mockMvc.perform(post("/api/reservations/holds/{id}/confirm", holdId).header("Authorization", token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ACTIVE"));
		assertAvailability(token, start, end, "RESERVED", false);
		mockMvc.perform(get("/api/reservations/my").header("Authorization", token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].id").value(holdId))
				.andExpect(jsonPath("$[0].status").value("ACTIVE"));

		mockMvc.perform(post("/api/reservations/{id}/cancel", holdId).header("Authorization", token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CANCELLED"))
				.andExpect(jsonPath("$.cancelledAt").isString());
		mockMvc.perform(post("/api/reservations/{id}/cancel", holdId).header("Authorization", token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CANCELLED"));
		assertAvailability(token, start, end, "FREE", true);
	}

	@Test
	void enforcesAuthenticationOwnershipValidationAndConflictResponses() throws Exception {
		Driver owner = createDriver("api-owner@example.com", "01KG101AAA");
		Driver stranger = createDriver("api-stranger@example.com", "01KG102AAA");
		Long spotId = spotId("A1");
		Instant start = Instant.now().plus(Duration.ofDays(31)).truncatedTo(ChronoUnit.SECONDS);
		Instant end = start.plus(Duration.ofHours(2));

		mockMvc.perform(post("/api/reservations/holds")
					.contentType(MediaType.APPLICATION_JSON)
					.content(holdRequest(owner.vehicle().getId(), spotId, start, end)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

		MvcResult created = mockMvc.perform(post("/api/reservations/holds")
					.header("Authorization", bearerToken(owner.user()))
					.contentType(MediaType.APPLICATION_JSON)
					.content(holdRequest(owner.vehicle().getId(), spotId, start, end)))
				.andExpect(status().isCreated())
				.andReturn();
		long holdId = ((Number) JsonPath.read(created.getResponse().getContentAsString(), "$.id")).longValue();

		mockMvc.perform(post("/api/reservations/holds/{id}/confirm", holdId)
					.header("Authorization", bearerToken(stranger.user())))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("RESERVATION_NOT_FOUND"));

		mockMvc.perform(post("/api/reservations/holds")
					.header("Authorization", bearerToken(stranger.user()))
					.contentType(MediaType.APPLICATION_JSON)
					.content(holdRequest(stranger.vehicle().getId(), spotId, start.plusSeconds(1), end)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("RESERVATION_CONFLICT"));

		mockMvc.perform(post("/api/reservations/holds")
					.header("Authorization", bearerToken(owner.user()))
					.contentType(MediaType.APPLICATION_JSON)
					.content(holdRequest(owner.vehicle().getId(), spotId, end, start)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_RESERVATION_REQUEST"));
	}

	private void assertAvailability(
			String token,
			Instant start,
			Instant end,
			String expectedStatus,
			boolean expectedAvailable
	) throws Exception {
		mockMvc.perform(get("/api/parking/availability")
					.header("Authorization", token)
					.queryParam("startAt", start.toString())
					.queryParam("endAt", end.toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.spots[0].code").value("A1"))
				.andExpect(jsonPath("$.spots[0].status").value(expectedStatus))
				.andExpect(jsonPath("$.spots[0].available").value(expectedAvailable));
	}

	private Driver createDriver(String email, String plate) {
		UserEntity user = userRepository.saveAndFlush(new UserEntity(email, "password-hash"));
		VehicleEntity vehicle = vehicleRepository.saveAndFlush(new VehicleEntity(user, plate, plate));
		return new Driver(user, vehicle);
	}

	private Long spotId(String code) {
		return jdbcTemplate.queryForObject(
				"SELECT id FROM parking_spots WHERE code = ?",
				Long.class,
				code
		);
	}

	private String bearerToken(UserEntity user) {
		return "Bearer " + jwtService.issueToken(user.getId()).value();
	}

	private static String holdRequest(Long vehicleId, Long spotId, Instant start, Instant end) {
		return """
				{
				  "vehicleId": %d,
				  "spotId": %d,
				  "startAt": "%s",
				  "endAt": "%s"
				}
				""".formatted(vehicleId, spotId, start, end);
	}

	private record Driver(UserEntity user, VehicleEntity vehicle) {
	}
}
