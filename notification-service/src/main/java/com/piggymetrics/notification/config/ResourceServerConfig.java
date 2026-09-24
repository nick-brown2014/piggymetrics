package com.piggymetrics.notification.config;

import com.piggymetrics.notification.service.security.CustomUserInfoTokenServices;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.security.web.SecurityFilterChain;

/**
 * OAuth2 resource server backed by opaque token introspection against the
 * legacy {@code auth-service} user-info endpoint.
 *
 * @author cdov
 */
@Configuration
@EnableWebSecurity
public class ResourceServerConfig {

	@Bean
	public OpaqueTokenIntrospector opaqueTokenIntrospector(
			@Value("${security.oauth2.resource.user-info-uri}") String userInfoUri,
			@Value("${security.oauth2.client.clientId:notification-service}") String clientId) {
		return new CustomUserInfoTokenServices(userInfoUri, clientId);
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http, OpaqueTokenIntrospector introspector) throws Exception {
		http
				.csrf(csrf -> csrf.disable())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
				.oauth2ResourceServer(oauth2 -> oauth2
						.opaqueToken(opaque -> opaque.introspector(introspector)));
		return http.build();
	}
}
