package com.piggymetrics.account.config;

import com.piggymetrics.account.client.OAuth2ClientCredentialsFeignRequestInterceptor;
import com.piggymetrics.account.service.security.CustomUserInfoTokenServices;
import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.security.web.SecurityFilterChain;

/**
 * OAuth2 resource server backed by opaque token introspection against the
 * legacy {@code auth-service} user-info endpoint. This module is also an OAuth2
 * client: its Feign clients obtain client-credentials tokens through
 * {@link OAuth2ClientCredentialsFeignRequestInterceptor}.
 *
 * @author cdov
 */
@Configuration
@EnableWebSecurity
public class ResourceServerConfig {

	@Bean
	public OpaqueTokenIntrospector opaqueTokenIntrospector(
			@Value("${security.oauth2.resource.user-info-uri}") String userInfoUri,
			@Value("${security.oauth2.client.clientId:account-service}") String clientId) {
		return new CustomUserInfoTokenServices(userInfoUri, clientId);
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http, OpaqueTokenIntrospector introspector) throws Exception {
		http
				.csrf(csrf -> csrf.disable())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers("/", "/demo").permitAll()
						.anyRequest().authenticated())
				.oauth2ResourceServer(oauth2 -> oauth2
						.opaqueToken(opaque -> opaque.introspector(introspector)));
		return http.build();
	}

	@Bean
	public OAuth2AuthorizedClientManager authorizedClientManager(ClientRegistrationRepository registrations,
			OAuth2AuthorizedClientService clientService) {
		OAuth2AuthorizedClientProvider provider = OAuth2AuthorizedClientProviderBuilder.builder()
				.clientCredentials().build();
		AuthorizedClientServiceOAuth2AuthorizedClientManager manager =
				new AuthorizedClientServiceOAuth2AuthorizedClientManager(registrations, clientService);
		manager.setAuthorizedClientProvider(provider);
		return manager;
	}

	@Bean
	public RequestInterceptor oauth2FeignRequestInterceptor(OAuth2AuthorizedClientManager manager,
			@Value("${security.oauth2.client.clientId:account-service}") String clientId) {
		return new OAuth2ClientCredentialsFeignRequestInterceptor(manager, clientId);
	}
}
