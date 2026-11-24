package ec.rehabelite.auth.mail;

import ec.rehabelite.shared.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Chooses the adapter of the account emails from {@code app.mail.transport} (TEC-16): SMTP by default, also when
 * MAIL_TRANSPORT is empty, or Brevo's HTTP API with {@code api}. The choice is a switch rather than
 * {@code @ConditionalOnProperty} because an empty MAIL_TRANSPORT in .env would match no condition.
 */
@Configuration(proxyBeanMethods = false)
class MailGatewayConfig {

    private static final Logger log = LoggerFactory.getLogger(MailGatewayConfig.class);

    @Bean
    MailGateway mailGateway(AppProperties properties, ObjectProvider<JavaMailSender> mailSender) {
        AppProperties.Mail mail = properties.mail();
        log.info("Account emails transport={}", mail.transport());
        return switch (mail.transport()) {
            case SMTP -> new SmtpMailGateway(mailSender.getObject(), mail.from());
            case API -> new BrevoApiMailGateway(mail.brevo().baseUrl(), mail.brevo().apiKey(), mail.from(),
                    mail.brevo().connectTimeout(), mail.brevo().readTimeout());
        };
    }

}
