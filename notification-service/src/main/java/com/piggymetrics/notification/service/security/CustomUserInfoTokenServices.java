package com.piggymetrics.notification.service.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.RequestEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.OAuth2TokenIntrospectionClaimNames;
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@link OpaqueTokenIntrospector} that validates opaque tokens against the legacy
 * (Spring Boot 2 / spring-security-oauth2) {@code auth-service} user-info endpoint.
 *
 * The legacy endpoint returns a serialized {@code OAuth2Authentication}. Besides the user
 * principal, this introspector also extracts {@code oauth2Request.clientId} and
 * {@code oauth2Request.scope} of the calling service and exposes the scopes as
 * {@code SCOPE_} prefixed authorities, so they can be used in controller security checks.
 */
public class CustomUserInfoTokenServices implements OpaqueTokenIntrospector {

	private static final Logger logger = LoggerFactory.getLogger(CustomUserInfoTokenServices.class);

	private static final String[] PRINCIPAL_KEYS = new String[] { "user", "username",
			"userid", "user_id", "login", "id", "name" };

	private static final String SCOPE_AUTHORITY_PREFIX = "SCOPE_";

	private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
			new ParameterizedTypeReference<>() {};

	private final URI userInfoEndpointUri;

	private final String clientId;

	private RestOperations restTemplate = new RestTemplate();

	public CustomUserInfoTokenServices(String userInfoEndpointUrl, String clientId) {
		this.userInfoEndpointUri = URI.create(userInfoEndpointUrl);
		this.clientId = clientId;
	}

	public void setRestTemplate(RestOperations restTemplate) {
		this.restTemplate = restTemplate;
	}

	@Override
	public OAuth2AuthenticatedPrincipal introspect(String token) {
		Map<String, Object> map = getMap(token);
		if (map.containsKey("error")) {
			logger.debug("userinfo returned error: {}", map.get("error"));
			throw new BadOpaqueTokenException("Provided token isn't active");
		}
		return extractPrincipal(map);
	}

	private OAuth2AuthenticatedPrincipal extractPrincipal(Map<String, Object> map) {
		String principal = getPrincipal(map);
		Map<String, Object> request = getRequest(map);
		String requestClientId = (String) request.get("clientId");
		Set<String> scopes = getScopes(request);

		Collection<GrantedAuthority> authorities = new ArrayList<>();
		for (String scope : scopes) {
			authorities.add(new SimpleGrantedAuthority(SCOPE_AUTHORITY_PREFIX + scope));
		}

		Map<String, Object> attributes = new LinkedHashMap<>(map);
		attributes.put(OAuth2TokenIntrospectionClaimNames.ACTIVE, true);
		attributes.put(OAuth2TokenIntrospectionClaimNames.SUB, principal);
		attributes.put(OAuth2TokenIntrospectionClaimNames.SCOPE, new ArrayList<>(scopes));
		if (requestClientId != null) {
			attributes.put(OAuth2TokenIntrospectionClaimNames.CLIENT_ID, requestClientId);
		}

		return new DefaultOAuth2AuthenticatedPrincipal(principal, attributes, authorities);
	}

	private String getPrincipal(Map<String, Object> map) {
		for (String key : PRINCIPAL_KEYS) {
			if (map.containsKey(key)) {
				return String.valueOf(map.get(key));
			}
		}
		return "unknown";
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> getRequest(Map<String, Object> map) {
		Object request = map.get("oauth2Request");
		if (request instanceof Map<?, ?>) {
			return (Map<String, Object>) request;
		}
		return Collections.emptyMap();
	}

	@SuppressWarnings("unchecked")
	private Set<String> getScopes(Map<String, Object> request) {
		Object scope = request.get("scope");
		if (scope instanceof Collection<?>) {
			return new LinkedHashSet<>((Collection<String>) scope);
		}
		if (scope instanceof String) {
			return new LinkedHashSet<>(List.of(((String) scope).split(" ")));
		}
		return Collections.emptySet();
	}

	private Map<String, Object> getMap(String accessToken) {
		logger.debug("Getting user info from: {}", userInfoEndpointUri);
		try {
			RequestEntity<Void> request = RequestEntity.method(HttpMethod.GET, userInfoEndpointUri)
					.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
					.build();
			Map<String, Object> body = restTemplate.exchange(request, MAP_TYPE).getBody();
			return body != null ? body : Collections.singletonMap("error", "Empty user details response");
		}
		catch (Exception ex) {
			logger.info("Could not fetch user details for client {}: {}, {}", clientId,
					ex.getClass(), ex.getMessage());
			return Collections.singletonMap("error", "Could not fetch user details");
		}
	}
}
