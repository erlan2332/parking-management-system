package com.parking.backend.parking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

@Entity
@Table(name = "parking_spots")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ParkingSpotEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "code", nullable = false, unique = true, length = 10)
	private String code;

	@Column(name = "active", nullable = false)
	private boolean active;

	public ParkingSpotEntity(String code, boolean active) {
		this.code = requireNonBlank(code);
		this.active = active;
	}

	private static String requireNonBlank(String code) {
		Objects.requireNonNull(code, "Parking spot code must not be null");
		if (code.isBlank()) {
			throw new IllegalArgumentException("Parking spot code must not be blank");
		}
		return code.strip();
	}
}
