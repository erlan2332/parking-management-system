package com.parking.backend.invoice.repository;

import com.parking.backend.invoice.entity.InvoiceEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<InvoiceEntity, Long> {

	Optional<InvoiceEntity> findByVisit_Id(Long visitId);

	@Query("""
			SELECT invoice
			FROM InvoiceEntity invoice
			JOIN FETCH invoice.visit visit
			JOIN visit.vehicle vehicle
			WHERE vehicle.user.id = :userId
			ORDER BY invoice.issuedAt DESC, invoice.id DESC
			""")
	List<InvoiceEntity> findAllOwnedByUserOrderByIssuedAtDescIdDesc(@Param("userId") Long userId);

	@Query("""
			SELECT invoice
			FROM InvoiceEntity invoice
			JOIN FETCH invoice.visit visit
			JOIN visit.vehicle vehicle
			WHERE invoice.id = :invoiceId AND vehicle.user.id = :userId
			""")
	Optional<InvoiceEntity> findOwnedById(
			@Param("invoiceId") Long invoiceId,
			@Param("userId") Long userId
	);

	@Query("""
			SELECT invoice
			FROM InvoiceEntity invoice
			JOIN FETCH invoice.visit visit
			JOIN visit.vehicle vehicle
			WHERE visit.id = :visitId AND vehicle.user.id = :userId
			""")
	Optional<InvoiceEntity> findOwnedByVisitId(
			@Param("visitId") Long visitId,
			@Param("userId") Long userId
	);
}
