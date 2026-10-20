package com.parking.backend.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
		@NotBlank
		@Email
		@Size(max = 320)
		String email,

		@NotBlank
		@Size(min = 10, max = 72)
		String password
) {

	public RegisterRequest {
		email = email == null ? null : email.strip();
	}
}
