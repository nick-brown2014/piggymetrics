package com.piggymetrics.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Converts {@code grant_type=password} token requests into a
 * {@link PasswordGrantAuthenticationToken}.
 */
public final class PasswordGrantAuthenticationConverter implements AuthenticationConverter {

    @Override
    public Authentication convert(HttpServletRequest request) {
        MultiValueMap<String, String> parameters = parameters(request);

        String grantType = parameters.getFirst("grant_type");
        if (!PasswordGrantAuthenticationToken.PASSWORD.getValue().equals(grantType)) {
            return null;
        }

        String username = parameters.getFirst("username");
        if (!StringUtils.hasText(username) || parameters.get("username").size() != 1) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_REQUEST);
        }
        String password = parameters.getFirst("password");
        if (!StringUtils.hasText(password) || parameters.get("password").size() != 1) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_REQUEST);
        }

        Set<String> requestedScopes = null;
        String scope = parameters.getFirst("scope");
        if (StringUtils.hasText(scope)) {
            if (parameters.get("scope").size() != 1) {
                throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_REQUEST);
            }
            requestedScopes = new HashSet<>(Arrays.asList(StringUtils.delimitedListToStringArray(scope, " ")));
        }

        Authentication clientPrincipal = SecurityContextHolder.getContext().getAuthentication();
        if (!(clientPrincipal instanceof OAuth2ClientAuthenticationToken)) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_CLIENT);
        }

        Map<String, Object> additionalParameters = new HashMap<>();
        parameters.forEach((key, values) -> {
            if (!key.equals("grant_type") && !key.equals("username") && !key.equals("password") && !key.equals("scope")) {
                additionalParameters.put(key, values.get(0));
            }
        });

        return new PasswordGrantAuthenticationToken(username, password, requestedScopes,
                clientPrincipal, additionalParameters);
    }

    private static MultiValueMap<String, String> parameters(HttpServletRequest request) {
        Map<String, String[]> parameterMap = request.getParameterMap();
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>(parameterMap.size());
        parameterMap.forEach((key, values) -> {
            for (String value : values) {
                parameters.add(key, value);
            }
        });
        return parameters;
    }
}
