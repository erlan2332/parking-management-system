package com.parking.backend.invoice.service;

import com.parking.backend.invoice.entity.InvoiceEntity;
import com.parking.backend.invoice.repository.InvoiceRepository;

import com.parking.backend.common.exception.InvoiceAlreadyIssuedException;
import com.parking.backend.common.exception.InvoiceNotFoundException;
import com.parking.backend.common.exception.InvoiceNotIssuedException;
import com.parking.backend.common.persistence.DatabaseConstraintViolationDetector;
import com.parking.backend.pricing.model.ParkingCostBreakdown;
import com.parking.backend.pricing.service.ParkingPricingService;
import com.parking.backend.visit.entity.ParkingVisitEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class InvoiceService {

	private static final String UNIQUE_VISIT_CONSTRAINT = "uk_invoices_visit";

	private final InvoiceRepository invoiceRepository;
	private final ParkingPricingService parkingPricingService;

	public InvoiceService(InvoiceRepository invoiceRepository, ParkingPricingService parkingPricingService) {
		this.invoiceRepository = invoiceRepository;
		this.parkingPricingService = parkingPricingService;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public InvoiceEntity issueForVisit(ParkingVisitEntity visit, Instant issuedAt) {
		Optional<InvoiceEntity> existingInvoice = invoiceRepository.findByVisit_Id(visit.getId());
		if (existingInvoice.isPresent()) {
			return existingInvoice.get();
		}

		ParkingCostBreakdown cost = parkingPricingService.calculate(visit.getEnteredAt(), visit.getExitedAt());
		try {
			return invoiceRepository.saveAndFlush(InvoiceEntity.issue(visit, cost, issuedAt));
		} catch (DataIntegrityViolationException exception) {
			if (DatabaseConstraintViolationDetector.hasConstraint(exception, UNIQUE_VISIT_CONSTRAINT)) {
				throw new InvoiceAlreadyIssuedException(exception);
			}
			throw exception;
		}
	}

	@Transactional(readOnly = true)
	public List<InvoiceEntity> getMyInvoices(Long userId) {
		return List.copyOf(invoiceRepository.findAllOwnedByUserOrderByIssuedAtDescIdDesc(userId));
	}

	@Transactional(readOnly = true)
	public InvoiceEntity getOwnedInvoice(Long invoiceId, Long userId) {
		return invoiceRepository.findOwnedById(invoiceId, userId)
				.orElseThrow(InvoiceNotFoundException::new);
	}

	@Transactional(readOnly = true)
	public InvoiceEntity getOwnedInvoiceForVisit(Long visitId, Long userId) {
		return invoiceRepository.findOwnedByVisitId(visitId, userId)
				.orElseThrow(InvoiceNotIssuedException::new);
	}

	@Transactional(readOnly = true)
	public Optional<InvoiceEntity> findByVisitId(Long visitId) {
		return invoiceRepository.findByVisit_Id(visitId);
	}
}
