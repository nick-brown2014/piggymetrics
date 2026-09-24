package com.piggymetrics.account.client;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;

/**
 * Feign {@link RequestInterceptor} that obtains an OAuth2 access token via the
 * client_credentials grant and sets it on every outgoing Feign request,
 * mirroring the legacy {@code OAuth2FeignRequestInterceptor} behavior.
 */
public class OAuth2ClientCredentialsFeignRequestInterceptor implements RequestInterceptor {

	private final OAuth2AuthorizedClientManager manager;

	private final String registrationId;

	public OAuth2ClientCredentialsFeignRequestInterceptor(OAuth2AuthorizedClientManager manager,
			String registrationId) {
		this.manager = manager;
		this.registrationId = registrationId;
	}

	@Override
	public void apply(RequestTemplate template) {
		OAuth2AuthorizeRequest request = OAuth2AuthorizeRequest.withClientRegistrationId(registrationId)
				.principal(registrationId)
				.build();
		OAuth2AuthorizedClient client = manager.authorize(request);
		if (client == null) {
			throw new IllegalStateException("could not obtain client credentials token for " + registrationId);
		}
		template.header(HttpHeaders.AUTHORIZATION, "Bearer " + client.getAccessToken().getTokenValue());
	}
}
