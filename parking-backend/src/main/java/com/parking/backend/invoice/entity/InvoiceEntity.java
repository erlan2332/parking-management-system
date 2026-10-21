package com.parking.backend.invoice.entity;

import com.parking.backend.invoice.model.InvoiceStatus;

import com.parking.backend.pricing.model.ParkingCostBreakdown;
import com.parking.backend.visit.entity.ParkingVisitEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

@Entity
@Table(name = "invoices")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InvoiceEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "visit_id", nullable = false, unique = true)
	private ParkingVisitEntity visit;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private InvoiceStatus status;

	@Column(name = "issued_at", nullable = false, updatable = false)
	private Instant issuedAt;

	@Column(name = "entered_at", nullable = false, updatable = false)
	private Instant enteredAt;

	@Column(name = "exited_at", nullable = false, updatable = false)
	private Instant exitedAt;

	@Column(name = "vehicle_plate", nullable = false, updatable = false, length = 32)
	private String vehiclePlate;

	@Column(name = "spot_code", nullable = false, updatable = false, length = 10)
	private String spotCode;

	@Column(name = "pricing_zone_id", nullable = false, updatable = false, length = 64)
	private String pricingZoneId;

	@Column(nullable = false, updatable = false, length = 3)
	private String currency;

	@Column(name = "total_duration_seconds", nullable = false, updatable = false)
	private long totalDurationSeconds;

	@Column(name = "day_duration_seconds", nullable = false, updatable = false)
	private long dayDurationSeconds;

	@Column(name = "night_duration_seconds", nullable = false, updatable = false)
	private long nightDurationSeconds;

	@Column(name = "day_rate_per_minute", nullable = false, updatable = false, precision = 19, scale = 6)
	private BigDecimal dayRatePerMinute;

	@Column(name = "night_rate_per_minute", nullable = false, updatable = false, precision = 19, scale = 6)
	private BigDecimal nightRatePerMinute;

	@Column(name = "day_cost", nullable = false, updatable = false, precision = 19, scale = 6)
	private BigDecimal dayCost;

	@Column(name = "night_cost", nullable = false, updatable = false, precision = 19, scale = 6)
	private BigDecimal nightCost;

	@Column(name = "total_amount", nullable = false, updatable = false, precision = 19, scale = 2)
	private BigDecimal totalAmount;

	private InvoiceEntity(ParkingVisitEntity visit, ParkingCostBreakdown cost, Instant issuedAt) {
		this.visit = Objects.requireNonNull(visit, "Parking visit must not be null");
		this.issuedAt = Objects.requireNonNull(issuedAt, "Issue time must not be null");
		ParkingCostBreakdown requiredCost = Objects.requireNonNull(cost, "Parking cost must not be null");
		if (visit.getExitedAt() == null) {
			throw new IllegalArgumentException("An invoice requires a completed parking visit");
		}
		this.status = InvoiceStatus.ISSUED;
		this.enteredAt = visit.getEnteredAt();
		this.exitedAt = visit.getExitedAt();
		this.vehiclePlate = visit.getVehicle().getNormalizedPlate();
		this.spotCode = visit.getSpot().getCode();
		this.pricingZoneId = requiredCost.pricingZoneId();
		this.currency = requiredCost.currency();
		this.totalDurationSeconds = requiredCost.totalDurationSeconds();
		this.dayDurationSeconds = requiredCost.dayDurationSeconds();
		this.nightDurationSeconds = requiredCost.nightDurationSeconds();
		this.dayRatePerMinute = requiredCost.dayRatePerMinute();
		this.nightRatePerMinute = requiredCost.nightRatePerMinute();
		this.dayCost = requiredCost.dayCost();
		this.nightCost = requiredCost.nightCost();
		this.totalAmount = requiredCost.totalCost();
	}

	public static InvoiceEntity issue(ParkingVisitEntity visit, ParkingCostBreakdown cost, Instant issuedAt) {
		return new InvoiceEntity(visit, cost, issuedAt);
	}

	public String getInvoiceNumber() {
		return String.format(Locale.ROOT, "INV-%08d", id);
	}
	public ParkingCostBreakdown toCostBreakdown() {
		return new ParkingCostBreakdown(
				currency,
				pricingZoneId,
				totalDurationSeconds,
				dayDurationSeconds,
				nightDurationSeconds,
				dayRatePerMinute,
				nightRatePerMinute,
				dayCost,
				nightCost,
				totalAmount
		);
	}
}
