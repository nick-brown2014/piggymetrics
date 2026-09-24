package com.piggymetrics.notification.config;

import com.piggymetrics.notification.client.OAuth2ClientCredentialsFeignInterceptor;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

/**
 * OAuth2 client used by Feign calls to {@code account-service}. The scheduled
 * notification jobs run outside any HTTP request, so the authorized-client
 * manager is backed by {@link OAuth2AuthorizedClientService} rather than the
 * request-scoped default manager.
 */
@Configuration
public class OAuth2ClientConfig {

	@Bean
	public OAuth2AuthorizedClientManager authorizedClientManager(ClientRegistrationRepository registrations,
			OAuth2AuthorizedClientService clients) {
		OAuth2AuthorizedClientProvider provider = OAuth2AuthorizedClientProviderBuilder.builder()
				.clientCredentials()
				.build();
		AuthorizedClientServiceOAuth2AuthorizedClientManager manager =
				new AuthorizedClientServiceOAuth2AuthorizedClientManager(registrations, clients);
		manager.setAuthorizedClientProvider(provider);
		return manager;
	}

	@Bean
	public RequestInterceptor oauth2FeignRequestInterceptor(OAuth2AuthorizedClientManager manager) {
		return new OAuth2ClientCredentialsFeignInterceptor(manager, "notification-service");
	}
}
