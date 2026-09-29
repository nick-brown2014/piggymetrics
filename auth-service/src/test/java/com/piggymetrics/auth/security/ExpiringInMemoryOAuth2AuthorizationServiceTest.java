package com.piggymetrics.auth.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class ExpiringInMemoryOAuth2AuthorizationServiceTest {

	private final ExpiringInMemoryOAuth2AuthorizationService service =
			new ExpiringInMemoryOAuth2AuthorizationService();

	private static final RegisteredClient CLIENT = RegisteredClient.withId("client")
			.clientId("client")
			.authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
			.authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
			.scope("server")
			.build();

	private static OAuth2Authorization authorization(String accessValue, Instant accessExpiresAt,
													 String refreshValue, Instant refreshExpiresAt) {
		OAuth2AccessToken accessToken = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,
				accessValue, accessExpiresAt.minus(12, ChronoUnit.HOURS), accessExpiresAt);
		OAuth2Authorization.Builder builder = OAuth2Authorization.withRegisteredClient(CLIENT)
				.principalName("principal")
				.authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
				.authorizedScopes(CLIENT.getScopes())
				.token(accessToken);
		if (refreshValue != null) {
			OAuth2RefreshToken refreshToken = new OAuth2RefreshToken(
					refreshValue, refreshExpiresAt.minus(30, ChronoUnit.DAYS), refreshExpiresAt);
			builder.refreshToken(refreshToken);
		}
		return builder.build();
	}

	@Test
	public void shouldRemoveOnlyFullyExpiredAuthorizations() {
		Instant now = Instant.now();

		OAuth2Authorization expired = authorization(
				"expired-access", now.minus(1, ChronoUnit.HOURS), null, null);
		OAuth2Authorization expiredAccessLiveRefresh = authorization(
				"refreshable-access", now.minus(1, ChronoUnit.HOURS),
				"live-refresh", now.plus(30, ChronoUnit.DAYS));
		OAuth2Authorization live = authorization(
				"live-access", now.plus(12, ChronoUnit.HOURS), null, null);

		service.save(expired);
		service.save(expiredAccessLiveRefresh);
		service.save(live);

		service.removeExpired();

		assertNull(service.findById(expired.getId()));
		assertNotNull(service.findById(expiredAccessLiveRefresh.getId()));
		assertNotNull(service.findById(live.getId()));

		assertNull(service.findByToken("expired-access", OAuth2TokenType.ACCESS_TOKEN));
		assertNotNull(service.findByToken("refreshable-access", OAuth2TokenType.ACCESS_TOKEN));
		assertNotNull(service.findByToken("live-access", OAuth2TokenType.ACCESS_TOKEN));
	}

	@Test
	public void expiredTokenRemainsFindableUntilSwept() {
		Instant now = Instant.now();
		OAuth2Authorization expired = authorization(
				"expired-access", now.minus(1, ChronoUnit.HOURS), null, null);
		service.save(expired);

		// findByToken itself does not evict — the introspector checks isActive()
		assertNotNull(service.findByToken("expired-access", OAuth2TokenType.ACCESS_TOKEN));
	}
}
