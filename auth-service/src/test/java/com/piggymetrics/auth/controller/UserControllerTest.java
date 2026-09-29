package com.piggymetrics.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.piggymetrics.auth.domain.User;
import com.piggymetrics.auth.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
public class UserControllerTest {

	private static final ObjectMapper mapper = new ObjectMapper();

	@InjectMocks
	private UserController accountController;

	@Mock
	private UserService userService;

	private MockMvc mockMvc;

	@BeforeEach
	public void setup() {
		this.mockMvc = MockMvcBuilders.standaloneSetup(accountController).build();
	}

	@Test
	public void shouldCreateNewUser() throws Exception {

		final User user = new User();
		user.setUsername("test");
		user.setPassword("password");

		String json = mapper.writeValueAsString(user);

		mockMvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().isOk());
	}

	@Test
	public void shouldFailWhenUserIsNotValid() throws Exception {

		mockMvc.perform(post("/users"))
				.andExpect(status().isBadRequest());
	}

	@Test
	public void shouldReturnCurrentUser() throws Exception {

		DefaultOAuth2AuthenticatedPrincipal principal = new DefaultOAuth2AuthenticatedPrincipal(
				"test",
				Map.of("client_id", "browser", "scope", List.of("ui"), "grant_type", "password"),
				List.of(new SimpleGrantedAuthority("SCOPE_ui")));
		BearerTokenAuthentication authentication = new BearerTokenAuthentication(
				principal,
				new org.springframework.security.oauth2.core.OAuth2AccessToken(
						org.springframework.security.oauth2.core.OAuth2AccessToken.TokenType.BEARER,
						"token", null, null),
				principal.getAuthorities());

		mockMvc.perform(get("/users/current").principal(authentication))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("test"))
				.andExpect(jsonPath("$.user").value("test"));
	}
}
