package com.piggymetrics.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.web.authentication.ClientSecretBasicAuthenticationConverter;
import org.springframework.security.oauth2.server.authorization.web.authentication.ClientSecretPostAuthenticationConverter;
import org.springframework.security.oauth2.server.authorization.web.authentication.DelegatingAuthenticationConverter;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

/**
 * The legacy {@code browser} client sends {@code Authorization: Basic YnJvd3Nlcjo=}
 * ("browser:") — an empty secret. Spring Authorization Server's
 * {@link ClientSecretBasicAuthenticationConverter} rejects empty credentials, so this
 * converter produces a client authentication token with an empty secret for that case
 * and delegates everything else to the standard converters.
 */
public final class LegacyBasicClientAuthenticationConverter implements AuthenticationConverter {

    private final DelegatingAuthenticationConverter delegate = new DelegatingAuthenticationConverter(
            List.of(new ClientSecretBasicAuthenticationConverter(), new ClientSecretPostAuthenticationConverter()));

    private final WebAuthenticationDetailsSource authenticationDetailsSource = new WebAuthenticationDetailsSource();

    @Override
    public OAuth2ClientAuthenticationToken convert(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Basic ")) {
            String decoded;
            try {
                decoded = new String(Base64.getDecoder().decode(header.substring(6).trim()), StandardCharsets.UTF_8);
            } catch (IllegalArgumentException ex) {
                throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_CLIENT);
            }
            int sep = decoded.indexOf(':');
            if (sep >= 0) {
                String clientId = decoded.substring(0, sep);
                String secret = decoded.substring(sep + 1);
                if (secret.isEmpty()) {
                    OAuth2ClientAuthenticationToken token = new OAuth2ClientAuthenticationToken(
                            clientId, ClientAuthenticationMethod.CLIENT_SECRET_BASIC, secret, null);
                    token.setDetails(authenticationDetailsSource.buildDetails(request));
                    return token;
                }
            }
        }
        return (OAuth2ClientAuthenticationToken) delegate.convert(request);
    }
}
