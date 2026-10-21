package com.parking.backend.security.web;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class CurrentUserIdResolver {

	public Long resolve(Jwt jwt) {
		return Long.valueOf(jwt.getSubject());
	}
}
