package com.parking.backend.user.service;

import com.parking.backend.user.entity.UserEntity;
import com.parking.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;

import com.parking.backend.common.exception.UserNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

	private final UserRepository userRepository;

	@Transactional(readOnly = true)
	public UserEntity getById(Long userId) {
		return userRepository.findById(userId)
				.orElseThrow(() -> new UserNotFoundException(userId));
	}
}
