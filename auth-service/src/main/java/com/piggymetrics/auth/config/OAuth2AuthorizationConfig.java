package com.piggymetrics.auth.config;

import com.piggymetrics.auth.security.AuthorizationServiceTokenIntrospector;
import com.piggymetrics.auth.security.ExpiringInMemoryOAuth2AuthorizationService;
import com.piggymetrics.auth.security.LegacyBasicClientAuthenticationConverter;
import com.piggymetrics.auth.security.PasswordGrantAuthenticationConverter;
import com.piggymetrics.auth.security.PasswordGrantAuthenticationProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.server.authorization.token.DelegatingOAuth2TokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2AccessTokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2RefreshTokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.security.authentication.AuthenticationProvider;

import java.time.Duration;

/**
 * @author cdov
 */
@Configuration
@EnableScheduling
public class OAuth2AuthorizationConfig {

    private static final String NOOP_PASSWORD_ENCODE = "{noop}";

    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerSecurityFilterChain(
            HttpSecurity http, AuthenticationManager userAuthenticationManager,
            OAuth2AuthorizationService authorizationService,
            OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator) throws Exception {

        OAuth2AuthorizationServerConfigurer authorizationServerConfigurer =
                new OAuth2AuthorizationServerConfigurer();

        AuthenticationConverter passwordGrantConverter = new PasswordGrantAuthenticationConverter();
        AuthenticationProvider passwordGrantProvider = new PasswordGrantAuthenticationProvider(
                userAuthenticationManager, authorizationService, tokenGenerator);
        AuthenticationConverter legacyBasicConverter = new LegacyBasicClientAuthenticationConverter();

        http
                .securityMatcher(authorizationServerConfigurer.getEndpointsMatcher())
                .with(authorizationServerConfigurer, configurer -> configurer
                        .tokenEndpoint(tokenEndpoint -> tokenEndpoint
                                .accessTokenRequestConverter(passwordGrantConverter)
                                .authenticationProvider(passwordGrantProvider))
                        .clientAuthentication(clientAuthentication -> clientAuthentication
                                .authenticationConverter(legacyBasicConverter)))
                .csrf(csrf -> csrf.ignoringRequestMatchers(authorizationServerConfigurer.getEndpointsMatcher()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        return http.build();
    }

    @Bean
    public RegisteredClientRepository registeredClientRepository(Environment env) {
        TokenSettings tokenSettings = TokenSettings.builder()
                .accessTokenFormat(OAuth2TokenFormat.REFERENCE)
                .accessTokenTimeToLive(Duration.ofHours(12))
                .refreshTokenTimeToLive(Duration.ofDays(30))
                .reuseRefreshTokens(false)
                .build();
        ClientSettings clientSettings = ClientSettings.builder()
                .requireAuthorizationConsent(false)
                .requireProofKey(false)
                .build();

        RegisteredClient browser = RegisteredClient.withId("browser")
                .clientId("browser")
                .clientSecret(NOOP_PASSWORD_ENCODE)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(new AuthorizationGrantType("password"))
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .scope("ui")
                .tokenSettings(tokenSettings)
                .clientSettings(clientSettings)
                .build();

        RegisteredClient accountService = serviceClient("account-service",
                env.getProperty("ACCOUNT_SERVICE_PASSWORD"), tokenSettings, clientSettings);
        RegisteredClient statisticsService = serviceClient("statistics-service",
                env.getProperty("STATISTICS_SERVICE_PASSWORD"), tokenSettings, clientSettings);
        RegisteredClient notificationService = serviceClient("notification-service",
                env.getProperty("NOTIFICATION_SERVICE_PASSWORD"), tokenSettings, clientSettings);

        return new InMemoryRegisteredClientRepository(
                browser, accountService, statisticsService, notificationService);
    }

    private static RegisteredClient serviceClient(String clientId, String secret,
                                                  TokenSettings tokenSettings, ClientSettings clientSettings) {
        return RegisteredClient.withId(clientId)
                .clientId(clientId)
                .clientSecret(NOOP_PASSWORD_ENCODE + (secret != null ? secret : ""))
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .scope("server")
                .tokenSettings(tokenSettings)
                .clientSettings(clientSettings)
                .build();
    }

    @Bean
    public OAuth2AuthorizationService authorizationService() {
        return new ExpiringInMemoryOAuth2AuthorizationService();
    }

    @Bean
    public OAuth2TokenGenerator<?> tokenGenerator() {
        // Opaque (reference) tokens only — no JwtGenerator registered.
        return new DelegatingOAuth2TokenGenerator(
                new OAuth2AccessTokenGenerator(), new OAuth2RefreshTokenGenerator());
    }

    @Bean
    public AuthorizationServerSettings authorizationServerSettings() {
        return AuthorizationServerSettings.builder()
                .tokenEndpoint("/oauth/token")
                .tokenIntrospectionEndpoint("/oauth/check_token")
                .tokenRevocationEndpoint("/oauth/revoke")
                .build();
    }

    @Bean
    public OpaqueTokenIntrospector tokenIntrospector(OAuth2AuthorizationService authorizationService,
                                                   RegisteredClientRepository registeredClientRepository) {
        return new AuthorizationServiceTokenIntrospector(authorizationService, registeredClientRepository);
    }
}
