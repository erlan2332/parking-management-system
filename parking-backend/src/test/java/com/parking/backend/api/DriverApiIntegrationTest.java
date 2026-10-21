package com.parking.backend.api;

import com.jayway.jsonpath.JsonPath;
import com.parking.backend.security.service.JwtService;
import com.parking.backend.user.entity.UserEntity;
import com.parking.backend.user.repository.UserRepository;
import com.parking.backend.vehicle.entity.VehicleEntity;
import com.parking.backend.vehicle.repository.VehicleRepository;
import com.parking.backend.vehicle.service.VehicleService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DriverApiIntegrationTest {

	private static final String PASSWORD = "SecurePassword123!";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private VehicleRepository vehicleRepository;

	@Autowired
	private VehicleService vehicleService;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private JwtEncoder jwtEncoder;

	@Autowired
	private JwtDecoder jwtDecoder;

	@Test
	@Tag("smoke")
	void registersUserWithNormalizedEmailAndPasswordHash() throws Exception {
		mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":" Driver@Example.COM ","password":"SecurePassword123!"}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.email").value("driver@example.com"))
				.andExpect(jsonPath("$.password").doesNotExist())
				.andExpect(jsonPath("$.passwordHash").doesNotExist());

		UserEntity user = userRepository.findByEmailIgnoreCase("driver@example.com").orElseThrow();
		assertNotEquals(PASSWORD, user.getPasswordHash());
		assertTrue(passwordEncoder.matches(PASSWORD, user.getPasswordHash()));
	}

	@Test
	@Tag("smoke")
	void rejectsCaseInsensitiveDuplicateEmail() throws Exception {
		createUser("driver@example.com", PASSWORD);

		mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"DRIVER@example.com","password":"SecurePassword123!"}
								"""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"{\"email\":\"not-an-email\",\"password\":\"SecurePassword123!\"}",
			"{\"email\":\"driver@example.com\",\"password\":\"\"}",
			"{\"email\":\"driver@example.com\",\"password\":\"short\"}"
	})
	void rejectsInvalidRegistrationRequests(String requestBody) throws Exception {
		mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(requestBody))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}

	@Test
	@Tag("smoke")
	void logsInAndReturnsVerifiableOneHourJwt() throws Exception {
		UserEntity user = createUser("driver@example.com", PASSWORD);

		MvcResult result = mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"DRIVER@example.com","password":"SecurePassword123!"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isString())
				.andExpect(jsonPath("$.tokenType").value("Bearer"))
				.andExpect(jsonPath("$.expiresIn").value(3600))
				.andReturn();

		String tokenValue = JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
		Jwt jwt = jwtDecoder.decode(tokenValue);
		assertEquals(user.getId().toString(), jwt.getSubject());
		assertEquals("parking-management-system", jwt.getClaimAsString("iss"));
		assertEquals(3600, jwt.getExpiresAt().getEpochSecond() - jwt.getIssuedAt().getEpochSecond());
	}

	@Test
	@Tag("smoke")
	void returnsSameUnauthorizedResponseForWrongPasswordAndMissingUser() throws Exception {
		createUser("driver@example.com", PASSWORD);

		mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"driver@example.com","password":"WrongPassword123!"}
								"""))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
				.andExpect(jsonPath("$.message").value("Invalid email or password"));

		mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"missing@example.com","password":"WrongPassword123!"}
								"""))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
				.andExpect(jsonPath("$.message").value("Invalid email or password"));
	}

	@Test
	void returnsCurrentProfileWithoutSensitiveFields() throws Exception {
		UserEntity currentUser = createUser("current@example.com", PASSWORD);
		UserEntity anotherUser = createUser("another@example.com", PASSWORD);
		String token = tokenFor(currentUser);

		mockMvc.perform(get("/api/users/me")
						.queryParam("userId", anotherUser.getId().toString())
						.header("Authorization", bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(currentUser.getId()))
				.andExpect(jsonPath("$.email").value("current@example.com"))
				.andExpect(jsonPath("$.createdAt").isString())
				.andExpect(jsonPath("$.password").doesNotExist())
				.andExpect(jsonPath("$.passwordHash").doesNotExist());
	}

	@Test
	void returnsNotFoundWhenTokenUserNoLongerExists() throws Exception {
		UserEntity user = createUser("deleted@example.com", PASSWORD);
		String token = tokenFor(user);
		userRepository.delete(user);
		userRepository.flush();

		mockMvc.perform(get("/api/users/me").header("Authorization", bearer(token)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
	}

	@Test
	@Tag("smoke")
	void rejectsProtectedEndpointsWithoutJwt() throws Exception {
		mockMvc.perform(get("/api/users/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

		mockMvc.perform(post("/api/vehicles")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"plateNumber\":\"01KG123ABC\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	@Test
	void rejectsAlteredJwt() throws Exception {
		UserEntity user = createUser("altered@example.com", PASSWORD);
		String token = tokenFor(user);
		int signatureStart = token.lastIndexOf('.') + 1;
		int changedIndex = signatureStart + 3;
		char replacement = token.charAt(changedIndex) == 'A' ? 'B' : 'A';
		String alteredToken = token.substring(0, changedIndex) + replacement + token.substring(changedIndex + 1);

		assertUnauthorized(alteredToken);
	}

	@Test
	void rejectsJwtWithWrongSignature() throws Exception {
		SecretKey otherKey = new SecretKeySpec(
				"fedcba9876543210fedcba9876543210".getBytes(StandardCharsets.UTF_8),
				"HmacSHA256"
		);
		JwtEncoder otherEncoder = NimbusJwtEncoder.withSecretKey(otherKey)
				.algorithm(MacAlgorithm.HS256)
				.build();
		String token = customToken(
				otherEncoder,
				"parking-management-system",
				Instant.now(),
				Instant.now().plusSeconds(3600),
				"1"
		);

		assertUnauthorized(token);
	}

	@Test
	void rejectsExpiredJwt() throws Exception {
		Instant now = Instant.now();
		String token = customToken(
				jwtEncoder,
				"parking-management-system",
				now.minusSeconds(600),
				now.minusSeconds(300),
				"1"
		);

		assertUnauthorized(token);
	}

	@Test
	void rejectsJwtWithWrongIssuer() throws Exception {
		Instant now = Instant.now();
		String token = customToken(
				jwtEncoder,
				"different-application",
				now,
				now.plusSeconds(3600),
				"1"
		);

		assertUnauthorized(token);
	}

	@Test
	@Tag("smoke")
	void createsNormalizedVehicleForCurrentUser() throws Exception {
		UserEntity user = createUser("vehicle-owner@example.com", PASSWORD);

		MvcResult result = mockMvc.perform(post("/api/vehicles")
						.header("Authorization", bearer(tokenFor(user)))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"plateNumber\":\" 01-kg-123-abc \"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.plateNumber").value("01-kg-123-abc"))
				.andExpect(jsonPath("$.normalizedPlate").value("01KG123ABC"))
				.andReturn();

		Number vehicleId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
		VehicleEntity vehicle = vehicleRepository.findById(vehicleId.longValue()).orElseThrow();
		assertEquals(user.getId(), vehicle.getUser().getId());
	}

	@Test
	void returnsEmptyVehicleList() throws Exception {
		UserEntity user = createUser("no-vehicles@example.com", PASSWORD);

		mockMvc.perform(get("/api/vehicles").header("Authorization", bearer(tokenFor(user))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").isArray())
				.andExpect(jsonPath("$").isEmpty());
	}

	@Test
	void returnsOnlyCurrentUsersVehiclesInStableOrder() throws Exception {
		UserEntity currentUser = createUser("list-owner@example.com", PASSWORD);
		UserEntity anotherUser = createUser("list-other@example.com", PASSWORD);
		VehicleEntity first = vehicleService.addVehicle(currentUser.getId(), "A111AA");
		VehicleEntity second = vehicleService.addVehicle(currentUser.getId(), "B222BB");
		vehicleService.addVehicle(anotherUser.getId(), "C333CC");

		mockMvc.perform(get("/api/vehicles").header("Authorization", bearer(tokenFor(currentUser))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].id").value(first.getId()))
				.andExpect(jsonPath("$[1].id").value(second.getId()));
	}

	@Test
	@Tag("smoke")
	void hidesVehicleOwnedByAnotherUser() throws Exception {
		UserEntity currentUser = createUser("get-current@example.com", PASSWORD);
		UserEntity anotherUser = createUser("get-other@example.com", PASSWORD);
		VehicleEntity foreignVehicle = vehicleService.addVehicle(anotherUser.getId(), "A111AA");

		mockMvc.perform(get("/api/vehicles/{id}", foreignVehicle.getId())
						.header("Authorization", bearer(tokenFor(currentUser))))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("VEHICLE_NOT_FOUND"));
	}

	@Test
	@Tag("smoke")
	void rejectsDuplicateVehiclePlate() throws Exception {
		UserEntity firstUser = createUser("duplicate-first@example.com", PASSWORD);
		UserEntity secondUser = createUser("duplicate-second@example.com", PASSWORD);
		vehicleService.addVehicle(firstUser.getId(), "01 KG 123 ABC");

		mockMvc.perform(post("/api/vehicles")
						.header("Authorization", bearer(tokenFor(secondUser)))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"plateNumber\":\"01-KG-123-ABC\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("VEHICLE_ALREADY_EXISTS"));
	}

	@Test
	void rejectsInvalidVehiclePlate() throws Exception {
		UserEntity user = createUser("invalid-plate@example.com", PASSWORD);

		mockMvc.perform(post("/api/vehicles")
						.header("Authorization", bearer(tokenFor(user)))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"plateNumber\":\"01/KG@123\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_PLATE_NUMBER"));
	}

	@Test
	void ignoresAttemptToAssignVehicleToAnotherUser() throws Exception {
		UserEntity currentUser = createUser("assignment-current@example.com", PASSWORD);
		UserEntity anotherUser = createUser("assignment-other@example.com", PASSWORD);

		MvcResult result = mockMvc.perform(post("/api/vehicles")
						.header("Authorization", bearer(tokenFor(currentUser)))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"plateNumber":"A123BC","userId":%d}
								""".formatted(anotherUser.getId())))
				.andExpect(status().isCreated())
				.andReturn();

		Number vehicleId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
		VehicleEntity vehicle = vehicleRepository.findById(vehicleId.longValue()).orElseThrow();
		assertEquals(currentUser.getId(), vehicle.getUser().getId());
		assertFalse(vehicle.getUser().getId().equals(anotherUser.getId()));
	}

	private UserEntity createUser(String email, String rawPassword) {
		return userRepository.saveAndFlush(new UserEntity(email, passwordEncoder.encode(rawPassword)));
	}

	private String tokenFor(UserEntity user) {
		return jwtService.issueToken(user.getId()).value();
	}

	private void assertUnauthorized(String token) throws Exception {
		mockMvc.perform(get("/api/users/me").header("Authorization", bearer(token)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	private static String customToken(
			JwtEncoder encoder,
			String issuer,
			Instant issuedAt,
			Instant expiresAt,
			String subject
	) {
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer(issuer)
				.issuedAt(issuedAt)
				.expiresAt(expiresAt)
				.subject(subject)
				.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
		return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
	}

	private static String bearer(String token) {
		return "Bearer " + token;
	}
}
