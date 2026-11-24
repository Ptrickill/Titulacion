package ec.rehabelite.auth.application;

import ec.rehabelite.auth.domain.AccountTokenPurpose;
import ec.rehabelite.auth.domain.User;
import ec.rehabelite.shared.domain.ExternalServiceException;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Issues the links of an account and emails them inside the caller's transaction (MEJ-01, MEJ-02): if a link cannot
 * be sent, the operation fails with 503 and rolls back. The notice to the previous address of a changing email is
 * the exception: it leaves after the commit, on another thread, and a failure is only logged.
 */
@Component
class AccountLinkSender {

    static final String ACTIVATION_MAIL_FAILED =
            "No se pudo enviar el correo de activación. Intente nuevamente en unos minutos.";
    static final String EMAIL_CHANGE_MAIL_FAILED =
            "No se pudo enviar el enlace de confirmación al nuevo correo. Intente nuevamente en unos minutos.";

    private static final Logger log = LoggerFactory.getLogger(AccountLinkSender.class);

    private final AccountTokenService tokenService;
    private final AccountMailer mailer;
    private final TaskExecutor mailExecutor;

    AccountLinkSender(AccountTokenService tokenService, AccountMailer mailer,
            @Qualifier("applicationTaskExecutor") TaskExecutor mailExecutor) {
        this.tokenService = tokenService;
        this.mailer = mailer;
        this.mailExecutor = mailExecutor;
    }

    /** New activation link to the account's email; the previous ones stop working. */
    void sendActivation(User user, Instant now) {
        String token = tokenService.issue(user.getId(), AccountTokenPurpose.ACTIVATION, now);
        try {
            mailer.sendActivation(user, token);
        } catch (MailException ex) {
            throw new ExternalServiceException(ACTIVATION_MAIL_FAILED, ex);
        }
    }

    /** New link to confirm the pending email, sent to that address; the previous ones stop working. */
    void sendEmailChange(User user, Instant now) {
        String token = tokenService.issue(user.getId(), AccountTokenPurpose.EMAIL_CHANGE, now);
        try {
            mailer.sendEmailChange(user, token);
        } catch (MailException ex) {
            throw new ExternalServiceException(EMAIL_CHANGE_MAIL_FAILED, ex);
        }
    }

    /** After the commit, on another thread: the change never depends on the notice. */
    void sendEmailChangeNoticeAfterCommit(Long userId, String fullName, String previousEmail) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                mailExecutor.execute(() -> {
                    try {
                        mailer.sendEmailChangeNotice(fullName, previousEmail);
                        log.info("Email change notice sent to the previous address userId={}", userId);
                    } catch (MailException ex) {
                        log.warn("Email change notice not sent userId={} cause={}", userId, ex.getClass().getName());
                    }
                });
            }
        });
    }

}
