package com.piggymetrics.auth.controller;

import com.piggymetrics.auth.domain.User;
import com.piggymetrics.auth.security.LegacyAuthenticationView;
import com.piggymetrics.auth.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/users")
public class UserController {

	@Autowired
	private UserService userService;

	@GetMapping(value = "/current")
	public Map<String, Object> getUser(Authentication authentication) {
		return LegacyAuthenticationView.of(authentication);
	}

	@PreAuthorize("hasAuthority('SCOPE_server')")
	@PostMapping
	public void createUser(@Valid @RequestBody User user) {
		userService.create(user);
	}
}
