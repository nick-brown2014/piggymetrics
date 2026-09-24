package com.piggymetrics.statistics.config;

import com.piggymetrics.statistics.service.StatisticsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class ResourceServerSecurityTest {

	private static final String ACCOUNT_JSON = """
			{"incomes":[],"expenses":[],"saving":{"amount":0,"currency":"USD","interest":0,"deposit":false,"capitalization":false}}
			""";

	@Autowired
	private MockMvc mockMvc;

	@MockBean
	private OpaqueTokenIntrospector introspector;

	@MockBean
	private StatisticsService statisticsService;

	@Test
	public void shouldRejectRequestWithoutToken() throws Exception {
		mockMvc.perform(get("/current")).andExpect(status().isUnauthorized());
	}

	@Test
	public void shouldRejectInvalidToken() throws Exception {
		when(introspector.introspect(anyString())).thenThrow(new BadOpaqueTokenException("inactive"));

		mockMvc.perform(get("/current").header(HttpHeaders.AUTHORIZATION, "Bearer bad"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	public void shouldAllowCurrentForAuthenticatedUser() throws Exception {
		stubPrincipal("alice", Collections.emptyList());

		mockMvc.perform(get("/current").header(HttpHeaders.AUTHORIZATION, "Bearer user-token"))
				.andExpect(status().isOk());
	}

	@Test
	public void shouldForbidOtherAccountWithoutServerScope() throws Exception {
		stubPrincipal("alice", Collections.emptyList());

		mockMvc.perform(get("/bob").header(HttpHeaders.AUTHORIZATION, "Bearer user-token"))
				.andExpect(status().isForbidden());
	}

	@Test
	public void shouldAllowDemoAccountWithoutServerScope() throws Exception {
		stubPrincipal("alice", Collections.emptyList());

		mockMvc.perform(get("/demo").header(HttpHeaders.AUTHORIZATION, "Bearer user-token"))
				.andExpect(status().isOk());
	}

	@Test
	public void shouldAllowAnyAccountWithServerScope() throws Exception {
		stubPrincipal("account-service", List.of("server"));

		mockMvc.perform(get("/bob").header(HttpHeaders.AUTHORIZATION, "Bearer service-token"))
				.andExpect(status().isOk());
	}

	@Test
	public void shouldForbidPutWithoutServerScope() throws Exception {
		stubPrincipal("alice", Collections.emptyList());

		mockMvc.perform(put("/alice").header(HttpHeaders.AUTHORIZATION, "Bearer user-token")
				.contentType(MediaType.APPLICATION_JSON).content(ACCOUNT_JSON))
				.andExpect(status().isForbidden());
	}

	@Test
	public void shouldAllowPutWithServerScope() throws Exception {
		stubPrincipal("account-service", List.of("server"));

		mockMvc.perform(put("/alice").header(HttpHeaders.AUTHORIZATION, "Bearer service-token")
				.contentType(MediaType.APPLICATION_JSON).content(ACCOUNT_JSON))
				.andExpect(status().isOk());
	}

	private void stubPrincipal(String name, List<String> scopes) {
		when(introspector.introspect(anyString())).thenReturn(new DefaultOAuth2AuthenticatedPrincipal(
				name, Map.of("sub", name, "scope", scopes),
				scopes.stream().<GrantedAuthority>map(s -> new SimpleGrantedAuthority("SCOPE_" + s)).toList()));
	}
}
