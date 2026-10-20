package com.parking.backend.reservation;

import com.parking.backend.parking.ParkingSpotEntity;
import com.parking.backend.user.UserEntity;
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
@Table(name = "reservations")
public class ReservationEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private UserEntity user;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "vehicle_id", nullable = false)
	private VehicleEntity vehicle;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "spot_id", nullable = false)
	private ParkingSpotEntity spot;

	@Column(name = "start_at", nullable = false)
	private Instant startAt;

	@Column(name = "end_at", nullable = false)
	private Instant endAt;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 32)
	private ReservationStatus status;

	@Column(name = "hold_expires_at")
	private Instant holdExpiresAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "confirmed_at")
	private Instant confirmedAt;

	@Column(name = "cancelled_at")
	private Instant cancelledAt;

	protected ReservationEntity() {
	}

	private ReservationEntity(
			UserEntity user,
			VehicleEntity vehicle,
			ParkingSpotEntity spot,
			Instant startAt,
			Instant endAt,
			Instant holdExpiresAt,
			Instant createdAt
	) {
		this.user = Objects.requireNonNull(user, "User must not be null");
		this.vehicle = Objects.requireNonNull(vehicle, "Vehicle must not be null");
		this.spot = Objects.requireNonNull(spot, "Parking spot must not be null");
		this.startAt = Objects.requireNonNull(startAt, "Start time must not be null");
		this.endAt = Objects.requireNonNull(endAt, "End time must not be null");
		this.holdExpiresAt = Objects.requireNonNull(holdExpiresAt, "Hold expiration must not be null");
		this.createdAt = Objects.requireNonNull(createdAt, "Creation time must not be null");
		this.status = ReservationStatus.HELD;
	}

	public static ReservationEntity createHold(
			UserEntity user,
			VehicleEntity vehicle,
			ParkingSpotEntity spot,
			Instant startAt,
			Instant endAt,
			Instant holdExpiresAt,
			Instant createdAt
	) {
		return new ReservationEntity(user, vehicle, spot, startAt, endAt, holdExpiresAt, createdAt);
	}

	public Long getId() {
		return id;
	}

	public UserEntity getUser() {
		return user;
	}

	public VehicleEntity getVehicle() {
		return vehicle;
	}

	public ParkingSpotEntity getSpot() {
		return spot;
	}

	public Instant getStartAt() {
		return startAt;
	}

	public Instant getEndAt() {
		return endAt;
	}

	public ReservationStatus getStatus() {
		return status;
	}

	public Instant getHoldExpiresAt() {
		return holdExpiresAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getConfirmedAt() {
		return confirmedAt;
	}

	public Instant getCancelledAt() {
		return cancelledAt;
	}

	public boolean isExpiredHoldAt(Instant now) {
		return status == ReservationStatus.HELD && !holdExpiresAt.isAfter(now);
	}

	public void expireHold() {
		status = ReservationStatus.HOLD_EXPIRED;
	}

	public void confirm(Instant confirmedAt) {
		status = ReservationStatus.ACTIVE;
		this.confirmedAt = Objects.requireNonNull(confirmedAt, "Confirmation time must not be null");
	}

	public void release(Instant releasedAt) {
		status = ReservationStatus.HOLD_RELEASED;
		cancelledAt = Objects.requireNonNull(releasedAt, "Release time must not be null");
	}

	public void cancel(Instant cancelledAt) {
		status = ReservationStatus.CANCELLED;
		this.cancelledAt = Objects.requireNonNull(cancelledAt, "Cancellation time must not be null");
	}

	public void checkIn() {
		if (status != ReservationStatus.ACTIVE) {
			throw new IllegalStateException("Only an active reservation can be checked in");
		}
		status = ReservationStatus.CHECKED_IN;
	}

	public void complete() {
		if (status != ReservationStatus.CHECKED_IN) {
			throw new IllegalStateException("Only a checked-in reservation can be completed");
		}
		status = ReservationStatus.COMPLETED;
	}
}
