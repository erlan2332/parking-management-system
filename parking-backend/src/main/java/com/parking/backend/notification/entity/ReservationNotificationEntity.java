package com.parking.backend.notification.entity;

import com.parking.backend.notification.model.ReservationNotificationStatus;
import com.parking.backend.notification.model.ReservationNotificationType;

import com.parking.backend.reservation.entity.ReservationEntity;
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
@Table(name = "reservation_notifications")
public class ReservationNotificationEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "reservation_id", nullable = false)
	private ReservationEntity reservation;

	@Enumerated(EnumType.STRING)
	@Column(name = "notification_type", nullable = false, length = 64)
	private ReservationNotificationType notificationType;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private ReservationNotificationStatus status;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "processing_started_at")
	private Instant processingStartedAt;

	@Column(name = "sent_at")
	private Instant sentAt;

	@Column(name = "attempt_count", nullable = false)
	private int attemptCount;

	@Column(name = "last_error", length = 512)
	private String lastError;

	protected ReservationNotificationEntity() {
	}

	public Long getId() {
		return id;
	}

	public ReservationEntity getReservation() {
		return reservation;
	}

	public ReservationNotificationType getNotificationType() {
		return notificationType;
	}

	public ReservationNotificationStatus getStatus() {
		return status;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getProcessingStartedAt() {
		return processingStartedAt;
	}

	public Instant getSentAt() {
		return sentAt;
	}

	public int getAttemptCount() {
		return attemptCount;
	}

	public String getLastError() {
		return lastError;
	}

	public void startSending(Instant startedAt) {
		if (status != ReservationNotificationStatus.PENDING
				&& status != ReservationNotificationStatus.FAILED
				&& status != ReservationNotificationStatus.SENDING) {
			throw new IllegalStateException("Only a pending, failed, or stale notification can be claimed");
		}
		status = ReservationNotificationStatus.SENDING;
		processingStartedAt = Objects.requireNonNull(startedAt, "Processing start time must not be null");
		attemptCount++;
		lastError = null;
	}

	public void markSent(Instant successfulAt) {
		requireSending();
		status = ReservationNotificationStatus.SENT;
		sentAt = Objects.requireNonNull(successfulAt, "Successful send time must not be null");
		lastError = null;
	}

	public void markFailed(String safeError) {
		requireSending();
		status = ReservationNotificationStatus.FAILED;
		lastError = Objects.requireNonNull(safeError, "Failure description must not be null");
	}

	private void requireSending() {
		if (status != ReservationNotificationStatus.SENDING) {
			throw new IllegalStateException("Notification is not being sent");
		}
	}
}
