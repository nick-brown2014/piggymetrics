package com.piggymetrics.statistics.service.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.OAuth2TokenIntrospectionClaimNames;
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException;
import org.springframework.security.oauth2.server.resource.introspection.OAuth2IntrospectionException;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestOperations;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validates opaque access tokens issued by auth-service by calling its user info endpoint
 * (which returns the serialized {@code OAuth2Authentication}).
 *
 * Besides user details, the calling client's id and scopes are extracted from the {@code oauth2Request}
 * part of the response and exposed as {@code client_id}/{@code scope} attributes and {@code SCOPE_*}
 * authorities, which are used in controller security checks.
 */
public class UserInfoTokenIntrospector implements OpaqueTokenIntrospector {

	private static final Logger log = LoggerFactory.getLogger(UserInfoTokenIntrospector.class);

	private static final String[] PRINCIPAL_KEYS = new String[] { "user", "username",
			"userid", "user_id", "login", "id", "name" };

	private static final String AUTHORITIES = "authorities";

	private static final String[] AUTHORITY_KEYS = { "authority", "role", "value" };

	private static final String SCOPE_AUTHORITY_PREFIX = "SCOPE_";

	private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
			new ParameterizedTypeReference<>() {};

	private final String userInfoEndpointUrl;

	private final RestOperations restOperations;

	public UserInfoTokenIntrospector(String userInfoEndpointUrl, RestOperations restOperations) {
		this.userInfoEndpointUrl = userInfoEndpointUrl;
		this.restOperations = restOperations;
	}

	@Override
	public OAuth2AuthenticatedPrincipal introspect(String token) {
		Map<String, Object> map = getMap(token);
		if (map == null || map.isEmpty() || map.containsKey("error")) {
			log.debug("userinfo returned error: {}", map == null ? null : map.get("error"));
			throw new BadOpaqueTokenException("Invalid access token");
		}
		return extractPrincipal(map);
	}

	private OAuth2AuthenticatedPrincipal extractPrincipal(Map<String, Object> map) {
		Map<String, Object> attributes = new HashMap<>(map);

		Map<String, Object> request = getRequest(map);
		Object clientId = request.get("clientId");
		Set<String> scope = getScope(request);

		if (clientId != null) {
			attributes.put(OAuth2TokenIntrospectionClaimNames.CLIENT_ID, clientId);
		}
		attributes.put(OAuth2TokenIntrospectionClaimNames.SCOPE, new ArrayList<>(scope));

		List<GrantedAuthority> authorities = new ArrayList<>(extractAuthorities(map));
		scope.forEach(s -> authorities.add(new SimpleGrantedAuthority(SCOPE_AUTHORITY_PREFIX + s)));

		return new DefaultOAuth2AuthenticatedPrincipal(getPrincipalName(map), attributes, authorities);
	}

	private String getPrincipalName(Map<String, Object> map) {
		for (String key : PRINCIPAL_KEYS) {
			if (map.containsKey(key) && map.get(key) != null) {
				return String.valueOf(map.get(key));
			}
		}
		return "unknown";
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> getRequest(Map<String, Object> map) {
		Object request = map.get("oauth2Request");
		return request instanceof Map ? (Map<String, Object>) request : Collections.emptyMap();
	}

	@SuppressWarnings("unchecked")
	private Set<String> getScope(Map<String, Object> request) {
		Object scope = request.get("scope");
		return scope instanceof Collection ? new LinkedHashSet<>((Collection<String>) scope) : Collections.emptySet();
	}

	private List<GrantedAuthority> extractAuthorities(Map<String, Object> map) {
		String authorities = "ROLE_USER";
		if (map.containsKey(AUTHORITIES)) {
			authorities = asAuthorities(map.get(AUTHORITIES));
		}
		return AuthorityUtils.commaSeparatedStringToAuthorityList(authorities);
	}

	@SuppressWarnings("unchecked")
	private String asAuthorities(Object object) {
		List<Object> authorities = new ArrayList<>();
		if (object instanceof Collection<?> collection) {
			object = collection.toArray(new Object[0]);
		}
		if (object instanceof Object[] array) {
			for (Object value : array) {
				if (value instanceof Map) {
					value = getAuthority((Map<String, Object>) value);
				}
				authorities.add(value);
			}
			return StringUtils.collectionToCommaDelimitedString(authorities);
		}
		return String.valueOf(object);
	}

	private Object getAuthority(Map<String, Object> map) {
		for (String key : AUTHORITY_KEYS) {
			if (map.containsKey(key)) {
				return map.get(key);
			}
		}
		return map;
	}

	private Map<String, Object> getMap(String accessToken) {
		log.debug("Getting user info from: {}", userInfoEndpointUrl);

		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(accessToken);
		headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

		try {
			return restOperations.exchange(userInfoEndpointUrl, HttpMethod.GET, new HttpEntity<>(headers), MAP_TYPE)
					.getBody();
		} catch (HttpClientErrorException ex) {
			log.info("Could not fetch user details: {}, {}", ex.getClass(), ex.getMessage());
			throw new BadOpaqueTokenException("Invalid access token", ex);
		} catch (RestClientException ex) {
			log.info("Could not fetch user details: {}, {}", ex.getClass(), ex.getMessage());
			throw new OAuth2IntrospectionException("Could not fetch user details", ex);
		}
	}
}
