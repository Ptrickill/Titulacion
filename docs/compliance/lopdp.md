# Cumplimiento de la LOPDP

Matriz de cumplimiento de la Ley Orgánica de Protección de Datos Personales del Ecuador (LOPDP) en Rehab Elite (MEJ-06, Jira RHE-90). Para cada principio u obligación que aplica al sistema indica la medida que lo cumple y su evidencia: un archivo, una prueba o una captura.

> **Alcance.** Este documento describe lo que hace el sistema. No es una opinión legal. Los números de artículo, los plazos y las obligaciones marcados **a verificar** deben confirmarse con el texto vigente de la ley, su reglamento y la normativa sanitaria antes del despliegue. La sección [Responsabilidades de la clínica](#responsabilidades-de-la-clínica) lista lo que el sistema no puede cumplir por sí solo.

Las capturas están en `docs/screenshots/mej-06-lopdp/`. Las pruebas del backend están en `backend/src/test/java/ec/rehabelite/` y las del frontend junto a cada componente (`*.spec.ts`).

## Matriz de cumplimiento

### 1. Licitud y consentimiento

| Medida | Evidencia |
|---|---|
| **Registrar un paciente exige su consentimiento para el tratamiento de datos personales y de salud** (`DATA_PROCESSING`), porque registrar ya es tratar datos. La recepción lo marca en el paso de revisión, con el texto vigente a la vista. El consentimiento de comunicaciones por correo (`EMAIL_COMMUNICATIONS`) es opcional. | `PatientConsentIntegrationTests.registeringAPatientRequiresTheConsentToProcessTheDataForTheTextInForce`; `person-form-dialog.spec.ts`; captura `03` |
| Cada consentimiento guarda el tipo, la versión del texto, el medio (firma física o portal), la fecha, quién lo registró y su revocación (tabla `patient_consents`, V10). Nunca se borra. Hay un solo consentimiento vigente por paciente y tipo. | `V10__consents_and_data_subject_requests.sql`; `FlywayMigrationTests.v10KeepsOneOpenConsentPerTypeAndClosesRequestsWithAResponse` |
| **Sin consentimiento vigente no hay atención nueva:** ni historia clínica, ni tratamiento, ni sesión, ni acceso al portal (422 con mensaje claro). Los pacientes registrados antes de MEJ-06 quedan con "Consentimiento pendiente" y su ficha lo muestra. | `PatientConsentIntegrationTests.aPatientWithoutConsentGetsNoNewCareNorPortalUntilTheReceptionRecordsIt`; captura `04` |
| **Revocación:** revocar el tratamiento de datos desactiva el portal y bloquea la atención nueva; lo ya registrado se conserva. El paciente revoca las comunicaciones por correo en el portal; el tratamiento de datos lo revoca en recepción, porque le cierra el acceso. | `PatientConsentIntegrationTests.revokingTheDataConsentClosesThePortalAndBlocksNewCareButKeepsTheRecordReadable`; `PortalConsentIntegrationTests`; capturas `05` y `11` |
| Los textos del consentimiento y del aviso de privacidad están versionados en el repositorio (`backend/src/main/resources/legal/*.md`, marcados **BORRADOR**). Una versión nueva no invalida los consentimientos anteriores: la ficha muestra "Versión anterior" y el paciente puede aceptar la nueva en el portal. | `LegalTextCatalog`; `PatientConsentIntegrationTests.aConsentOfAnEarlierVersionStaysValidUntilTheNewOneReplacesIt` |
| Los correos de la cuenta del portal (activación, restablecimiento, confirmación y aviso de cambio de correo) son necesarios para la cuenta y se envían siempre. Las comunicaciones no esenciales, ninguna por ahora, solo se envían con `EMAIL_COMMUNICATIONS` (`MailConsentPolicy`). | `MailDispatcherTests`; `PatientConsentIntegrationTests.portalAccessNeedsOnlyTheDataConsentAndItsAccountEmailsAlwaysLeave` |

### 2. Transparencia: aviso de privacidad

| Medida | Evidencia |
|---|---|
| Página pública `/privacy-notice` con la versión vigente del aviso, enlazada desde el inicio de sesión y desde el portal del paciente. `GET /api/legal/privacy-notice` no pide sesión. | `RoleAccessIntegrationTests.thePrivacyNoticeIsPublicAndTheConsentTextsNeedASession`; `privacy-notice-page.spec.ts`; `login.spec.ts`; capturas `01`, `02` y `11` |

### 3. Finalidad

| Medida | Evidencia |
|---|---|
| Los datos se usan para la atención fisioterapéutica, el seguimiento del tratamiento y la gestión de la clínica, como dice el aviso. No hay usos secundarios: ni publicidad ni perfiles. El correo del paciente solo se usa para su cuenta mientras no acepte comunicaciones. | `legal/privacy-notice.md`; `MailDispatcher` |

### 4. Minimización y proporcionalidad

| Medida | Evidencia |
|---|---|
| Cada rol ve solo lo que necesita (HU-04, MEJ-05). La recepción no ve datos clínicos. Los reportes de la dirección no llevan cédula ni datos de contacto. La vista de asignaciones no lleva diagnóstico. | `RoleAccessIntegrationTests`; `OperationsIntegrationTests`; `ReportTreatmentsIntegrationTests` |
| La exportación de la recepción incluye datos personales, consentimientos y solicitudes, nunca datos clínicos. La exportación clínica la hace el fisioterapeuta e identifica al paciente sin sus datos de contacto. | `PatientDataExportIntegrationTests`; `ClinicalRecordExportIntegrationTests` |
| La dirección solo ve el nombre y la cédula del paciente en una solicitud de eliminación, para confirmarla. | `DataSubjectRequestIntegrationTests`; captura `07` |

### 5. Datos sensibles de salud

| Medida | Evidencia |
|---|---|
| **Los textos clínicos libres se guardan cifrados con AES-256-GCM:** historia clínica, motivos y planes de tratamiento, motivos de cambio de estado, sesiones y resúmenes de IA. También se cifran las tablas `_aud` de Envers. La clave viene de `CLINICAL_DATA_KEY`; sin ella la aplicación no arranca. | `ClinicalDataEncryptionIntegrationTests`; `ClinicalDataCipherTests`; `SecurityPropertiesTests`; `docs/deployment/cifrado.md` |
| La migración V11 cifra los datos existentes. | `FlywayMigrationTests.v11EncryptsTheExistingClinicalTextOfADatabaseAtV9` |
| Solo el fisioterapeuta accede a la historia clínica. Toda lectura o cambio de datos clínicos queda en la auditoría (HU-18). | `ClinicalAuditIntegrationTests` |

### 6. Seguridad e integridad

| Medida | Evidencia |
|---|---|
| Contraseñas con BCrypt, bloqueo tras 5 intentos fallidos, JWT con expiración, cuentas desactivables y enlaces de un solo uso (HU-01, HU-03, MEJ-01). | `AuthControllerIntegrationTests`; `AccountActivationIntegrationTests` |
| **Cierre de sesión por inactividad:** tras 15 minutos sin actividad (`app.session.idle-timeout`), con aviso 2 minutos antes ("Seguir conectado"). El límite máximo sigue siendo la expiración del JWT. | `idle-session.spec.ts`; `app-shell.spec.ts`; `AuthControllerIntegrationTests`; captura `12` |
| **Cabeceras de seguridad** en todas las respuestas del backend: `Content-Security-Policy`, `X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy` y `Permissions-Policy`. `Strict-Transport-Security` solo va por HTTPS. El frontend de producción lleva su CSP en una etiqueta `<meta>`, verificada en el navegador sin violaciones. | `SecurityHeadersIntegrationTests`; `frontend/src/index.prod.html` |
| `audit_logs` es de solo inserción (trigger) y registra usuario, acción, entidad, fecha e IP. Los 403 quedan como `ACCESS_DENIED`. | `AuditLogAppendOnlyIntegrationTests`; `RoleAccessIntegrationTests` |

### 7. Confidencialidad

| Medida | Evidencia |
|---|---|
| **Los logs nunca llevan** nombres, cédulas, correos, teléfonos ni textos clínicos: solo ids y códigos. Una prueba recorre los flujos principales y busca cada valor sintético en la salida. | `LogPrivacyIntegrationTests` |
| Los archivos exportados se envían con `Cache-Control: no-store`. La auditoría guarda solo metadatos, nunca los correos ni los tokens. | `PatientDataExportIntegrationTests`; `ClinicalRecordExportIntegrationTests`; `EmailChangeIntegrationTests` |

### 8. Derechos del titular

| Medida | Evidencia |
|---|---|
| **Registro de solicitudes** (acceso, rectificación, actualización, oposición, eliminación y portabilidad) en la ficha del paciente: fecha de recepción, estado, respuesta, fecha límite y días transcurridos. Cada cambio queda auditado. | `DataSubjectRequestIntegrationTests`; `data-requests-panel.spec.ts`; captura `05` |
| **Plazo de respuesta:** `app.compliance.request-response-days = 15` días calendario desde la recepción (**a verificar**: confirmar el plazo y si se cuenta en días hábiles). | `application.yml` |
| **Acceso y portabilidad:** la recepción exporta los datos personales, los consentimientos y las solicitudes. El fisioterapeuta exporta la historia clínica completa. Ambos en PDF y JSON, y cada descarga se audita como `EXPORT`. | `PatientDataExportIntegrationTests`; `ClinicalRecordExportIntegrationTests`; capturas `06` y `10` |
| **Rectificación y actualización:** la recepción edita los datos personales (HU-06), y la edición queda auditada. | `PatientUpdateIntegrationTests` |
| **Eliminación:** la recepción registra la solicitud y solo la dirección operativa la acepta, escribiendo la cédula del paciente. Aceptarla bloquea al paciente, borra su correo, teléfono y dirección, revoca sus consentimientos y desactiva y anonimiza su cuenta del portal. Se conservan los nombres, la cédula, el número de historia, la fecha de nacimiento, el sexo, la fecha de ingreso y la historia clínica (ver conservación). Es irreversible. | `DataSubjectRequestIntegrationTests.theReceptionNeverAcceptsAnErasureAndTheDirectorConfirmsItWithTheNationalId`; `erasure-dialog.spec.ts`; capturas `07`, `08` y `09` |

### 9. Conservación

| Medida | Evidencia |
|---|---|
| **La historia clínica nunca se borra:** los tratamientos cambian de estado, las cuentas se desactivan y una eliminación aceptada bloquea al paciente sin borrar su historia, que el fisioterapeuta sigue leyendo. El plazo de conservación lo fija la normativa sanitaria del MSP (**a verificar**). | CLAUDE.md, regla de datos 4; `DataSubjectRequestIntegrationTests` |
| Los datos de un paciente bloqueado que no están en la lista de conservación ni en la de borrado se mantienen por ahora: ocupación, nivel de instrucción, seguro, etnia y religión (**a verificar** con la clínica). | `Patient.blockAndClearContactData` |

### 10. Transferencia internacional

| Medida | Evidencia |
|---|---|
| El sistema envía datos a encargados del tratamiento que pueden estar fuera del Ecuador: el alojamiento en Render (backend y base de datos), los correos de la cuenta por Brevo (destinatario, nombre y enlace) y, desde HU-16, Anthropic para los resúmenes de IA. El sistema no puede garantizar las condiciones de esas transferencias: ver responsabilidades de la clínica (**a verificar**: requisitos de la LOPDP para la transferencia internacional). | `docs/deployment/correo.md`; `docs/deployment/base-de-datos.md` |

### 11. Notificación de vulneraciones

| Medida | Evidencia |
|---|---|
| La auditoría (`audit_logs`, accesos denegados, exportaciones) y los logs sin datos personales permiten reconstruir qué pasó y qué datos se vieron afectados. El procedimiento y los plazos de notificación a la autoridad y a los titulares son de la clínica (**a verificar**). | `AuditLogControllerIntegrationTests` |

## Responsabilidades de la clínica

Lo que el sistema no puede cumplir por sí solo:

1. **Delegado de protección de datos:** designarlo si la ley lo exige para el tratamiento de datos de salud (**a verificar**) y publicar su contacto en el aviso de privacidad.
2. **Textos legales:** redactar las versiones definitivas del aviso de privacidad y de los consentimientos, que reemplazan los BORRADOR de `backend/src/main/resources/legal/`, y subir su versión en la cabecera de cada archivo.
3. **Procedimiento de notificación de vulneraciones:** quién detecta, quién decide, a quién se notifica (autoridad de protección de datos y titulares) y en qué plazo (**a verificar**). El sistema aporta la auditoría.
4. **Contratos con los encargados del tratamiento:** Render (alojamiento y base de datos), Brevo (correo) y Anthropic (resúmenes de IA, HU-16). Cada contrato debe cubrir la confidencialidad, la seguridad, la transferencia internacional y la devolución o eliminación de los datos al terminar (**a verificar**).
5. **Custodia de la clave de cifrado:** conservar `CLINICAL_DATA_KEY` y sus anteriores fuera del servidor, con acceso restringido. Si se pierde, los textos clínicos quedan ilegibles (`docs/deployment/cifrado.md`).
6. **Plazos:** confirmar el plazo de respuesta a las solicitudes del titular (hoy 15 días) y el de conservación de la historia clínica.
7. **Registro de actividades de tratamiento** y evaluación de impacto, si la ley los exige para datos de salud (**a verificar**).
8. **Cabeceras en el hosting (TEC-07):** al desplegar el frontend, el servidor estático debe enviar la CSP como cabecera, junto con `frame-ancestors 'none'`, que no vale en una etiqueta `<meta>`.
9. **Capacitación** del personal en el manejo de datos de salud y en el registro de los consentimientos firmados.

## Capturas de MEJ-06

| Captura | Qué muestra |
|---|---|
| `01` | Inicio de sesión con el enlace al aviso de privacidad |
| `02` | Aviso de privacidad público |
| `03` | Registro de un paciente: consentimientos en el paso de revisión |
| `04` | Ficha de un paciente con "Consentimiento pendiente" |
| `05` | Consentimientos y solicitudes del titular en la ficha |
| `06` | "Exportar datos" de la recepción |
| `07`, `08` | Solicitudes de eliminación de la dirección y su confirmación con la cédula |
| `09` | Ficha de un paciente bloqueado |
| `10` | "Exportar historia clínica" del fisioterapeuta |
| `11` | "Mis consentimientos" en el portal del paciente |
| `12` | Aviso de cierre por inactividad |
| `21`–`26` | Las mismas pantallas a 390 px |
