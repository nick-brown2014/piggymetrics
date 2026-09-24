package com.piggymetrics.notification.service;

import com.google.common.collect.ImmutableList;
import com.piggymetrics.notification.client.AccountServiceClient;
import com.piggymetrics.notification.domain.NotificationType;
import com.piggymetrics.notification.domain.Recipient;
import jakarta.mail.MessagingException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class NotificationServiceImplTest {

	@InjectMocks
	private NotificationServiceImpl notificationService;

	@Mock
	private RecipientService recipientService;

	@Mock
	private AccountServiceClient client;

	@Mock
	private EmailService emailService;

	@Test
	public void shouldSendBackupNotificationsEvenWhenErrorsOccursForSomeRecipients() throws IOException, MessagingException, InterruptedException {

		final String attachment = "json";

		Recipient withError = new Recipient();
		withError.setAccountName("with-error");

		Recipient withNoError = new Recipient();
		withNoError.setAccountName("with-no-error");

		when(client.getAccount(withError.getAccountName())).thenThrow(new RuntimeException());
		when(client.getAccount(withNoError.getAccountName())).thenReturn(attachment);

		when(recipientService.findReadyToNotify(NotificationType.BACKUP)).thenReturn(ImmutableList.of(withNoError, withError));

		notificationService.sendBackupNotifications();

		// TODO test concurrent code in a right way

		verify(emailService, timeout(100)).send(NotificationType.BACKUP, withNoError, attachment);
		verify(recipientService, timeout(100)).markNotified(NotificationType.BACKUP, withNoError);

		verify(recipientService, never()).markNotified(NotificationType.BACKUP, withError);
	}

	@Test
	public void shouldSendRemindNotificationsEvenWhenErrorsOccursForSomeRecipients() throws IOException, MessagingException, InterruptedException {

		final String attachment = "json";

		Recipient withError = new Recipient();
		withError.setAccountName("with-error");

		Recipient withNoError = new Recipient();
		withNoError.setAccountName("with-no-error");

		when(recipientService.findReadyToNotify(NotificationType.REMIND)).thenReturn(ImmutableList.of(withNoError, withError));
		doThrow(new RuntimeException()).when(emailService).send(NotificationType.REMIND, withError, null);

		notificationService.sendRemindNotifications();

		// TODO test concurrent code in a right way

		verify(emailService, timeout(100)).send(NotificationType.REMIND, withNoError, null);
		verify(recipientService, timeout(100)).markNotified(NotificationType.REMIND, withNoError);

		verify(recipientService, never()).markNotified(NotificationType.REMIND, withError);
	}
}
