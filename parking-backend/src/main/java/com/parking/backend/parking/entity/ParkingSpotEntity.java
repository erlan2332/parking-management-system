package com.parking.backend.parking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Objects;

@Entity
@Table(name = "parking_spots")
public class ParkingSpotEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "code", nullable = false, unique = true, length = 10)
	private String code;

	@Column(name = "active", nullable = false)
	private boolean active;

	protected ParkingSpotEntity() {
	}

	public ParkingSpotEntity(String code, boolean active) {
		this.code = requireNonBlank(code);
		this.active = active;
	}

	public Long getId() {
		return id;
	}

	public String getCode() {
		return code;
	}

	public boolean isActive() {
		return active;
	}

	private static String requireNonBlank(String code) {
		Objects.requireNonNull(code, "Parking spot code must not be null");
		if (code.isBlank()) {
			throw new IllegalArgumentException("Parking spot code must not be blank");
		}
		return code.strip();
	}
}
