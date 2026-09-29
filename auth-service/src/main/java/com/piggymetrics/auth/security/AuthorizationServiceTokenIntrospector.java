package com.piggymetrics.auth.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;

import java.security.Principal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.Authentication;

/**
 * Introspects opaque access tokens against the in-memory
 * {@link OAuth2AuthorizationService}, exposing the legacy attribute names
 * ({@code client_id}, {@code scope}, {@code grant_type}, ...) expected by
 * downstream consumers.
 */
public class AuthorizationServiceTokenIntrospector implements OpaqueTokenIntrospector {

    private final OAuth2AuthorizationService authorizationService;
    private final RegisteredClientRepository registeredClientRepository;

    public AuthorizationServiceTokenIntrospector(OAuth2AuthorizationService authorizationService,
                                                 RegisteredClientRepository registeredClientRepository) {
        this.authorizationService = authorizationService;
        this.registeredClientRepository = registeredClientRepository;
    }

    @Override
    public OAuth2AuthenticatedPrincipal introspect(String token) {
        OAuth2Authorization authorization =
                authorizationService.findByToken(token, OAuth2TokenType.ACCESS_TOKEN);
        if (authorization == null || !authorization.getAccessToken().isActive()) {
            throw new BadOpaqueTokenException("Provided token isn't active");
        }

        RegisteredClient registeredClient =
                registeredClientRepository.findById(authorization.getRegisteredClientId());
        String clientId = registeredClient != null ? registeredClient.getClientId()
                : authorization.getRegisteredClientId();

        String grantType = authorization.getAuthorizationGrantType() != null
                ? authorization.getAuthorizationGrantType().getValue() : null;

        List<String> scopes = new ArrayList<>(authorization.getAuthorizedScopes());

        List<GrantedAuthority> authorities = new ArrayList<>();
        for (String scope : scopes) {
            authorities.add(new SimpleGrantedAuthority("SCOPE_" + scope));
        }
        Object userPrincipal = authorization.getAttribute(Principal.class.getName());
        if (userPrincipal instanceof Authentication userAuthentication
                && userAuthentication.getAuthorities() != null) {
            authorities.addAll(userAuthentication.getAuthorities());
        }

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("sub", authorization.getPrincipalName());
        attributes.put("client_id", clientId);
        attributes.put("scope", scopes);
        attributes.put("grant_type", grantType);
        attributes.put("active", true);
        Instant issuedAt = authorization.getAccessToken().getToken().getIssuedAt();
        Instant expiresAt = authorization.getAccessToken().getToken().getExpiresAt();
        if (issuedAt != null) {
            attributes.put("iat", issuedAt);
        }
        if (expiresAt != null) {
            attributes.put("exp", expiresAt);
        }

        return new DefaultOAuth2AuthenticatedPrincipal(authorization.getPrincipalName(), attributes, authorities);
    }
}
