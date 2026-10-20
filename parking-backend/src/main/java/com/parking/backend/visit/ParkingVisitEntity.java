package com.parking.backend.visit;

import com.parking.backend.parking.ParkingSpotEntity;
import com.parking.backend.reservation.ReservationEntity;
import com.parking.backend.vehicle.VehicleEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "parking_visits")
public class ParkingVisitEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "vehicle_id", nullable = false)
	private VehicleEntity vehicle;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "spot_id", nullable = false)
	private ParkingSpotEntity spot;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "reservation_id")
	private ReservationEntity reservation;

	@Enumerated(EnumType.STRING)
	@Column(name = "entry_type", nullable = false, length = 16)
	private ParkingEntryType entryType;

	@Column(name = "entered_at", nullable = false)
	private Instant enteredAt;

	@Column(name = "exited_at")
	private Instant exitedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected ParkingVisitEntity() {
	}

	private ParkingVisitEntity(
			VehicleEntity vehicle,
			ParkingSpotEntity spot,
			ReservationEntity reservation,
			ParkingEntryType entryType,
			Instant enteredAt
	) {
		this.vehicle = Objects.requireNonNull(vehicle, "Vehicle must not be null");
		this.spot = Objects.requireNonNull(spot, "Parking spot must not be null");
		this.reservation = reservation;
		this.entryType = Objects.requireNonNull(entryType, "Entry type must not be null");
		this.enteredAt = Objects.requireNonNull(enteredAt, "Entry time must not be null");
		this.createdAt = enteredAt;
	}

	public static ParkingVisitEntity forReservation(
			VehicleEntity vehicle,
			ParkingSpotEntity spot,
			ReservationEntity reservation,
			Instant enteredAt
	) {
		return new ParkingVisitEntity(
				vehicle,
				spot,
				Objects.requireNonNull(reservation, "Reservation must not be null"),
				ParkingEntryType.RESERVATION,
				enteredAt
		);
	}

	public static ParkingVisitEntity walkIn(
			VehicleEntity vehicle,
			ParkingSpotEntity spot,
			Instant enteredAt
	) {
		return new ParkingVisitEntity(vehicle, spot, null, ParkingEntryType.WALK_IN, enteredAt);
	}

	public Long getId() {
		return id;
	}

	public VehicleEntity getVehicle() {
		return vehicle;
	}

	public ParkingSpotEntity getSpot() {
		return spot;
	}

	public ReservationEntity getReservation() {
		return reservation;
	}

	public ParkingEntryType getEntryType() {
		return entryType;
	}

	public Instant getEnteredAt() {
		return enteredAt;
	}

	public Instant getExitedAt() {
		return exitedAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public ParkingVisitStatus getStatus() {
		return exitedAt == null ? ParkingVisitStatus.ACTIVE : ParkingVisitStatus.COMPLETED;
	}

	public void complete(Instant exitTime) {
		if (exitedAt != null) {
			throw new IllegalStateException("Parking visit is already completed");
		}
		Instant requiredExitTime = Objects.requireNonNull(exitTime, "Exit time must not be null");
		if (requiredExitTime.isBefore(enteredAt)) {
			throw new IllegalArgumentException("Exit time must not be earlier than entry time");
		}
		exitedAt = requiredExitTime;
	}
}
