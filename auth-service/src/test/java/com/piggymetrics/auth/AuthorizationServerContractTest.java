package com.piggymetrics.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.piggymetrics.auth.domain.User;
import com.piggymetrics.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;

@SpringBootTest
@AutoConfigureMockMvc
public class AuthorizationServerContractTest {

	private static final ObjectMapper mapper = new ObjectMapper();
	private static final String BROWSER_BASIC = "Basic YnJvd3Nlcjo="; // "browser:"

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@BeforeEach
	public void setup() {
		User user = new User();
		user.setUsername("test");
		user.setPassword(new BCryptPasswordEncoder().encode("password"));
		userRepository.save(user);
	}

	private String passwordGrantToken() throws Exception {
		MvcResult result = mockMvc.perform(post("/oauth/token")
						.header("Authorization", BROWSER_BASIC)
						.contentType(MediaType.APPLICATION_FORM_URLENCODED)
						.param("grant_type", "password")
						.param("username", "test")
						.param("password", "password")
						.param("scope", "ui"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.access_token").exists())
				.andExpect(jsonPath("$.token_type").value("Bearer"))
				.andExpect(jsonPath("$.refresh_token").exists())
				.andReturn();
		JsonNode body = mapper.readTree(result.getResponse().getContentAsString());
		String accessToken = body.get("access_token").asText();
		assertThat(accessToken.split("\\.")).hasSizeLessThan(3);
		return accessToken;
	}

	private String serviceToken() throws Exception {
		MvcResult result = mockMvc.perform(post("/oauth/token")
						.with(httpBasic("account-service", "account-pass"))
						.contentType(MediaType.APPLICATION_FORM_URLENCODED)
						.param("grant_type", "client_credentials")
						.param("scope", "server"))
				.andExpect(status().isOk())
				.andReturn();
		String accessToken = mapper.readTree(result.getResponse().getContentAsString())
				.get("access_token").asText();
		assertThat(accessToken.split("\\.")).hasSizeLessThan(3);
		return accessToken;
	}

	@Test
	public void passwordGrantReturnsOpaqueTokens() throws Exception {
		passwordGrantToken();
	}

	@Test
	public void wrongPasswordFails() throws Exception {
		mockMvc.perform(post("/oauth/token")
						.header("Authorization", BROWSER_BASIC)
						.contentType(MediaType.APPLICATION_FORM_URLENCODED)
						.param("grant_type", "password")
						.param("username", "test")
						.param("password", "wrong"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("invalid_grant"));
	}

	@Test
	public void clientCredentialsGrantReturnsOpaqueToken() throws Exception {
		serviceToken();
	}

	@Test
	public void refreshTokenGrantIssuesNewAccessToken() throws Exception {
		MvcResult result = mockMvc.perform(post("/oauth/token")
						.header("Authorization", BROWSER_BASIC)
						.contentType(MediaType.APPLICATION_FORM_URLENCODED)
						.param("grant_type", "password")
						.param("username", "test")
						.param("password", "password")
						.param("scope", "ui"))
				.andExpect(status().isOk())
				.andReturn();
		String refreshToken = mapper.readTree(result.getResponse().getContentAsString())
				.get("refresh_token").asText();

		mockMvc.perform(post("/oauth/token")
						.header("Authorization", BROWSER_BASIC)
						.contentType(MediaType.APPLICATION_FORM_URLENCODED)
						.param("grant_type", "refresh_token")
						.param("refresh_token", refreshToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.access_token").exists());
	}

	@Test
	public void userInfoForBrowserToken() throws Exception {
		String token = passwordGrantToken();
		mockMvc.perform(get("/users/current").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("test"))
				.andExpect(jsonPath("$.user").value("test"))
				.andExpect(jsonPath("$.oauth2Request.clientId").value("browser"))
				.andExpect(jsonPath("$.oauth2Request.scope[0]").value("ui"))
				.andExpect(jsonPath("$.authorities[0]").value("SCOPE_ui"))
				.andExpect(jsonPath("$.clientOnly").value(false));
	}

	@Test
	public void userInfoForServiceToken() throws Exception {
		String token = serviceToken();
		mockMvc.perform(get("/users/current").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("account-service"))
				.andExpect(jsonPath("$.oauth2Request.scope[0]").value("server"))
				.andExpect(jsonPath("$.clientOnly").value(true));
	}

	@Test
	public void userInfoRequiresValidToken() throws Exception {
		mockMvc.perform(get("/users/current"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/users/current").header("Authorization", "Bearer garbage"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	public void createUserRequiresServerScope() throws Exception {
		String browserToken = passwordGrantToken();
		String serviceToken = serviceToken();

		String json = "{\"username\": \"newuser\", \"password\": \"newpassword\"}";

		mockMvc.perform(post("/users")
						.header("Authorization", "Bearer " + browserToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(json))
				.andExpect(status().isForbidden());

		mockMvc.perform(post("/users")
						.header("Authorization", "Bearer " + serviceToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(json))
				.andExpect(status().isOk());

		mockMvc.perform(post("/oauth/token")
						.header("Authorization", BROWSER_BASIC)
						.contentType(MediaType.APPLICATION_FORM_URLENCODED)
						.param("grant_type", "password")
						.param("username", "newuser")
						.param("password", "newpassword"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.access_token").exists());
	}
}
