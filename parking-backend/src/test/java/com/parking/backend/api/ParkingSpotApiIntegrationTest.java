package com.parking.backend.api;

import com.parking.backend.parking.ParkingSpotController;
import com.parking.backend.parking.dto.ParkingSpotResponse;
import com.parking.backend.security.JwtService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ParkingSpotApiIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	@Tag("smoke")
	void returnsAllSpotsWithJwtInNaturalOrder() throws Exception {
		mockMvc.perform(get("/api/parking/spots").header("Authorization", bearerToken()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(20))
				.andExpect(jsonPath("$[0].code").value("A1"))
				.andExpect(jsonPath("$[9].code").value("A10"))
				.andExpect(jsonPath("$[10].code").value("B1"))
				.andExpect(jsonPath("$[19].code").value("B10"))
				.andExpect(jsonPath("$[0].active").value(true))
				.andExpect(jsonPath("$[0].status").value("FREE"));
	}

	@Test
	void rejectsSpotListWithoutJwt() throws Exception {
		mockMvc.perform(get("/api/parking/spots"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	@Test
	void returnsSpotByIdWithJwt() throws Exception {
		Long spotId = spotId("A1");

		mockMvc.perform(get("/api/parking/spots/{id}", spotId).header("Authorization", bearerToken()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(spotId))
				.andExpect(jsonPath("$.code").value("A1"))
				.andExpect(jsonPath("$.active").value(true))
				.andExpect(jsonPath("$.status").value("FREE"));
	}

	@Test
	void returnsNotFoundForUnknownSpotId() throws Exception {
		mockMvc.perform(get("/api/parking/spots/{id}", Long.MAX_VALUE)
						.header("Authorization", bearerToken()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("PARKING_SPOT_NOT_FOUND"));
	}

	@Test
	@Tag("smoke")
	void returnsAvailabilityForValidInterval() throws Exception {
		mockMvc.perform(validAvailabilityRequest())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.startAt").value("2026-12-01T14:00:00+06:00"))
				.andExpect(jsonPath("$.endAt").value("2026-12-01T16:00:00+06:00"))
				.andExpect(jsonPath("$.spots.length()").value(20))
				.andExpect(jsonPath("$.spots[0].id").isNumber())
				.andExpect(jsonPath("$.spots[0].code").value("A1"))
				.andExpect(jsonPath("$.spots[0].available").value(true));
	}

	@Test
	void rejectsAvailabilityWithoutJwt() throws Exception {
		mockMvc.perform(get("/api/parking/availability")
						.queryParam("startAt", "2026-12-01T14:00:00+06:00")
						.queryParam("endAt", "2026-12-01T16:00:00+06:00"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	@Test
	@Tag("smoke")
	void rejectsInvalidInterval() throws Exception {
		mockMvc.perform(get("/api/parking/availability")
						.header("Authorization", bearerToken())
						.queryParam("startAt", "2026-12-01T16:00:00+06:00")
						.queryParam("endAt", "2026-12-01T14:00:00+06:00"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_PARKING_INTERVAL"));
	}

	@Test
	void rejectsMissingStartAt() throws Exception {
		mockMvc.perform(get("/api/parking/availability")
						.header("Authorization", bearerToken())
						.queryParam("endAt", "2026-12-01T16:00:00+06:00"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	@Test
	void rejectsMissingEndAt() throws Exception {
		mockMvc.perform(get("/api/parking/availability")
						.header("Authorization", bearerToken())
						.queryParam("startAt", "2026-12-01T14:00:00+06:00"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	@Test
	void rejectsMalformedDate() throws Exception {
		mockMvc.perform(get("/api/parking/availability")
						.header("Authorization", bearerToken())
						.queryParam("startAt", "not-a-date")
						.queryParam("endAt", "2026-12-01T16:00:00+06:00"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	@Test
	void returnsFalseAvailabilityForInactiveSpot() throws Exception {
		jdbcTemplate.update("UPDATE parking_spots SET active = FALSE WHERE code = 'A1'");

		mockMvc.perform(validAvailabilityRequest())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.spots[0].code").value("A1"))
				.andExpect(jsonPath("$.spots[0].available").value(false));
	}

	@Test
	void controllerReturnsDtoInsteadOfEntity() throws NoSuchMethodException {
		assertEquals(
				ParkingSpotResponse.class,
				ParkingSpotController.class.getMethod("getSpot", Long.class).getReturnType()
		);
	}

	private MockHttpServletRequestBuilder validAvailabilityRequest() {
		return get("/api/parking/availability")
				.header("Authorization", bearerToken())
				.queryParam("startAt", "2026-12-01T14:00:00+06:00")
				.queryParam("endAt", "2026-12-01T16:00:00+06:00");
	}

	private Long spotId(String code) {
		return jdbcTemplate.queryForObject(
				"SELECT id FROM parking_spots WHERE code = ?",
				Long.class,
				code
		);
	}

	private String bearerToken() {
		return "Bearer " + jwtService.issueToken(1L).value();
	}
}
