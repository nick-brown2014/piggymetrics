package com.piggymetrics.statistics.config;

import com.piggymetrics.statistics.service.security.UserInfoTokenIntrospector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.client.RestTemplate;

/**
 * @author cdov
 */
@Configuration
public class ResourceServerConfig {

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
						.anyRequest().authenticated())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.csrf(AbstractHttpConfigurer::disable)
				.oauth2ResourceServer(oauth2 -> oauth2.opaqueToken(Customizer.withDefaults()));
		return http.build();
	}

	@Bean
	public OpaqueTokenIntrospector opaqueTokenIntrospector(
			@Value("${spring.security.oauth2.resourceserver.opaquetoken.introspection-uri}") String userInfoUri) {
		return new UserInfoTokenIntrospector(userInfoUri, new RestTemplate());
	}
}
