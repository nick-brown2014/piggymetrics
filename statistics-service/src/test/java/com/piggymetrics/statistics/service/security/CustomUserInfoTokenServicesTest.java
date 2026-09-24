package com.piggymetrics.statistics.service.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

public class CustomUserInfoTokenServicesTest {

	private static final String USER_INFO_URI = "http://auth-service:5000/uaa/users/current";

	private CustomUserInfoTokenServices introspector;
	private MockRestServiceServer server;

	@BeforeEach
	public void setup() {
		RestTemplate restTemplate = new RestTemplate();
		server = MockRestServiceServer.bindTo(restTemplate).build();
		introspector = new CustomUserInfoTokenServices(USER_INFO_URI, "statistics-service");
		introspector.setRestTemplate(restTemplate);
	}

	@Test
	public void shouldExtractPrincipalAndScopesFromLegacyResponse() {
		server.expect(requestTo(USER_INFO_URI))
				.andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token-123"))
				.andRespond(withSuccess("""
						{
						  "user": "account-service",
						  "oauth2Request": {
						    "clientId": "account-service",
						    "scope": ["server"]
						  }
						}
						""", MediaType.APPLICATION_JSON));

		OAuth2AuthenticatedPrincipal principal = introspector.introspect("token-123");

		assertEquals("account-service", principal.getName());
		assertEquals("account-service", principal.getAttribute("client_id"));
		assertEquals(List.of("SCOPE_server"),
				principal.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList());
		server.verify();
	}

	@Test
	public void shouldRejectTokenWhenUserInfoReturnsError() {
		server.expect(requestTo(USER_INFO_URI))
				.andRespond(withStatus(HttpStatus.UNAUTHORIZED));

		assertThrows(BadOpaqueTokenException.class, () -> introspector.introspect("bad-token"));
	}
}
