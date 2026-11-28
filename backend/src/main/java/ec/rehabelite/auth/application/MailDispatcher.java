package ec.rehabelite.auth.application;

import ec.rehabelite.auth.MailConsentPolicy;
import ec.rehabelite.auth.mail.MailCategory;
import ec.rehabelite.auth.mail.MailGateway;
import ec.rehabelite.auth.mail.MailMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The only way an email leaves (MEJ-06): an account email always goes to the {@link MailGateway}; a communication
 * goes only when the recipient accepts communications ({@link MailConsentPolicy}), otherwise it is dropped and only
 * the account id is logged.
 */
@Component
class MailDispatcher {

    private static final Logger log = LoggerFactory.getLogger(MailDispatcher.class);

    private final MailGateway mailGateway;
    private final MailConsentPolicy consentPolicy;

    MailDispatcher(MailGateway mailGateway, MailConsentPolicy consentPolicy) {
        this.mailGateway = mailGateway;
        this.consentPolicy = consentPolicy;
    }

    /**
     * Sends the message, or drops a communication without consent.
     *
     * @param recipientUserId the account of the recipient; required for a communication
     * @return whether the message was handed to the gateway
     */
    boolean send(MailCategory category, Long recipientUserId, MailMessage message) {
        if (category == MailCategory.COMMUNICATION
                && (recipientUserId == null || !consentPolicy.allowsCommunications(recipientUserId))) {
            log.info("Communication not sent: the recipient does not accept communications userId={}",
                    recipientUserId);
            return false;
        }
        mailGateway.send(message);
        return true;
    }

}
