package com.parking.backend.user;

import com.parking.backend.security.CurrentUserIdResolver;
import com.parking.backend.user.dto.UserResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

	private final UserService userService;
	private final CurrentUserIdResolver currentUserIdResolver;

	public UserController(UserService userService, CurrentUserIdResolver currentUserIdResolver) {
		this.userService = userService;
		this.currentUserIdResolver = currentUserIdResolver;
	}

	@GetMapping("/me")
	public UserResponse getCurrentUser(@AuthenticationPrincipal Jwt jwt) {
		Long userId = currentUserIdResolver.resolve(jwt);
		return UserResponse.from(userService.getById(userId));
	}
}
