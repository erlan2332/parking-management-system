package com.parking.backend.api;

import com.parking.backend.invoice.InvoiceEntity;
import com.parking.backend.invoice.InvoiceService;
import com.parking.backend.parking.ParkingSpotEntity;
import com.parking.backend.parking.ParkingSpotRepository;
import com.parking.backend.security.JwtService;
import com.parking.backend.user.UserEntity;
import com.parking.backend.user.UserRepository;
import com.parking.backend.vehicle.VehicleEntity;
import com.parking.backend.vehicle.VehicleRepository;
import com.parking.backend.visit.ParkingVisitEntity;
import com.parking.backend.visit.ParkingVisitRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class InvoiceApiIntegrationTest {

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private InvoiceService invoiceService;
	@Autowired
	private ParkingVisitRepository parkingVisitRepository;
	@Autowired
	private ParkingSpotRepository parkingSpotRepository;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private VehicleRepository vehicleRepository;
	@Autowired
	private JwtService jwtService;

	@Test
	void returnsOwnedInvoiceListDetailsVisitInvoiceAndHistoricalCost() throws Exception {
		Driver owner = createDriver("invoice-api-owner@example.com", "01KG921AAA");
		InvoiceEntity older = issueInvoice(owner.vehicle(), "2026-12-01T13:30:00Z", "2026-12-01T14:30:00Z");
		InvoiceEntity newer = issueInvoice(owner.vehicle(), "2026-12-02T02:00:00Z", "2026-12-02T03:00:00Z");
		String token = bearerToken(owner.user());

		mockMvc.perform(get("/api/invoices/my").header("Authorization", token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].id").value(newer.getId()))
				.andExpect(jsonPath("$[1].id").value(older.getId()))
				.andExpect(jsonPath("$[1].invoiceNumber").value(older.getInvoiceNumber()))
				.andExpect(jsonPath("$[1].status").value("ISSUED"))
				.andExpect(jsonPath("$[1].totalAmount").value(90.0));

		mockMvc.perform(get("/api/invoices/{id}", older.getId()).header("Authorization", token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.visitId").value(older.getVisit().getId()))
				.andExpect(jsonPath("$.pricingZoneId").value("Asia/Bishkek"))
				.andExpect(jsonPath("$.dayDurationSeconds").value(1_800))
				.andExpect(jsonPath("$.nightDurationSeconds").value(1_800))
				.andExpect(jsonPath("$.totalAmount").value(90.0));

		mockMvc.perform(get("/api/visits/{id}/invoice", older.getVisit().getId())
					.header("Authorization", token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(older.getId()))
				.andExpect(jsonPath("$.invoiceNumber").value(older.getInvoiceNumber()));

		mockMvc.perform(get("/api/visits/{id}/cost", older.getVisit().getId())
					.header("Authorization", token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.dayRatePerMinute").value(2.0))
				.andExpect(jsonPath("$.nightRatePerMinute").value(1.0))
				.andExpect(jsonPath("$.totalCost").value(90.0));
	}

	@Test
	void invoiceEndpointsRequireJwtAndHideAnotherUsersData() throws Exception {
		Driver owner = createDriver("invoice-api-private@example.com", "01KG922AAA");
		Driver stranger = createDriver("invoice-api-stranger@example.com", "01KG923AAA");
		InvoiceEntity invoice = issueInvoice(
				owner.vehicle(),
				"2026-12-01T13:30:00Z",
				"2026-12-01T14:30:00Z"
		);

		mockMvc.perform(get("/api/invoices/my"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
		mockMvc.perform(get("/api/invoices/{id}", invoice.getId())
					.header("Authorization", bearerToken(stranger.user())))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("INVOICE_NOT_FOUND"));
		mockMvc.perform(get("/api/visits/{id}/invoice", invoice.getVisit().getId())
					.header("Authorization", bearerToken(stranger.user())))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("PARKING_VISIT_NOT_FOUND"));
	}

	@Test
	void visitInvoiceEndpointDoesNotCreateInvoicesForActiveOrLegacyVisits() throws Exception {
		Driver owner = createDriver("invoice-api-missing@example.com", "01KG924AAA");
		ParkingSpotEntity spot = parkingSpotRepository.findAllInNaturalCodeOrder().getFirst();
		ParkingVisitEntity active = parkingVisitRepository.saveAndFlush(ParkingVisitEntity.walkIn(
				owner.vehicle(),
				spot,
				Instant.parse("2026-12-01T13:30:00Z")
		));
		ParkingVisitEntity legacy = ParkingVisitEntity.walkIn(
				owner.vehicle(),
				parkingSpotRepository.findAllInNaturalCodeOrder().get(1),
				Instant.parse("2026-11-01T13:30:00Z")
		);
		legacy.complete(Instant.parse("2026-11-01T14:30:00Z"));
		parkingVisitRepository.saveAndFlush(legacy);
		String token = bearerToken(owner.user());

		mockMvc.perform(get("/api/visits/{id}/invoice", active.getId()).header("Authorization", token))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("PARKING_VISIT_NOT_COMPLETED"));
		mockMvc.perform(get("/api/visits/{id}/invoice", legacy.getId()).header("Authorization", token))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("INVOICE_NOT_ISSUED"));
		mockMvc.perform(get("/api/invoices/my").header("Authorization", token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));
	}

	private InvoiceEntity issueInvoice(VehicleEntity vehicle, String enteredAt, String exitedAt) {
		ParkingVisitEntity visit = ParkingVisitEntity.walkIn(
				vehicle,
				parkingSpotRepository.findAllInNaturalCodeOrder().getFirst(),
				Instant.parse(enteredAt)
		);
		visit.complete(Instant.parse(exitedAt));
		parkingVisitRepository.saveAndFlush(visit);
		return invoiceService.issueForVisit(visit, visit.getExitedAt());
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
