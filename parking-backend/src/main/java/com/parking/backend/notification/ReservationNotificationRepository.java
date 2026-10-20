package com.parking.backend.notification;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface ReservationNotificationRepository
		extends JpaRepository<ReservationNotificationEntity, Long> {

	@Modifying
	@Query(value = """
			INSERT INTO reservation_notifications (
			    reservation_id,
			    notification_type,
			    status,
			    created_at,
			    attempt_count
			)
			SELECT reservation.id,
			       'RESERVATION_ENDING_SOON',
			       'PENDING',
			       :now,
			       0
			FROM reservations reservation
			WHERE reservation.status = 'CHECKED_IN'
			  AND reservation.end_at > :now
			  AND reservation.end_at <= :windowEnd
			  AND EXISTS (
			      SELECT 1
			      FROM parking_visits visit
			      WHERE visit.reservation_id = reservation.id
			        AND visit.exited_at IS NULL
			  )
			ON CONFLICT (reservation_id, notification_type) DO NOTHING
			""", nativeQuery = true)
	int createDueReminders(@Param("now") Instant now, @Param("windowEnd") Instant windowEnd);

	@Query(value = """
			SELECT notification.id
			FROM reservation_notifications notification
			JOIN reservations reservation ON reservation.id = notification.reservation_id
			WHERE notification.notification_type = 'RESERVATION_ENDING_SOON'
			  AND (
			      notification.status = 'PENDING'
			      OR (
			          notification.status = 'FAILED'
			          AND notification.processing_started_at < :retryBefore
			      )
			      OR (
			          notification.status = 'SENDING'
			          AND notification.processing_started_at <= :staleBefore
			      )
			  )
			  AND reservation.status = 'CHECKED_IN'
			  AND reservation.end_at > :now
			  AND reservation.end_at <= :windowEnd
			  AND EXISTS (
			      SELECT 1
			      FROM parking_visits visit
			      WHERE visit.reservation_id = reservation.id
			        AND visit.exited_at IS NULL
			  )
			ORDER BY reservation.end_at, notification.id
			LIMIT 1
			FOR UPDATE OF notification SKIP LOCKED
			""", nativeQuery = true)
	Optional<Long> findNextClaimableId(
			@Param("now") Instant now,
			@Param("windowEnd") Instant windowEnd,
			@Param("retryBefore") Instant retryBefore,
			@Param("staleBefore") Instant staleBefore
	);

	@EntityGraph(attributePaths = {"reservation.user", "reservation.vehicle", "reservation.spot"})
	@Query("SELECT notification FROM ReservationNotificationEntity notification WHERE notification.id = :id")
	Optional<ReservationNotificationEntity> findWithReservationById(@Param("id") Long id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT notification FROM ReservationNotificationEntity notification WHERE notification.id = :id")
	Optional<ReservationNotificationEntity> findByIdForUpdate(@Param("id") Long id);

	@Query(value = """
			SELECT EXISTS (
			    SELECT 1
			    FROM reservation_notifications notification
			    JOIN reservations reservation ON reservation.id = notification.reservation_id
			    WHERE notification.id = :notificationId
			      AND notification.status = 'SENDING'
			      AND reservation.status = 'CHECKED_IN'
			      AND reservation.end_at > :now
			      AND reservation.end_at <= :windowEnd
			      AND EXISTS (
			          SELECT 1
			          FROM parking_visits visit
			          WHERE visit.reservation_id = reservation.id
			            AND visit.exited_at IS NULL
			      )
			)
			""", nativeQuery = true)
	boolean isStillEligible(
			@Param("notificationId") Long notificationId,
			@Param("now") Instant now,
			@Param("windowEnd") Instant windowEnd
	);

	Optional<ReservationNotificationEntity> findByReservation_IdAndNotificationType(
			Long reservationId,
			ReservationNotificationType notificationType
	);
}
