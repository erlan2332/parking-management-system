package com.parking.backend.common.exception;

public class InvoiceNotIssuedException extends RuntimeException {

	public InvoiceNotIssuedException() {
		super("No invoice was issued for this completed parking visit");
	}
}
