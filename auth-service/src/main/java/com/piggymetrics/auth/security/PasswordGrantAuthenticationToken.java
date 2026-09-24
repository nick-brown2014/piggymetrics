package com.piggymetrics.auth.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationGrantAuthenticationToken;

import java.util.Map;
import java.util.Set;

/**
 * Authentication token for the legacy {@code password} grant type, which Spring
 * Authorization Server does not provide out of the box.
 */
public class PasswordGrantAuthenticationToken extends OAuth2AuthorizationGrantAuthenticationToken {

    public static final AuthorizationGrantType PASSWORD = new AuthorizationGrantType("password");

    private final String username;
    private final String password;
    private final Set<String> requestedScopes;

    public PasswordGrantAuthenticationToken(String username, String password, Set<String> requestedScopes,
                                            Authentication clientPrincipal, Map<String, Object> additionalParameters) {
        super(PASSWORD, clientPrincipal, additionalParameters);
        this.username = username;
        this.password = password;
        this.requestedScopes = requestedScopes;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public Set<String> getRequestedScopes() {
        return requestedScopes;
    }
}
