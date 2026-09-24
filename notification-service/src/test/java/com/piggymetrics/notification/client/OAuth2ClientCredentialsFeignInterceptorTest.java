package com.piggymetrics.notification.client;

import feign.RequestTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class OAuth2ClientCredentialsFeignInterceptorTest {

	private static final String REGISTRATION_ID = "notification-service";

	@Mock
	private OAuth2AuthorizedClientManager manager;

	@Test
	public void shouldAddBearerTokenToRequest() {
		OAuth2AccessToken token = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,
				"abc", Instant.now(), Instant.now().plusSeconds(3600));
		ClientRegistration registration = ClientRegistration.withRegistrationId(REGISTRATION_ID)
				.clientId(REGISTRATION_ID)
				.authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
				.tokenUri("http://localhost:5000/uaa/oauth/token")
				.build();
		when(manager.authorize(any(OAuth2AuthorizeRequest.class)))
				.thenReturn(new OAuth2AuthorizedClient(registration, REGISTRATION_ID, token));

		RequestTemplate template = new RequestTemplate();
		new OAuth2ClientCredentialsFeignInterceptor(manager, REGISTRATION_ID).apply(template);

		assertTrue(template.headers().containsKey(HttpHeaders.AUTHORIZATION));
		assertEquals("Bearer abc", template.headers().get(HttpHeaders.AUTHORIZATION).iterator().next());
	}

	@Test
	public void shouldFailWhenTokenCannotBeObtained() {
		when(manager.authorize(any(OAuth2AuthorizeRequest.class))).thenReturn(null);

		RequestTemplate template = new RequestTemplate();
		assertThrows(IllegalStateException.class,
				() -> new OAuth2ClientCredentialsFeignInterceptor(manager, REGISTRATION_ID).apply(template));
	}
}
