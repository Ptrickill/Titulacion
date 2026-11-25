# Correo de las cuentas en producción (Render)

Configuración del envío de los correos de las cuentas en producción (TEC-16, Jira RHE-88). Son cinco correos:

- activación (MEJ-01);
- restablecimiento de contraseña (MEJ-01);
- confirmación del correo nuevo (MEJ-02);
- aviso al correo anterior (MEJ-02);
- reenvío de invitación.

## Por qué una API HTTP

En desarrollo los correos salen por SMTP a Mailpit (`localhost:1025`). En Render no se cuenta con conexiones SMTP salientes: los servicios del plan gratuito no pueden abrir los puertos SMTP (25, 465 y 587). Por eso en producción los correos salen por la **API HTTP de transacciones de Brevo**: `POST https://api.brevo.com/v3/smtp/email` por HTTPS (443). La restricción de Render se comprueba al desplegar, en el sprint 6.

El backend elige el transporte con `MAIL_TRANSPORT`:

| `MAIL_TRANSPORT` | Adaptador | Uso |
|---|---|---|
| vacío o `smtp` | `SmtpMailGateway` (`JavaMailSender`, `MAIL_HOST`/`MAIL_PORT`) | Desarrollo con Mailpit y pruebas |
| `api` | `BrevoApiMailGateway` (API de Brevo, cabecera `api-key`) | Producción en Render |

Cualquier otro valor, o `api` sin `BREVO_API_KEY`, impide que la aplicación arranque. El contenido de los correos es el mismo con los dos transportes: `AccountMailer` arma el mensaje (texto y HTML) y el adaptador solo lo entrega.

## Variables de entorno en Render

Se definen en el servicio web, en **Environment**. Nunca se escriben en el repositorio.

| Variable | Valor |
|---|---|
| `MAIL_TRANSPORT` | `api` |
| `BREVO_API_KEY` | La clave de la API de Brevo (ver más abajo). Es un secreto. |
| `MAIL_FROM` | El remitente, de un **dominio propio verificado en Brevo**, p. ej. `no-reply@<dominio-de-la-clinica>`. |
| `APP_FRONTEND_URL` | La URL pública del frontend: los enlaces de los correos apuntan a sus páginas `/activar-cuenta`, `/restablecer-contrasena` y `/confirmar-correo`. |

Con `MAIL_TRANSPORT=api` no hacen falta `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME` ni `MAIL_PASSWORD`.

La dirección de la API y los tiempos de espera tienen valores por defecto en `application.yml` y no son variables de entorno:

| Propiedad | Valor por defecto |
|---|---|
| `app.mail.brevo.base-url` | `https://api.brevo.com` |
| `app.mail.brevo.connect-timeout` | `5s` |
| `app.mail.brevo.read-timeout` | `10s` |

## Configuración en Brevo

1. **Cuenta y clave de la API.** En Brevo, **SMTP & API → API Keys**, generar una clave para Rehab Elite y guardarla solo en `BREVO_API_KEY` de Render. No sirve la clave SMTP: es otra credencial.
2. **Dominio del remitente.** En **Senders, Domains & Dedicated IPs → Domains**, agregar el dominio de `MAIL_FROM` y publicar en su DNS los registros que Brevo indique. Copiar los valores exactos del panel de Brevo:
   - el código de verificación de Brevo (registro TXT);
   - **DKIM**, para firmar los mensajes con el dominio;
   - **SPF**, que autoriza a Brevo a enviar en nombre del dominio. Si el dominio ya tiene un registro SPF, se agrega el `include` de Brevo al existente; nunca se crean dos registros SPF;
   - **DMARC**, una política para el dominio. Brevo la pide, y Gmail y Yahoo la exigen a los remitentes masivos.
3. **Remitente.** En **Senders**, registrar la dirección de `MAIL_FROM` con el nombre "Rehab Elite". El backend siempre envía con ese nombre.
4. **IP autorizadas.** Si la cuenta de Brevo restringe la API a ciertas IP (**Security → Authorised IPs**), agregar las IP de salida del servicio de Render, o desactivar la restricción. Si no, Brevo rechaza las peticiones con 401 aunque la clave sea correcta.

