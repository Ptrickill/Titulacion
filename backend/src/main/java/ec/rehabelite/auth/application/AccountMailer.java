package ec.rehabelite.auth.application;

import ec.rehabelite.auth.domain.User;
import ec.rehabelite.shared.config.AppProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * Emails of the account links (MEJ-01), in Spanish, as plain text and HTML. The link carries the raw token: the
 * message is never logged. A delivery failure surfaces as Spring's {@code MailException}.
 */
@Component
@RequiredArgsConstructor
class AccountMailer {

    static final String ACTIVATION_SUBJECT = "Active su cuenta de Rehab Elite";
    static final String RESET_SUBJECT = "Restablezca su contraseña de Rehab Elite";
    static final String ACTIVATION_PATH = "/activar-cuenta";
    static final String RESET_PATH = "/restablecer-contrasena";

    private static final String SENDER_NAME = "Rehab Elite";

    private final JavaMailSender mailSender;
    private final AppProperties properties;

    void sendActivation(User user, String token) {
        String link = linkTo(ACTIVATION_PATH, token);
        String validity = describe(properties.tokens().activationTtl());
        String text = """
                Hola, %s:

                Se creó una cuenta para usted en Rehab Elite. Para activarla y definir su contraseña, abra este enlace:

                %s

                El enlace vence en %s y solo se puede usar una vez. Si no esperaba este correo, ignórelo.

                Rehab Elite
                """.formatted(user.getFullName(), link, validity);
        String html = layout("Active su cuenta",
                "Se creó una cuenta para usted en Rehab Elite. Para activarla y definir su contraseña, use este botón:",
                "Activar mi cuenta", link,
                "El enlace vence en " + validity + " y solo se puede usar una vez. "
                        + "Si no esperaba este correo, ignórelo.",
                user.getFullName());
        send(user, ACTIVATION_SUBJECT, text, html);
    }

    void sendPasswordReset(User user, String token) {
        String link = linkTo(RESET_PATH, token);
        String validity = describe(properties.tokens().resetTtl());
        String text = """
                Hola, %s:

                Recibimos una solicitud para restablecer la contraseña de su cuenta de Rehab Elite. Para definir una contraseña nueva, abra este enlace:

                %s

                El enlace vence en %s y solo se puede usar una vez. Si usted no lo solicitó, ignore este correo: su contraseña no cambia.

                Rehab Elite
                """.formatted(user.getFullName(), link, validity);
        String html = layout("Restablezca su contraseña",
                "Recibimos una solicitud para restablecer la contraseña de su cuenta de Rehab Elite. "
                        + "Para definir una contraseña nueva, use este botón:",
                "Restablecer contraseña", link,
                "El enlace vence en " + validity + " y solo se puede usar una vez. "
                        + "Si usted no lo solicitó, ignore este correo: su contraseña no cambia.",
                user.getFullName());
        send(user, RESET_SUBJECT, text, html);
    }

    private void send(User user, String subject, String text, String html) {
        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(properties.mail().from(), SENDER_NAME);
            helper.setTo(user.getEmail());
            helper.setSubject(subject);
            helper.setText(text, html);
        } catch (MessagingException | UnsupportedEncodingException ex) {
            throw new MailPreparationException("Could not prepare the account email", ex);
        }
        mailSender.send(message);
    }

    private String linkTo(String path, String token) {
        return properties.frontendBaseUrl() + path + "?token=" + token;
    }

    /** "48 horas", "1 hora", "30 minutos". */
    static String describe(Duration duration) {
        long hours = duration.toHours();
        if (hours > 0 && duration.equals(Duration.ofHours(hours))) {
            return hours == 1 ? "1 hora" : hours + " horas";
        }
        long minutes = Math.max(1, duration.toMinutes());
        return minutes == 1 ? "1 minuto" : minutes + " minutos";
    }

    private static String layout(String title, String intro, String action, String link, String note,
            String fullName) {
        String safeLink = HtmlUtils.htmlEscape(link);
        return """
                <!doctype html>
                <html lang="es">
                <body style="margin:0;padding:24px;background:#F3F4F6;font-family:Arial,Helvetica,sans-serif;color:#0F172A">
                  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0">
                    <tr><td align="center">
                      <table role="presentation" width="560" cellpadding="0" cellspacing="0"
                             style="max-width:560px;background:#FFFFFF;border-radius:16px;overflow:hidden">
                        <tr><td style="background:#0F172A;padding:20px 32px;color:#FFFFFF;font-size:20px;font-weight:bold">
                          Rehab Elite
                        </td></tr>
                        <tr><td style="padding:32px">
                          <h1 style="margin:0 0 16px;font-size:22px">%s</h1>
                          <p style="margin:0 0 16px;font-size:15px;line-height:1.5">Hola, %s:</p>
                          <p style="margin:0 0 24px;font-size:15px;line-height:1.5">%s</p>
                          <p style="margin:0 0 24px">
                            <a href="%s" style="display:inline-block;background:#14B8A6;color:#0F172A;padding:12px 24px;
                               border-radius:8px;font-weight:bold;text-decoration:none">%s</a>
                          </p>
                          <p style="margin:0 0 8px;font-size:13px;color:#475569">Si el botón no funciona, copie este enlace en el navegador:</p>
                          <p style="margin:0 0 24px;font-size:13px;word-break:break-all"><a href="%s" style="color:#0F766E">%s</a></p>
                          <p style="margin:0;font-size:13px;color:#475569;line-height:1.5">%s</p>
                        </td></tr>
                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """.formatted(HtmlUtils.htmlEscape(title), HtmlUtils.htmlEscape(fullName), HtmlUtils.htmlEscape(intro),
                safeLink, HtmlUtils.htmlEscape(action), safeLink, safeLink, HtmlUtils.htmlEscape(note));
    }

}
