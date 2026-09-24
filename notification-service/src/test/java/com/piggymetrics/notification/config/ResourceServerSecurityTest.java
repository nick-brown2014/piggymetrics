package com.piggymetrics.notification.config;

import com.piggymetrics.notification.domain.Frequency;
import com.piggymetrics.notification.domain.NotificationSettings;
import com.piggymetrics.notification.domain.NotificationType;
import com.piggymetrics.notification.domain.Recipient;
import com.piggymetrics.notification.service.RecipientService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class ResourceServerSecurityTest {

	private static final String RECIPIENT_JSON = """
			{"email":"test@test.com","scheduledNotifications":{"REMIND":{"active":true,"frequency":"WEEKLY"},"BACKUP":{"active":false,"frequency":"MONTHLY"}}}
			""";

	@Autowired
	private MockMvc mockMvc;

	@MockBean
	private OpaqueTokenIntrospector introspector;

	@MockBean
	private RecipientService recipientService;

	@Test
	public void shouldRejectRequestWithoutToken() throws Exception {
		mockMvc.perform(get("/recipients/current")).andExpect(status().isUnauthorized());
	}

	@Test
	public void shouldRejectInvalidToken() throws Exception {
		when(introspector.introspect(anyString())).thenThrow(new BadOpaqueTokenException("inactive"));

		mockMvc.perform(get("/recipients/current").header(HttpHeaders.AUTHORIZATION, "Bearer bad"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	public void shouldAllowCurrentForAuthenticatedUser() throws Exception {
		stubPrincipal("test", List.of("server"));
		when(recipientService.findByAccountName("test")).thenReturn(stubRecipient());

		mockMvc.perform(get("/recipients/current").header(HttpHeaders.AUTHORIZATION, "Bearer user-token"))
				.andExpect(status().isOk());
	}

	@Test
	public void shouldAllowPutForAuthenticatedUser() throws Exception {
		stubPrincipal("test", List.of("server"));
		when(recipientService.save(eq("test"), any(Recipient.class))).thenReturn(stubRecipient());

		mockMvc.perform(put("/recipients/current").header(HttpHeaders.AUTHORIZATION, "Bearer user-token")
						.contentType(MediaType.APPLICATION_JSON).content(RECIPIENT_JSON))
				.andExpect(status().isOk());
	}

	@Test
	public void shouldAllowValidTokenWithoutAuthorities() throws Exception {
		stubPrincipal("test", Collections.emptyList());
		when(recipientService.findByAccountName("test")).thenReturn(stubRecipient());

		mockMvc.perform(get("/recipients/current").header(HttpHeaders.AUTHORIZATION, "Bearer user-token"))
				.andExpect(status().isOk());
	}

	private void stubPrincipal(String name, List<String> scopes) {
		when(introspector.introspect(anyString())).thenReturn(new DefaultOAuth2AuthenticatedPrincipal(
				name, Map.of("sub", name, "scope", scopes),
				scopes.stream().<GrantedAuthority>map(s -> new SimpleGrantedAuthority("SCOPE_" + s)).toList()));
	}

	private Recipient stubRecipient() {
		NotificationSettings remind = new NotificationSettings();
		remind.setActive(true);
		remind.setFrequency(Frequency.WEEKLY);

		NotificationSettings backup = new NotificationSettings();
		backup.setActive(false);
		backup.setFrequency(Frequency.MONTHLY);

		Recipient recipient = new Recipient();
		recipient.setAccountName("test");
		recipient.setEmail("test@test.com");
		recipient.setScheduledNotifications(Map.of(
				NotificationType.REMIND, remind,
				NotificationType.BACKUP, backup
		));
		return recipient;
	}
}