**`MAIL_FROM` no puede ser de gmail.com, outlook.com ni de otro proveedor de correo público.** Esos dominios publican políticas DMARC estrictas, y el servidor de Brevo no está autorizado a enviar en su nombre. Los proveedores de los destinatarios rechazarían los correos o los marcarían como spam. Por eso hace falta un dominio propio que la clínica controle.

## Comportamiento ante errores

| Respuesta de Brevo | Efecto |
|---|---|
| 2xx | Correo aceptado |
| 4xx (clave inválida, remitente sin verificar, datos rechazados), 5xx, tiempo de espera agotado, sin conexión | `MailSendException`, el mismo error que un servidor SMTP caído |

Así, cada correo conserva el comportamiento de MEJ-01 y MEJ-02:

- **Activación, invitación, reenvío y enlace al correo nuevo:** 503 "No se pudo enviar el correo de activación. Intente nuevamente en unos minutos." (o el mensaje del enlace de confirmación). No se crea ni cambia nada.
- **Restablecimiento de contraseña y aviso al correo anterior:** salen después del commit, en otro hilo. Un fallo solo queda en el log; la respuesta al usuario no cambia.

## Privacidad (LOPDP)

- **Lo que recibe Brevo:** solo los correos de las cuentas. Cada uno lleva el correo y el nombre del destinatario, el asunto y el texto con el enlace. **Nunca** lleva datos clínicos.
- **Los logs del backend:** nunca escriben el destinatario, el asunto, el contenido, el token ni la respuesta de Brevo, que puede repetir la dirección. Solo el código HTTP (`Brevo API rejected the account email status=401`) o el tipo del fallo de red (`Brevo API not reachable cause=java.net.http.HttpTimeoutException`).
- **Brevo es un encargado del tratamiento:** guarda un registro de los correos enviados (**Transactional → Logs**). Debe constar en la documentación de protección de datos del proyecto.

## Comprobación después del despliegue

1. **Arranque.** El log muestra `Account emails transport=API`. Si falta `BREVO_API_KEY`, la aplicación no arranca y el log dice `MAIL_TRANSPORT=api needs BREVO_API_KEY`.
2. **Envío real.** Desde `/olvide-contrasena`, pedir el restablecimiento de una cuenta de prueba con un correo propio.
3. **Recepción.** El correo llega con el remitente "Rehab Elite <`MAIL_FROM`>" y el enlace a `APP_FRONTEND_URL`. En **Transactional → Logs** de Brevo aparece como entregado.
4. **Autenticación.** En el mensaje recibido, "Mostrar original" (Gmail) debe decir `SPF: PASS`, `DKIM: PASS` y `DMARC: PASS`.
5. **Fallos.** Si el log muestra `status=401`, revisar la clave y las IP autorizadas. Si muestra `status=400`, revisar que el remitente y el dominio estén verificados.

## Desarrollo y pruebas

- **Desarrollo:** sin `MAIL_TRANSPORT` (o vacío), los correos van a Mailpit (`docker compose --profile dev up -d mailpit`, interfaz en `http://localhost:8025`). El perfil `full` de `docker-compose.yml` fija `MAIL_TRANSPORT=smtp`, así que la pila local nunca envía correos reales aunque `.env` diga `api`.
- **Pruebas:** nunca llaman a Brevo.
  - `BrevoApiMailGatewayTests` usa un servidor HTTP local del JDK (`com.sun.net.httpserver.HttpServer`) para el envío correcto, los errores 4xx y 5xx, el tiempo de espera y el host inalcanzable.
  - `AccountLinkSenderApiTransportTests` comprueba que un fallo de la API da el mismo 503 que el SMTP.
  - `MailGatewayConfigTests` comprueba la elección del adaptador.
  - Las pruebas de integración siguen con `RecordingMailSender` (transporte SMTP).
