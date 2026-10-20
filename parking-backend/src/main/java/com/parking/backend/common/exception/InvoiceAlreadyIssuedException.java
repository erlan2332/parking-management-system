package com.parking.backend.common.exception;

public class InvoiceAlreadyIssuedException extends RuntimeException {

	public InvoiceAlreadyIssuedException(Throwable cause) {
		super("An invoice has already been issued for this parking visit", cause);
	}
}
