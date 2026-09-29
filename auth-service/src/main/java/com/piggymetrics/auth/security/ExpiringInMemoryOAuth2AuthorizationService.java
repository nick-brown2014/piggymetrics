package com.piggymetrics.auth.security;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.oauth2.core.OAuth2DeviceCode;
import org.springframework.security.oauth2.core.OAuth2UserCode;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.util.Assert;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link OAuth2AuthorizationService} with the same lookup semantics as SAS's
 * {@code InMemoryOAuth2AuthorizationService} plus a scheduled sweep that evicts
 * authorizations whose tokens have all expired — the stock implementation never
 * removes entries and the map grows unbounded with 12h access / 30d refresh TTLs.
 */
public class ExpiringInMemoryOAuth2AuthorizationService implements OAuth2AuthorizationService {

    private final Map<String, OAuth2Authorization> authorizations = new ConcurrentHashMap<>();

    @Override
    public void save(OAuth2Authorization authorization) {
        Assert.notNull(authorization, "authorization cannot be null");
        authorizations.put(authorization.getId(), authorization);
    }

    @Override
    public void remove(OAuth2Authorization authorization) {
        Assert.notNull(authorization, "authorization cannot be null");
        authorizations.remove(authorization.getId(), authorization);
    }

    @Override
    public OAuth2Authorization findById(String id) {
        Assert.hasText(id, "id cannot be empty");
        return authorizations.get(id);
    }

    @Override
    public OAuth2Authorization findByToken(String token, OAuth2TokenType tokenType) {
        Assert.hasText(token, "token cannot be empty");
        for (OAuth2Authorization authorization : authorizations.values()) {
            if (hasToken(authorization, token, tokenType)) {
                return authorization;
            }
        }
        return null;
    }

    @Scheduled(fixedDelayString = "${auth.token-cleanup-interval:PT1H}")
    public void removeExpired() {
        Iterator<OAuth2Authorization> iterator = authorizations.values().iterator();
        while (iterator.hasNext()) {
            if (isExpired(iterator.next())) {
                iterator.remove();
            }
        }
    }

    private static boolean isExpired(OAuth2Authorization authorization) {
        return tokenExpired(authorization.getAccessToken())
                && tokenExpired(authorization.getRefreshToken())
                && tokenExpired(authorization.getToken(OAuth2AuthorizationCode.class))
                && tokenExpired(authorization.getToken(OidcIdToken.class))
                && tokenExpired(authorization.getToken(OAuth2DeviceCode.class))
                && tokenExpired(authorization.getToken(OAuth2UserCode.class));
    }

    private static boolean tokenExpired(OAuth2Authorization.Token<?> token) {
        return token == null || token.isExpired();
    }

    private static boolean hasToken(OAuth2Authorization authorization, String token, OAuth2TokenType tokenType) {
        if (tokenType == null) {
            return matchesState(authorization, token) ||
                    matchesAuthorizationCode(authorization, token) ||
                    matchesAccessToken(authorization, token) ||
                    matchesIdToken(authorization, token) ||
                    matchesRefreshToken(authorization, token) ||
                    matchesDeviceCode(authorization, token) ||
                    matchesUserCode(authorization, token);
        } else if (OAuth2ParameterNames.STATE.equals(tokenType.getValue())) {
            return matchesState(authorization, token);
        } else if (OAuth2ParameterNames.CODE.equals(tokenType.getValue())) {
            return matchesAuthorizationCode(authorization, token);
        } else if (OAuth2TokenType.ACCESS_TOKEN.equals(tokenType)) {
            return matchesAccessToken(authorization, token);
        } else if (OidcParameterNames.ID_TOKEN.equals(tokenType.getValue())) {
            return matchesIdToken(authorization, token);
        } else if (OAuth2TokenType.REFRESH_TOKEN.equals(tokenType)) {
            return matchesRefreshToken(authorization, token);
        } else if (OAuth2ParameterNames.DEVICE_CODE.equals(tokenType.getValue())) {
            return matchesDeviceCode(authorization, token);
        } else if (OAuth2ParameterNames.USER_CODE.equals(tokenType.getValue())) {
            return matchesUserCode(authorization, token);
        }
        return false;
    }

    private static boolean matchesState(OAuth2Authorization authorization, String token) {
        return token.equals(authorization.getAttribute(OAuth2ParameterNames.STATE));
    }

    private static boolean matchesAuthorizationCode(OAuth2Authorization authorization, String token) {
        OAuth2Authorization.Token<OAuth2AuthorizationCode> authorizationCode =
                authorization.getToken(OAuth2AuthorizationCode.class);
        return authorizationCode != null && authorizationCode.getToken().getTokenValue().equals(token);
    }

    private static boolean matchesAccessToken(OAuth2Authorization authorization, String token) {
        OAuth2Authorization.Token<?> accessToken = authorization.getAccessToken();
        return accessToken != null && accessToken.getToken().getTokenValue().equals(token);
    }

    private static boolean matchesIdToken(OAuth2Authorization authorization, String token) {
        OAuth2Authorization.Token<OidcIdToken> idToken = authorization.getToken(OidcIdToken.class);
        return idToken != null && idToken.getToken().getTokenValue().equals(token);
    }

    private static boolean matchesRefreshToken(OAuth2Authorization authorization, String token) {
        OAuth2Authorization.Token<?> refreshToken = authorization.getRefreshToken();
        return refreshToken != null && refreshToken.getToken().getTokenValue().equals(token);
    }

    private static boolean matchesDeviceCode(OAuth2Authorization authorization, String token) {
        OAuth2Authorization.Token<OAuth2DeviceCode> deviceCode = authorization.getToken(OAuth2DeviceCode.class);
        return deviceCode != null && deviceCode.getToken().getTokenValue().equals(token);
    }

    private static boolean matchesUserCode(OAuth2Authorization authorization, String token) {
        OAuth2Authorization.Token<OAuth2UserCode> userCode = authorization.getToken(OAuth2UserCode.class);
        return userCode != null && userCode.getToken().getTokenValue().equals(token);
    }
}
