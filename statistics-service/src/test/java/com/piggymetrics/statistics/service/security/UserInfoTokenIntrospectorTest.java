package com.piggymetrics.statistics.service.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.OAuth2TokenIntrospectionClaimNames;
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException;
import org.springframework.security.oauth2.server.resource.introspection.OAuth2IntrospectionException;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class UserInfoTokenIntrospectorTest {

	private static final String USER_INFO_URI = "http://auth-service:5000/uaa/users/current";

	private static final String USER_RESPONSE = """
			{
			  "authorities": [],
			  "details": {"tokenValue": "user-token", "tokenType": "bearer"},
			  "authenticated": true,
			  "userAuthentication": {"name": "demo", "principal": {"username": "demo"}},
			  "principal": {"username": "demo", "authorities": []},
			  "oauth2Request": {"clientId": "browser", "scope": ["ui"], "approved": true},
			  "clientOnly": false,
			  "name": "demo"
			}
			""";

	private static final String CLIENT_RESPONSE = """
			{
			  "authorities": [],
			  "authenticated": true,
			  "userAuthentication": null,
			  "principal": "account-service",
			  "oauth2Request": {"clientId": "account-service", "scope": ["server"], "approved": true},
			  "clientOnly": true,
			  "name": "account-service"
			}
			""";

	private MockRestServiceServer server;

	private UserInfoTokenIntrospector introspector;

	@BeforeEach
	void setup() {
		RestTemplate restTemplate = new RestTemplate();
		server = MockRestServiceServer.bindTo(restTemplate).build();
		introspector = new UserInfoTokenIntrospector(USER_INFO_URI, restTemplate);
	}

	@Test
	void shouldExtractUserPrincipal() {
		server.expect(requestTo(USER_INFO_URI))
				.andExpect(method(HttpMethod.GET))
				.andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer user-token"))
				.andRespond(withSuccess(USER_RESPONSE, MediaType.APPLICATION_JSON));

		OAuth2AuthenticatedPrincipal principal = introspector.introspect("user-token");

		assertEquals("demo", principal.getName());
		assertEquals("browser", principal.getAttribute(OAuth2TokenIntrospectionClaimNames.CLIENT_ID));
		assertEquals(List.of("ui"), principal.getAttribute(OAuth2TokenIntrospectionClaimNames.SCOPE));
		assertEquals(Set.of("SCOPE_ui"), AuthorityUtils.authorityListToSet(principal.getAuthorities()));
		server.verify();
	}

	@Test
	void shouldExtractClientPrincipalWithServerScope() {
		server.expect(requestTo(USER_INFO_URI))
				.andRespond(withSuccess(CLIENT_RESPONSE, MediaType.APPLICATION_JSON));

		OAuth2AuthenticatedPrincipal principal = introspector.introspect("client-token");

		assertEquals("account-service", principal.getName());
		assertEquals("account-service", principal.getAttribute(OAuth2TokenIntrospectionClaimNames.CLIENT_ID));
		assertEquals(Set.of("SCOPE_server"), AuthorityUtils.authorityListToSet(principal.getAuthorities()));
	}

	@Test
	void shouldExtractAuthorities() {
		server.expect(requestTo(USER_INFO_URI))
				.andRespond(withSuccess("""
						{"name": "admin", "authorities": [{"authority": "ROLE_ADMIN"}],
						 "oauth2Request": {"clientId": "browser", "scope": ["ui"]}}
						""", MediaType.APPLICATION_JSON));

		OAuth2AuthenticatedPrincipal principal = introspector.introspect("token");

		assertEquals(Set.of("ROLE_ADMIN", "SCOPE_ui"), AuthorityUtils.authorityListToSet(principal.getAuthorities()));
	}

	@Test
	void shouldDefaultToUserRoleWhenAuthoritiesAreMissing() {
		server.expect(requestTo(USER_INFO_URI))
				.andRespond(withSuccess("{\"name\": \"demo\"}", MediaType.APPLICATION_JSON));

		OAuth2AuthenticatedPrincipal principal = introspector.introspect("token");

		assertEquals("demo", principal.getName());
		assertEquals(Set.of("ROLE_USER"),
				AuthorityUtils.authorityListToSet(principal.getAuthorities()));
	}

	@Test
	void shouldRejectInvalidToken() {
		server.expect(requestTo(USER_INFO_URI))
				.andRespond(withStatus(HttpStatus.UNAUTHORIZED)
						.contentType(MediaType.APPLICATION_JSON)
						.body("{\"error\":\"invalid_token\"}"));

		assertThrows(BadOpaqueTokenException.class, () -> introspector.introspect("bad-token"));
	}

	@Test
	void shouldRejectErrorResponse() {
		server.expect(requestTo(USER_INFO_URI))
				.andRespond(withSuccess("{\"error\":\"invalid_token\"}", MediaType.APPLICATION_JSON));

		assertThrows(BadOpaqueTokenException.class, () -> introspector.introspect("bad-token"));
	}

	@Test
	void shouldFailWhenAuthServiceIsUnavailable() {
		server.expect(requestTo(USER_INFO_URI)).andRespond(withServerError());

		assertThrows(OAuth2IntrospectionException.class, () -> introspector.introspect("token"));
	}
}
