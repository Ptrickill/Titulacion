package ec.rehabelite.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ec.rehabelite.auth.MailConsentPolicy;
import ec.rehabelite.auth.mail.MailCategory;
import ec.rehabelite.auth.mail.MailGateway;
import ec.rehabelite.auth.mail.MailMessage;
import org.junit.jupiter.api.Test;

/**
 * MEJ-06 (D14): the account emails always leave, whatever the consent to email communications; a communication leaves
 * only for an account that accepts them.
 */
class MailDispatcherTests {

    private static final MailMessage MESSAGE = new MailMessage("paciente@rehabelite.test", "Asunto", "Texto", "<p>Html</p>");

    private final MailGateway gateway = mock(MailGateway.class);
    private final MailConsentPolicy policy = mock(MailConsentPolicy.class);
    private final MailDispatcher dispatcher = new MailDispatcher(gateway, policy);

    @Test
    void anAccountEmailNeverAsksForTheConsent() {
        assertThat(dispatcher.send(MailCategory.ACCOUNT, 7L, MESSAGE)).isTrue();
        assertThat(dispatcher.send(MailCategory.ACCOUNT, null, MESSAGE)).isTrue();

        verify(gateway, times(2)).send(MESSAGE);
        verifyNoInteractions(policy);
    }

    @Test
    void aCommunicationLeavesOnlyWithTheConsent() {
        when(policy.allowsCommunications(7L)).thenReturn(true);
        assertThat(dispatcher.send(MailCategory.COMMUNICATION, 7L, MESSAGE)).isTrue();
        verify(gateway).send(MESSAGE);
    }

    @Test
    void aCommunicationWithoutConsentOrRecipientIsDropped() {
        when(policy.allowsCommunications(8L)).thenReturn(false);

        assertThat(dispatcher.send(MailCategory.COMMUNICATION, 8L, MESSAGE)).isFalse();
        assertThat(dispatcher.send(MailCategory.COMMUNICATION, null, MESSAGE)).isFalse();
        verify(gateway, never()).send(MESSAGE);
    }

}
