package com.piggymetrics.auth.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders the legacy spring-security-oauth2 shape of {@code GET /users/current}
 * that the downstream services' user-info parsing depends on.
 */
public final class LegacyAuthenticationView {

    private LegacyAuthenticationView() {
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> of(Authentication authentication) {
        String name = authentication.getName();

        List<String> authorities = new ArrayList<>();
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            authorities.add(authority.getAuthority());
        }

        String clientId = null;
        List<String> scopes = new ArrayList<>();
        String grantType = null;

        Object principal = authentication.getPrincipal();
        if (principal instanceof OAuth2AuthenticatedPrincipal oauth2Principal) {
            Map<String, Object> attributes = oauth2Principal.getAttributes();
            clientId = (String) attributes.get("client_id");
            Object scopeAttr = attributes.get("scope");
            if (scopeAttr instanceof List<?> scopeList) {
                for (Object scope : scopeList) {
                    scopes.add(String.valueOf(scope));
                }
            }
            grantType = (String) attributes.get("grant_type");
        }

        Map<String, Object> oauth2Request = new HashMap<>();
        oauth2Request.put("clientId", clientId);
        oauth2Request.put("scope", scopes);
        oauth2Request.put("grantType", grantType);

        Map<String, Object> view = new HashMap<>();
        view.put("name", name);
        view.put("user", name);
        view.put("principal", name);
        view.put("authorities", authorities);
        view.put("clientOnly", "client_credentials".equals(grantType));
        view.put("oauth2Request", oauth2Request);
        return view;
    }
}
