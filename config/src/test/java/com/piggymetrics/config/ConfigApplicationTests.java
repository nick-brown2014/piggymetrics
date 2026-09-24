package com.piggymetrics.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.security.user.password=test-password")
@AutoConfigureMockMvc
public class ConfigApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	public void contextLoads() {
	}

	@Test
	public void actuatorHealthIsPublic() throws Exception {
		mockMvc.perform(get("/actuator/health"))
				.andExpect(status().isOk());
	}

	@Test
	public void configEndpointRequiresAuthentication() throws Exception {
		mockMvc.perform(get("/account-service/default"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	public void configEndpointRejectsBadCredentials() throws Exception {
		mockMvc.perform(get("/account-service/default").with(httpBasic("user", "wrong")))
				.andExpect(status().isUnauthorized());
	}

	@Test
	public void servesSharedConfigWithBasicAuth() throws Exception {
		mockMvc.perform(get("/account-service/default").with(httpBasic("user", "test-password")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("account-service"))
				.andExpect(jsonPath("$.propertySources[*].name").isArray());
	}
}
