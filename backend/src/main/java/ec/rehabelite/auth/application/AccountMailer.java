package ec.rehabelite.auth.application;

import ec.rehabelite.auth.domain.User;
import ec.rehabelite.auth.mail.MailCategory;
import ec.rehabelite.auth.mail.MailGateway;
import ec.rehabelite.auth.mail.MailMessage;
import ec.rehabelite.shared.config.AppProperties;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * Emails of the account links (MEJ-01), in Spanish, as plain text and HTML. It only writes them: the
 * {@link MailGateway} chosen by {@code app.mail.transport} delivers them (TEC-16), through the {@link MailDispatcher}.
 * They are all account emails (MEJ-06, {@link MailCategory#ACCOUNT}): a patient gets them whatever its consent to
 * email communications. The link carries the raw token: the
 * message is never logged. A delivery failure surfaces as Spring's {@code MailException}, whatever the transport.
 */
@Component
@RequiredArgsConstructor
class AccountMailer {

    static final String ACTIVATION_SUBJECT = "Active su cuenta de Rehab Elite";
    static final String RESET_SUBJECT = "Restablezca su contraseña de Rehab Elite";
    static final String ACTIVATION_PATH = "/activar-cuenta";
    static final String RESET_PATH = "/restablecer-contrasena";
    static final String EMAIL_CHANGE_SUBJECT = "Confirme su nuevo correo de Rehab Elite";
    static final String EMAIL_CHANGE_NOTICE_SUBJECT = "Se solicitó cambiar el correo de su cuenta de Rehab Elite";
    /** Spanish like the other emailed links: the user sees it in the email and in the address bar (MEJ-02). */
    static final String EMAIL_CHANGE_PATH = "/confirmar-correo";

    private final MailDispatcher dispatcher;
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
        send(user.getEmail(), ACTIVATION_SUBJECT, text, html);
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
        send(user.getEmail(), RESET_SUBJECT, text, html);
    }

    /** MEJ-02: link to confirm the new email of an activated account, sent to that new address. */
    void sendEmailChange(User user, String token) {
        String link = linkTo(EMAIL_CHANGE_PATH, token);
        String validity = describe(properties.tokens().activationTtl());
        String text = """
                Hola, %s:

                Se registró este correo como el nuevo correo de acceso de su cuenta de Rehab Elite. Para confirmarlo, abra este enlace:

                %s

                El enlace vence en %s y solo se puede usar una vez. Hasta que lo confirme, siga iniciando sesión con su correo anterior; su contraseña no cambia. Si no esperaba este correo, ignórelo.

                Rehab Elite
                """.formatted(user.getFullName(), link, validity);
        String html = layout("Confirme su nuevo correo",
                "Se registró este correo como el nuevo correo de acceso de su cuenta de Rehab Elite. "
                        + "Para confirmarlo, use este botón:",
                "Confirmar mi nuevo correo", link,
                "El enlace vence en " + validity + " y solo se puede usar una vez. Hasta que lo confirme, siga "
                        + "iniciando sesión con su correo anterior; su contraseña no cambia. "
                        + "Si no esperaba este correo, ignórelo.",
                user.getFullName());
        send(user.getPendingEmail(), EMAIL_CHANGE_SUBJECT, text, html);
    }

    /**
     * MEJ-02: notice without a link to the previous address of an account whose email is changing. It names neither
     * the new address nor anything else of the account.
     */
    void sendEmailChangeNotice(String fullName, String previousEmail) {
        String text = """
                Hola, %s:

                Se solicitó cambiar el correo de acceso de su cuenta de Rehab Elite. El cambio se completará cuando se confirme desde el nuevo correo; hasta entonces puede seguir iniciando sesión con este.

                Si usted no esperaba este cambio, comuníquese con la administración de la clínica.

                Rehab Elite
                """.formatted(fullName);
        String html = layout("Cambio de correo solicitado",
                "Se solicitó cambiar el correo de acceso de su cuenta de Rehab Elite. El cambio se completará "
                        + "cuando se confirme desde el nuevo correo; hasta entonces puede seguir iniciando sesión "
                        + "con este.",
                null, null,
                "Si usted no esperaba este cambio, comuníquese con la administración de la clínica.", fullName);
        send(previousEmail, EMAIL_CHANGE_NOTICE_SUBJECT, text, html);
    }

    private void send(String to, String subject, String text, String html) {
        dispatcher.send(MailCategory.ACCOUNT, null, new MailMessage(to, subject, text, html));
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

    /** Message with a button to the link, or without one when {@code link} is null (MEJ-02 notice). */
    private static String layout(String title, String intro, String action, String link, String note,
            String fullName) {
        String button = link == null ? "" : button(action, link);
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
                          %s<p style="margin:0;font-size:13px;color:#475569;line-height:1.5">%s</p>
                        </td></tr>
                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """.formatted(HtmlUtils.htmlEscape(title), HtmlUtils.htmlEscape(fullName), HtmlUtils.htmlEscape(intro),
                button, HtmlUtils.htmlEscape(note));
    }

    private static String button(String action, String link) {
        String safeLink = HtmlUtils.htmlEscape(link);
        return """
                <p style="margin:0 0 24px">
                            <a href="%s" style="display:inline-block;background:#14B8A6;color:#0F172A;padding:12px 24px;
                               border-radius:8px;font-weight:bold;text-decoration:none">%s</a>
                          </p>
                          <p style="margin:0 0 8px;font-size:13px;color:#475569">Si el botón no funciona, copie este enlace en el navegador:</p>
                          <p style="margin:0 0 24px;font-size:13px;word-break:break-all"><a href="%s" style="color:#0F766E">%s</a></p>
                          """.formatted(safeLink, HtmlUtils.htmlEscape(action), safeLink, safeLink);
    }


}
