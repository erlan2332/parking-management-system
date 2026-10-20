package com.parking.backend.vehicle;

import com.parking.backend.user.UserEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SourceType;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "vehicles")
public class VehicleEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private UserEntity user;

	@Column(name = "plate_number", nullable = false, length = 32)
	private String plateNumber;

	@Column(name = "normalized_plate", nullable = false, length = 32)
	private String normalizedPlate;

	@CreationTimestamp(source = SourceType.DB)
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected VehicleEntity() {
	}

	public VehicleEntity(UserEntity user, String plateNumber, String normalizedPlate) {
		this.user = Objects.requireNonNull(user, "User must not be null");
		this.plateNumber = requireNonBlank(plateNumber, "Plate number").strip();
		this.normalizedPlate = requireNonBlank(normalizedPlate, "Normalized plate");
	}

	public Long getId() {
		return id;
	}

	public UserEntity getUser() {
		return user;
	}

	public String getPlateNumber() {
		return plateNumber;
	}

	public String getNormalizedPlate() {
		return normalizedPlate;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	private static String requireNonBlank(String value, String fieldName) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(fieldName + " must not be blank");
		}
		return value;
	}
}
