package com.piggymetrics.account.config;

import com.piggymetrics.account.domain.Account;
import com.piggymetrics.account.service.AccountService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class ResourceServerSecurityTest {

	private static final String USER_JSON = """
			{"username":"newuser","password":"password123"}
			""";

	@Autowired
	private MockMvc mockMvc;

	@MockBean
	private OpaqueTokenIntrospector introspector;

	@MockBean
	private AccountService accountService;

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
		stubAccount();

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
	public void shouldAllowDemoAccountWithoutToken() throws Exception {
		stubAccount();

		mockMvc.perform(get("/demo"))
				.andExpect(status().isOk());
	}

	@Test
	public void shouldAllowAnyAccountWithServerScope() throws Exception {
		stubPrincipal("account-service", List.of("server"));
		stubAccount();

		mockMvc.perform(get("/bob").header(HttpHeaders.AUTHORIZATION, "Bearer service-token"))
				.andExpect(status().isOk());
	}

	@Test
	public void shouldAllowPostWithoutToken() throws Exception {
		mockMvc.perform(post("/").contentType(MediaType.APPLICATION_JSON).content(USER_JSON))
				.andExpect(status().isOk());
	}

	private void stubPrincipal(String name, List<String> scopes) {
		when(introspector.introspect(anyString())).thenReturn(new DefaultOAuth2AuthenticatedPrincipal(
				name, Map.of("sub", name, "scope", scopes),
				scopes.stream().<GrantedAuthority>map(s -> new SimpleGrantedAuthority("SCOPE_" + s)).toList()));
	}

	private void stubAccount() {
		Account account = new Account();
		account.setName("test");
		when(accountService.findByName(anyString())).thenReturn(account);
	}
}
