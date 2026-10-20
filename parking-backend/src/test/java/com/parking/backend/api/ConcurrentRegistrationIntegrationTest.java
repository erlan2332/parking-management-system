package com.parking.backend.api;

import com.parking.backend.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ConcurrentRegistrationIntegrationTest {

	private static final String EMAIL = "concurrent-registration@example.com";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@BeforeEach
	@AfterEach
	void removeTestUser() {
		userRepository.findByEmailIgnoreCase(EMAIL).ifPresent(userRepository::delete);
		userRepository.flush();
	}

	@Test
	void concurrentRegistrationCreatesOnlyOneUser() throws Exception {
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		Callable<Integer> registration = () -> {
			ready.countDown();
			start.await();
			return mockMvc.perform(post("/api/auth/register")
							.contentType(MediaType.APPLICATION_JSON)
							.content("""
									{"email":"concurrent-registration@example.com","password":"SecurePassword123!"}
									"""))
					.andReturn()
					.getResponse()
					.getStatus();
		};

		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			List<Future<Integer>> responses = new ArrayList<>();
			responses.add(executor.submit(registration));
			responses.add(executor.submit(registration));
			assertTrue(ready.await(5, java.util.concurrent.TimeUnit.SECONDS));
			start.countDown();

			List<Integer> statuses = new ArrayList<>();
			for (Future<Integer> response : responses) {
				statuses.add(response.get());
			}
			Collections.sort(statuses);

			assertEquals(List.of(201, 409), statuses);
			assertTrue(userRepository.findByEmailIgnoreCase(EMAIL).isPresent());
		} finally {
			executor.shutdownNow();
		}
	}
}
