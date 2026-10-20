package com.parking.backend.common.exception;

public class InvoiceNotFoundException extends RuntimeException {

	public InvoiceNotFoundException() {
		super("Invoice was not found");
	}
}
