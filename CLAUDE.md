# CLAUDE.md — Rehab Elite

Reglas permanentes del proyecto. Leer antes de cualquier cambio.

## Proyecto

Rehab Elite es un sistema web de gestión clínica y seguimiento terapéutico para una clínica de fisioterapia en Quito (proyecto Capstone).

### Roles

| Rol (`users.role`) | Uso principal |
|---|---|
| `ADMINISTRATOR` | Recepción (MEJ-05): registro de pacientes y su acceso al portal |
| `PHYSIOTHERAPIST` | Pacientes, historial, tratamientos, sesiones, resúmenes IA |
| `PATIENT` | Portal de solo lectura de su propio tratamiento |
| `OPERATIONS_DIRECTOR` | Reportes e indicadores; gestión del personal y de sus cuentas, horarios y asignaciones (MEJ-04, MEJ-05) |

### Fuera de alcance

Facturación, pagos, agenda médica, telemedicina, app móvil, integración con sistemas externos y diagnóstico automático. No implementar nada de esto aunque parezca útil.

## Regla de idioma

**En inglés:** carpetas, paquetes, clases, interfaces, métodos, variables, constantes, enums, tablas, columnas, índices, constraints, endpoints REST, rutas y componentes de Angular, nombres de archivos, variables de entorno, logs, comentarios de código/Javadoc y mensajes de commit.

**Excepción acordada (MEJ-01, MEJ-02):** las rutas públicas de Angular de los enlaces que llegan por correo van en español, porque el usuario las ve en el correo y en la barra: `/activar-cuenta`, `/restablecer-contrasena`, `/olvide-contrasena` y `/confirmar-correo` (MEJ-02). Sus componentes y su código siguen en inglés (`SetPasswordPage`, `ForgotPasswordPage`, `ConfirmEmailPage`). No crear otras rutas en español.

**En español:** textos visibles para el usuario (etiquetas, botones, mensajes de la interfaz, `title`/`detail` de `ProblemDetail`, mensajes de Bean Validation), datos de catálogo mostrados al usuario (descripciones CIE-10) y los documentos de `docs/`.

Los comentarios `# TODO` de `.env` / `.env.example` son instrucciones para el desarrollador y se mantienen en español.

## Glosario (español → inglés)

| Español | Inglés (código) |
|---|---|
| paciente | patient |
| historial clínico | clinical record |
| tratamiento | treatment |
| sesión terapéutica | therapy session |
| fisioterapeuta | physiotherapist |
| fisioterapeuta responsable | responsible (physiotherapist) / responsible assignment |
| personal (fisioterapeutas, administración, dirección) | staff (`staff_records`, `StaffRecord`) |
| recepción | reception (rol `ADMINISTRATOR`, MEJ-05) |
| dirección operativa | operations director |
| administrador | administrator |
| escala EVA | pain score (`pain_score`, `initial_pain_score`) |
| cédula | national ID (`national_id`) |
| número de historia clínica | medical record number (`medical_record_number`) |
| nombres / apellidos | first name / last name (guardan ambos nombres / ambos apellidos) |
| motivo de consulta | chief complaint |
| enfermedad actual | current illness |
| médico remitente | referring physician |
| exámenes complementarios | complementary tests |
| zona del dolor | pain location |
| número correlativo de sesión | sequence number |
| estado de evolución | progress status |
| diagnóstico CIE-10 | ICD-10 diagnosis |
| resumen IA | AI summary |
| validar resumen | review / approve summary |
| auditoría | audit log |
| intentos fallidos / bloqueado hasta | failed attempts / locked until |

## Stack

- **Backend:** Java 21, Spring Boot 4, Spring Security con JWT HS256 (OAuth2 Resource Server), Spring Data JPA, Flyway, Hibernate Envers (HU-18), Spring Modulith, Bean Validation, MapStruct, springdoc-openapi, Apache PDFBox 3.0.8 (HU-15, licencia Apache 2.0), Spring Mail (MEJ-01, correos de las cuentas) y, en producción, la API HTTP de transacciones de Brevo con `RestClient` (TEC-16).
- **Frontend:** Angular 22 con Angular Material.
- **Base de datos:** PostgreSQL 16.
- **IA:** API de Claude (Sprint 5, módulo `aisummary`).
- **Pruebas:** JUnit 5, Mockito, Testcontainers (backend); Vitest y Playwright (frontend).

## Arquitectura backend: monolito modular

Paquete base `ec.rehabelite`. Módulos (Spring Modulith):

| Módulo | Responsabilidad |
|---|---|
| `auth` | Login JWT, usuarios, roles, bloqueo, configuración de seguridad |
| `patients` | Datos personales de pacientes |
| `clinicalrecords` | Historial clínico, tratamientos, CIE-10, responsables, cambios de estado |
| `sessions` | Sesiones terapéuticas |
| `aisummary` | Resúmenes con IA y su revisión |
| `portal` | Portal del paciente (solo lectura) |
| `reports` | Reportes para la dirección operativa |
| `audit` | Registro de auditoría |
| `catalogs` | Datos de referencia (MEJ-03): división político-administrativa del Ecuador (INEC), y la dirección de pacientes y personal (`AddressRequest`, `AddressView`, `AddressFormatter`) |
| `staff` | Registro del personal (MEJ-02); listas del Registro de Pacientes de la recepción y de la Gestión de Personal de la dirección (MEJ-05); horario semanal de los fisioterapeutas (MEJ-04) |
| `assignments` | Horarios y asignaciones de la dirección operativa (MEJ-04): tablero semanal con la carga, disponibilidad y reasignación del responsable de los tratamientos activos. Sin tablas propias |
| `shared` | Módulo abierto: `config` (`AppProperties`, locale, reloj, validación), `domain` (excepciones base, `ClinicCalendar`, `NameCase` y, desde MEJ-02, `NationalId`, `PhoneNumber`, `PostalAddress` y `Sex`), `web` (`GlobalExceptionHandler`, `PageResponse`, `@ValidNationalId` y `@ValidPhone`) |

Cada módulo tiene:

- `web/` — controller REST + DTO como `record`.
- `application/` — servicios (casos de uso).
- `domain/` — entidades JPA + repositorios.

Reglas:

- Un módulo usa otro **solo** por las interfaces públicas del paquete raíz del módulo (p. ej. `ec.rehabelite.patients.PatientLookup`). Nunca importar `web`, `application` ni `domain` de otro módulo.
- `ModularityTests` debe pasar siempre.
- APIs públicas disponibles:
  - `ec.rehabelite.auth.CurrentUserProvider`: usuario autenticado (`CurrentUser`: id, email, fullName, `Role`).
  - `ec.rehabelite.audit.AuditLogService`: `record(AuditAction, entityType, entityId)` toma el usuario del token y la IP de la petición. `recordAll(acción, entidad, ids)` (HU-18) registra una entrada por id, por ejemplo los historiales de una lista.
    - `recordAll` inserta todas las entradas con **un solo `INSERT`** (`AuditLogRepository.insertAll`, SQL nativo con `unnest`), sea cual sea el número de ids. Un `saveAll` haría un `INSERT` por entrada: el id de `audit_logs` es `IDENTITY` y Hibernate no agrupa esos `INSERT` en lote.
  - `audit` no depende de `auth`, para evitar un ciclo: `auth` registra el `LOGIN` con `recordFor(userId, ...)`.
  - `ec.rehabelite.audit.AuditUserDirectory` (HU-18): `fullNames(ids)` y `users()` (`AuditUser`: id y nombre) para la auditoría. La declara `audit` y la implementa `auth` (`AuditUserDirectoryService`), que ya depende de `audit`; así `audit` nunca depende de `auth`.
  - `ec.rehabelite.auth.AccountProvisioning` (MEJ-02): la única forma de crear o cambiar una cuenta desde otro módulo, siempre para una persona registrada y con su correo y sus nombres. Corre en la transacción de quien llama.
    - `create(email, fullName, role)` crea la cuenta pendiente y audita `CREATE User`; `sendInvitation(userId)` envía la activación de MEJ-01 y audita `INVITATION_SENT User`. Quien llama vincula la cuenta y llama `sendInvitation` **al final**: si el correo falla, 503 y no queda nada.
    - `rename(userId, fullName)` mantiene `users.full_name` igual a "nombres apellidos" del registro.
    - `changeEmail(userId, correo)`: ver "Cambio de correo".
    - `summary(userId)`, `summaries(ids)` (una consulta), `accountsUsingEmail(correo)` (correo o `pending_email`) e `idsWithRole(rol)` devuelven `AccountSummary` (id, rol, `AccountStatus` y `emailChangePending`), nunca el correo.
    - `AccountStatus` (`PENDING_ACTIVATION`, `ACTIVE`, `INACTIVE`) está en el paquete raíz de `auth`.
  - `ec.rehabelite.auth.StaffAccountCreated(userId, email, fullName, role)` (MEJ-02): evento que publica `DirectorAccountInitializer` al crear la dirección operativa inicial de `.env` (MEJ-05; antes, el administrador inicial). `staff` lo escucha de forma **síncrona** (`@EventListener`, no `@ApplicationModuleListener`) en la misma transacción y crea su registro con datos por completar. Reemplaza a `PortalAccountCreated` (HU-02), que se eliminó junto con `POST /api/users`.
  - `ec.rehabelite.patients.RegisteredIdentities` (MEJ-02): `staffWithNationalId(cédula)` y `staffWithEmail(correo)`. La declara `patients` y la implementa `staff` (`StaffIdentityService`), como `AuditUserDirectory`: así `patients` comprueba la unicidad sin depender de `staff`.
  - `ec.rehabelite.patients.PatientDirectory` (HU-07):
    - `find(id)` y `search(q, pageable)` devuelven `PatientCard` (id, cédula, número de historia, nombres, apellidos, fecha de nacimiento y `ageOn(fecha)`).
    - `findProfile(id)` (HU-09) devuelve `PatientProfile`: además, sexo, contacto, ocupación, instrucción, seguro y fecha de ingreso. Sin etnia ni religión. Desde MEJ-03, `address` es la dirección compuesta (con los nombres del catálogo del INEC; en un paciente antiguo, su texto anterior), `addressComplete` dice si tiene parroquia y calles, y `hasHealthInsurance` es Sí/No.
    - `findByPortalUser(userId)` (HU-12) devuelve el `PatientCard` del paciente vinculado a una cuenta de portal (`patients.portal_user_id`), o vacío si la cuenta no tiene paciente.
    - `fullNames(ids)` (HU-14) devuelve solo "nombres apellidos" de cada paciente, en una sentencia que no lee otros datos personales (proyección `PatientName`). Es lo único que los reportes saben del paciente (minimización de datos, LOPDP).
    - `findIdByNationalId(cédula)`, `idsWithEmail(correo)` y `registry(q, offset, limit)` (MEJ-02) sirven al Registro de Pacientes de la recepción (MEJ-05): el último devuelve `PatientRegistrySlice` (`PatientRegistryEntry`: identificación, contacto, `needsCompletion` y `portalUserId`, y el total) desde cualquier desplazamiento, con la búsqueda y el orden de `search`.
    - Es la única forma en que otros módulos (`clinicalrecords`, `sessions`, `portal`, `staff`, ...) consultan pacientes.
    - `patients` nunca depende de `clinicalrecords` ni de `staff`.
  - `ec.rehabelite.auth.StaffDirectory` (HU-08):
    - `findActivePhysiotherapist(id)`, `activePhysiotherapists()` y `fullNames(ids)` devuelven `StaffMember` (id y nombre) o los nombres, también de cuentas desactivadas.
    - `physiotherapists()` (HU-14) devuelve todos los fisioterapeutas, activos o no, ordenados por nombre: una cuenta desactivada puede seguir como responsable de tratamientos y autora de sesiones.
    - Es la única forma en que otros módulos consultan cuentas del personal; `auth` nunca depende de ellos.
  - `ec.rehabelite.clinicalrecords.TreatmentQueryPort` (HU-10):
    - `find(id)` devuelve `TreatmentSummary`: id, paciente, `active`, fecha de inicio y, desde HU-11, el punto de partida (`initialPainScore`, `plannedSessions` y el diagnóstico CIE-10, código y descripción). Nada más del contenido clínico (motivo, enfermedad, objetivo...) y nunca la entidad.
    - `find` usa `TreatmentRepository.findSummaryById` (`@EntityGraph` de historial y diagnóstico): una sola sentencia.
    - `lockForSessionRegistration(id)` devuelve lo mismo, pero bloquea la fila del tratamiento (`PESSIMISTIC_WRITE`) hasta que termina la transacción de quien llama. Exige una transacción abierta (`MANDATORY`).
    - `findByPatient(patientId)` (HU-12) devuelve los tratamientos del paciente como `PatientTreatment`: id, estado (`status` y `active`), fecha de inicio, EVA inicial, sesiones planificadas, diagnóstico CIE-10 e id del responsable vigente (o `null`). El activo va primero y luego los demás, del más reciente al más antiguo. Son dos sentencias: los tratamientos (`TreatmentRepository.findByPatientId`) y sus responsables vigentes en una consulta `IN`.
    - Reportes (HU-14, HU-15), con `TreatmentFilter(status, responsibleId, from, to)` (todos opcionales; `TreatmentFilter.ALL` no filtra; el constructor de dos argumentos no tiene periodo). El responsable es siempre el vigente y el estado, el actual:
      - **Periodo del tratamiento (HU-15):** con `from` o `to` (días de Quito, incluidos), un tratamiento entra si estuvo en curso algún día del rango: empezó el día `to` o antes y, además, sigue activo, empezó el día `from` o después, o cerró el día `from` o después. Cierra el día (en Quito) de su último cambio de estado (`treatment_status_changes.changed_at`, comparado con el primer instante de `from` en Quito).
      - `countByStatus(filtro)` devuelve los tratamientos por estado, con `ACTIVE`, `COMPLETED` y `ABANDONED` siempre presentes (0 si no hay). Una sentencia (`GROUP BY`).
      - `idsWithStatus(estado)` devuelve los ids de los tratamientos en ese estado, o de todos si es `null`. Una sentencia, sin periodo.
      - `search(filtro, pageable)` devuelve una página de `PatientTreatment`, de la fecha de inicio más reciente a la más antigua, con el responsable vigente. Siempre dos sentencias, la página y el total, aunque la página no esté llena.
      - `list(filtro)` (HU-15, el PDF) devuelve todos los tratamientos del filtro, en el orden y con los datos de `search`. Una sentencia.
      - Todas usan el fragmento `TreatmentReportQueries` (`TreatmentReportQueriesImpl`), que arma el JPQL solo con los criterios enviados, porque PostgreSQL no acepta `:param IS NULL` con un nulo sin tipo. Un estado que no existe lanza `IllegalArgumentException`.
    - Asignaciones (MEJ-04):
      - `activeLoadByResponsible()` devuelve la carga de cada fisioterapeuta: sus tratamientos `ACTIVE` como responsable vigente, en un solo `GROUP BY` (`TreatmentRepository.countByCurrentResponsible`). Un fisioterapeuta sin tratamientos activos no es clave.
      - `activeTreatments(responsableIds, pageable)` devuelve una página de `ActiveTreatment` (id, paciente, fecha de inicio y responsable vigente; **ningún contenido clínico, ni el diagnóstico**), del inicio más reciente al más antiguo. Con ids, solo los de esos responsables (una colección vacía da una página vacía); `null`, todos los activos. La página y el total (Spring Data omite el total si la primera página no se llena).
    - Es la única forma en que otros módulos (`sessions`, `portal`, `reports`, `assignments`, ...) consultan tratamientos; `clinicalrecords` nunca depende de `sessions`.
  - `ec.rehabelite.clinicalrecords.TreatmentResponsibles` (MEJ-04): `reassign(tratamiento, fisioterapeuta)` cambia el responsable con **la misma lógica de HU-08** (`TreatmentService.reassignResponsible`, que la implementa): bloqueo del tratamiento, solo tratamientos activos y fisioterapeutas activos (422), `UPDATE Treatment` y revisión de Envers del usuario autenticado; elegir al vigente no cambia nada. Devuelve el `ActiveTreatment`. La usa la dirección operativa desde `assignments`.
  - `ec.rehabelite.staff.PhysiotherapistSchedules` (MEJ-04, la implementa `StaffScheduleService`): `all()` devuelve todos los fisioterapeutas con registro, activos o no, con sus franjas (`PhysiotherapistSchedule`: registro, cuenta, nombres y `ScheduleSlot`), por apellidos y nombres con intercalación española, en tres sentencias; `workingDays(cuentas)` devuelve los días con franjas de cada cuenta, en una. Si una cuenta está activa lo dice `StaffDirectory`, no `staff`. `ScheduleSlot.covers(día, desde, hasta)` dice si una franja cubre todo el rango.
  - `ec.rehabelite.sessions.TherapySessionQueryPort` (HU-12, HU-13):
    - `chronological(treatmentId)` devuelve las sesiones del tratamiento en orden cronológico (fecha, hora y correlativo) como `SessionSummary`: id, correlativo, fecha, hora, estado, `completed` y EVA.
    - `patientView(treatmentId)` (HU-13) devuelve las mismas sesiones, en el mismo orden, como `PatientSessionView`: correlativo, fecha, hora, tipo, id del responsable, EVA, actividades realizadas, estado e indicaciones para el paciente.
    - `latestPatientInstruction(treatmentId)` (HU-13) devuelve la sesión más reciente (fecha, hora y correlativo) que tiene indicaciones para el paciente, **sea cual sea su estado**, o vacío si ninguna las tiene. Es una sentencia que lee una sola fila (`TherapySessionRepository.findLatestWithPatientInstructions`).
    - **Ningún tipo público de `sessions` lleva las observaciones clínicas ni la evolución** (`clinicalObservations`, `progressStatus`), que son notas internas: así el portal no puede mostrarlas por error. Lo comprueba `SessionsPublicApiPrivacyTests`, que recorre los tipos del paquete raíz. Si otro módulo necesita un dato nuevo de la sesión, se agrega a un tipo público o se crea otro, nunca se expone la entidad.
    - `completedCounts(treatmentIds)` devuelve las sesiones `COMPLETED` de cada tratamiento en una sola consulta (`GROUP BY`); un tratamiento sin sesiones realizadas trae 0.
    - `countsByResponsibleAndStatus(treatmentIds, responsibleId, from, to)` (HU-14, HU-15) cuenta las sesiones de esos tratamientos por fisioterapeuta que las registró (`therapy_sessions.responsible_id`) y por estado, como `SessionCount` (responsable, estado y total). Con `responsibleId`, solo las que registró ese fisioterapeuta, sea quien sea el responsable vigente del tratamiento. Con `from` o `to`, solo las de `session_date` entre esos días, ambos incluidos (ya es la fecha de Quito). Es una sentencia nativa: los ids van como un arreglo (`= ANY(CAST(:ids AS bigint[]))`), para no llegar al límite de parámetros de PostgreSQL con muchos tratamientos, y los nulos con `CAST(:x AS tipo) IS NULL`.
    - Qué cuenta como sesión realizada lo decide `sessions` (`completed`). Las lecturas por este puerto no se auditan aquí: audita quien llama.
    - Es la única forma en que otros módulos (`portal`, `reports`, ...) leen sesiones.
  - `ec.rehabelite.catalogs.AddressRequest`, `AddressView` y `AddressFormatter` (MEJ-02, antes en `patients`): la dirección estructurada del request (con `@ConsistentPlace`), su vista con los nombres del INEC y el texto compuesto. Los usan `patients` y `staff`; el tipo embebido es `shared.domain.PostalAddress`.
  - `ec.rehabelite.catalogs.LocationCatalog` (MEJ-03): provincias, cantones y parroquias del Clasificador Geográfico Estadístico 2025 del INEC (corte al 31/12/2024), leídos de `catalogs/ec-locations-cge2025.json` al arrancar (`ResourceLocationCatalog`). `isConsistent(provincia, cantón, parroquia)` valida una dirección y `findParish(código)` devuelve el `Place` con los nombres oficiales (en mayúsculas, como los publica el INEC) y `displayName()` con mayúscula inicial. Fuente, reglas y conteos en `docs/catalogs/ubicaciones.md`; el JSON se genera con `tools/catalogs/inec_cge_to_json.py`. Ningún dato sale de la aplicación.
  - `ec.rehabelite.audit.EntityVersionHistory` (HU-10): `versionsOf(clase, id)` devuelve las versiones de Envers de una entidad `@Audited`, de la más antigua a la más reciente. Cada `EntityVersion` trae la entidad, la revisión, el tipo (`CREATE`, `UPDATE`, `DELETE`), la fecha y el autor (o `null`). Así ningún módulo usa `AuditRevision` ni Envers directamente.
- Seguridad: JWT HS256 emitido por `POST /api/auth/login` (claims `sub` = id, `email`, `fullName`, `role` y `sv`, la versión de sesión de MEJ-01; el rol da la autoridad `ROLE_<rol>`). Públicos solo login, los enlaces de las cuentas (`POST /api/auth/activate`, `/forgot-password` y `/reset-password`, MEJ-01, y `/confirm-email`, MEJ-02), `/actuator/health` y la documentación OpenAPI. El reloj se inyecta como bean `Clock` (`shared.config`).
- Estado de cuentas (HU-03):
  - `PATCH /api/users/{id}/status` con `{ "active": boolean }` (`UserManagementService.changeStatus`).
  - Nunca se borra una cuenta: se desactiva. Reactivarla reinicia intentos fallidos y bloqueo.
  - Nadie desactiva su propia cuenta (422).
  - Cada cambio real audita `STATUS_CHANGE User`; pedir el estado actual no cambia ni audita nada.
  - El login rechaza la cuenta inactiva con el mensaje genérico de HU-01.
  - `ActiveAccountTokenValidator` (validador del `JwtDecoder`, después de firma, expiración y emisor) da 401 a los tokens ya emitidos de una cuenta inactiva o inexistente, o emitidos antes del último restablecimiento de contraseña (MEJ-01). Las pruebas deben emitir tokens solo para usuarios guardados.
- Activación de cuentas y restablecimiento de contraseña (MEJ-01, Jira RHE-84), módulo `auth`. Reemplaza la contraseña que definía el administrador en HU-02:
  - **Flujo de una cuenta:** el administrador la crea **sin contraseña** → queda "Pendiente de activación" (`email_verified_at` nulo, `password_hash` nulo) y su titular recibe el correo "Active su cuenta de Rehab Elite" con el enlace `{APP_FRONTEND_URL}/activar-cuenta?token=…` → al activarla define su contraseña y queda activa. `UserResponse.status` es `PENDING_ACTIVATION`, `ACTIVE` o `INACTIVE` (desactivada, se haya activado o no; `User.status()`); `active` se conserva.
  - **Desde MEJ-02 no existe `POST /api/users`** (405, `Allow: GET`): una cuenta se crea solo con el registro de su persona (`POST /api/staff`, o `POST /api/patients/{id}/portal-account` para un paciente), con `AccountProvisioning`. El correo de activación sale **al final y dentro de la transacción**: si el SMTP falla, no se crea nada y responde 503 "No se pudo enviar el correo de activación. Intente nuevamente en unos minutos." (`ExternalServiceException`).
  - `POST /api/users/{id}/resend-invitation` (solo `ADMINISTRATOR`): enlace nuevo para una cuenta habilitada que espera a su titular: el de activación si está pendiente, o el de confirmación del correo nuevo si tiene un cambio de correo por confirmar (MEJ-02). Si no, 422 "Solo se puede reenviar el enlace a una cuenta habilitada que aún no se ha activado o que tiene un cambio de correo por confirmar."; 404 si no existe. Audita `INVITATION_RESENT User`. Un fallo del correo responde 503 y los enlaces anteriores siguen vigentes.
  - `UserResponse` trae `emailChangePending` (MEJ-02).
  - **Desde MEJ-02 no existe `GET /api/users`** (404): las cuentas se listan con su persona (`GET /api/people` para los pacientes y, desde MEJ-05, `GET /api/staff` para el personal), con su estado y último acceso. De `/api/users` quedan solo `PATCH /api/users/{id}/status` y `POST /api/users/{id}/resend-invitation`, que usan la lista y las fichas.
  - **Enlaces de un solo uso** (`account_tokens`, `AccountTokenService`): 32 bytes de `SecureRandom` en Base64 URL sin relleno (43 caracteres, `AccountTokens`); se guarda solo su SHA-256 en hexadecimal (`token_hash char(64)`, único). Vencen según `app.tokens.activation-ttl` (48 h por defecto) y `app.tokens.reset-ttl` (1 h). Un enlace nuevo del mismo propósito marca como usados (`used_at`) los anteriores sin usar. Al usarlo, la fila se lee con bloqueo `PESSIMISTIC_WRITE`, así que dos usos simultáneos no pueden tener éxito. **El token nunca va a los logs ni a la auditoría**: solo aparece en el enlace del correo, que tampoco se registra.
  - `POST /api/auth/activate` y `POST /api/auth/reset-password` (públicos, `AccountAccessService`) reciben `{token, password, passwordConfirmation}` (`SetPasswordRequest`). Primero validan la contraseña (la regla de HU-02: de 8 a 72 caracteres, con al menos una letra y un número, y como máximo 72 bytes) y la confirmación (400 "La confirmación no coincide con la contraseña."), sin gastar el enlace; después el enlace. Un enlace inexistente, usado, reemplazado o vencido, de otro propósito o de una cuenta desactivada responde siempre 400 "El enlace no es válido o ya venció. Solicite uno nuevo.". Responden 204.
    - Activar guarda el BCrypt, `email_verified_at` y `used_at`, y audita `ACTIVATION User` atribuida a la propia cuenta (`recordFor`).
    - Restablecer guarda el BCrypt, reinicia los intentos fallidos y el bloqueo, incrementa `users.session_version` (los JWT llevan el claim `sv` y `ActiveAccountTokenValidator` rechaza los de una versión anterior: así se cierran las sesiones abiertas, como al desactivar; un token sin el claim cuenta como versión 0) y audita `PASSWORD_RESET User` atribuida a la propia cuenta.
  - `POST /api/auth/forgot-password` `{email}` (público): responde **siempre 202** con el mismo mensaje, "Si el correo corresponde a una cuenta activa, recibirá en unos minutos un enlace para restablecer la contraseña.". Solo envía el correo "Restablezca su contraseña de Rehab Elite" (`/restablecer-contrasena?token=…`) si la cuenta existe, está habilitada y activada, y recibió **menos de 3 enlaces de restablecimiento en la última hora** (se cuentan en `account_tokens`). El correo sale después del commit en otro hilo (`applicationTaskExecutor`): ni la respuesta ni su tiempo revelan si la cuenta existe, y un fallo del SMTP solo se registra en el log. Cada solicitud audita `RESET_REQUEST User`, sin usuario y con el id de la cuenta, o sin id si el correo no existe (solo queda la IP); el correo nunca se guarda.
  - **Login:** una cuenta pendiente recibe el mismo mensaje genérico de credenciales inválidas, sin contar un intento fallido; su comparación BCrypt usa el hash ficticio para que el tiempo no la delate.
  - Las cuentas creadas antes de V5 y las del seed `dev` quedan verificadas (`email_verified_at = created_at`, o `now()` en el seed). `User.create` (administrador inicial, pruebas) crea la cuenta ya verificada y con contraseña.
  - Correo: `AccountMailer` arma un mensaje en español con texto y HTML (`MailMessage`: destinatario, asunto, texto y HTML; remitente `app.mail.from` con el nombre "Rehab Elite"). En desarrollo, Mailpit (`docker compose --profile dev up -d mailpit`) recibe los correos en el puerto 1025 y los muestra en `http://localhost:8025`.
  - **Transporte del correo (TEC-16, Jira RHE-88):** `AccountMailer` solo escribe el mensaje y lo entrega al puerto `auth.mail.MailGateway`; `MailGatewayConfig` elige el adaptador con `app.mail.transport` (`MAIL_TRANSPORT`):
    - `smtp`, por defecto, también con `MAIL_TRANSPORT` vacío: `SmtpMailGateway`, con `JavaMailSender` y el servidor de `spring.mail` (Mailpit; en las pruebas, `RecordingMailSender`);
    - `api`, en producción (Render no permite SMTP saliente en el plan gratuito): `BrevoApiMailGateway`, `POST {app.mail.brevo.base-url}/v3/smtp/email` con la cabecera `api-key` (`BREVO_API_KEY`), `RestClient` sobre el `HttpClient` del JDK, 5 s para conectar y 10 s para leer (`app.mail.brevo.connect-timeout` y `read-timeout`).
    - Con `api` sin `BREVO_API_KEY`, o con otro valor, la aplicación no arranca (`AppProperties.Mail`). `AppProperties.Brevo.toString()` nunca imprime la clave.
    - Un 4xx, un 5xx, un tiempo agotado o un host inalcanzable lanzan `MailSendException`, como un SMTP caído: los enlaces conservan su 503 y el aviso al correo anterior y el restablecimiento siguen siendo del mejor esfuerzo (solo el log).
    - Ni el log ni la excepción llevan el destinatario, el asunto, el contenido ni el cuerpo de la respuesta de Brevo: solo el código HTTP o el tipo del fallo de red. `MailMessage.toString()` no muestra nada.
    - Pruebas: `BrevoApiMailGatewayTests` (servidor HTTP local del JDK: envío, 4xx, 5xx, tiempo agotado y host inalcanzable; **nunca se llama a Brevo**), `AccountLinkSenderApiTransportTests` (el mismo 503 que con SMTP) y `MailGatewayConfigTests`. Despliegue, remitente y DNS (SPF, DKIM, DMARC) en `docs/deployment/correo.md`: `MAIL_FROM` debe ser de un dominio propio verificado en Brevo, nunca gmail.com ni outlook.com.
  - Pruebas: `AccountActivationIntegrationTests` (activación, enlace vencido, usado y reenviado, contraseña inválida, restablecimiento que cierra las sesiones, correo inexistente con la misma respuesta, límite de solicitudes y auditoría y logs sin el token; las cuentas se crean con `TestStaff`), `UserControllerIntegrationTests` (`POST /api/users` responde 405) y `FlywayMigrationTests` (V5 sobre una base con cuentas existentes, en una base nueva del mismo contenedor).
- Registro unificado de usuarios (MEJ-02, Jira RHE-85): pacientes y personal con los mismos datos personales, y cuentas creadas solo desde el registro de su persona, sin escribir el nombre ni el correo.
  - **Módulo `staff`** (tabla `staff_records`, entidad `StaffRecord`, vínculo único a `users` por `user_id`): cédula, nombres, apellidos, nacimiento, sexo, teléfono, correo y dirección estructurada, con las mismas reglas que el paciente (`@ValidNationalId`, `@ValidPhone`, `AddressRequest`, `NameCase`). El rol vive en `users.role`. Depende de `auth`, `patients`, `catalogs` y `audit` por sus interfaces públicas; desde MEJ-04, solo `assignments` depende de `staff` (`PhysiotherapistSchedules`).
  - `POST /api/staff` (`StaffRegistrationService.register`): rol (`PHYSIOTHERAPIST`, `ADMINISTRATOR` u `OPERATIONS_DIRECTOR`; otro valor, 400) y datos personales. Crea la cuenta con `AccountProvisioning.create`, el registro y, al final, la invitación de MEJ-01: si el correo falla, 503 y no se crea nada. Audita `CREATE User`, `CREATE StaffRecord` e `INVITATION_SENT User`.
  - `GET /api/staff/{id}` (datos personales, rol y `account`; no se audita) y `PUT /api/staff/{id}` (todos los datos obligatorios; la cédula registrada no cambia, 422 "La cédula registrada no se puede modificar.", pero un registro sin cédula la recibe aquí; el rol no se edita). Un cambio real audita `UPDATE StaffRecord`.
  - **`users.full_name` se deriva siempre** de "nombres apellidos" del registro: al crear la cuenta y en cada edición de la persona (`AccountProvisioning.rename`, sin auditoría propia: ya se audita la edición).
  - **Unicidad** (`StaffIdentityRules`, `PatientIdentityRules`): la cédula es única entre pacientes y personal. El correo del personal es único frente a pacientes, personal y cuentas (correo o `pending_email`). Varios pacientes **sí pueden compartir correo** (p. ej. un menor y su madre o padre; el formulario solo avisa), pero el correo de un paciente nunca puede ser el de un miembro del personal ni el de una cuenta del personal, y un paciente con cuenta no puede tomar el correo de otra cuenta. Un conflicto responde 409 con `existingPatientId` o `existingStaffId` y `field` (`nationalId` o `email`), nunca el correo. **Limitación conocida:** como la cédula es única entre pacientes y personal, un fisioterapeuta (o cualquier miembro del personal) no puede registrarse además como paciente.
  - **Desde MEJ-05 hay dos listas** (`PeopleRegistryService`, búsqueda de pacientes: dígitos, inicio de la cédula): `GET /api/people?q=&accountStatus=&page=&size=`, de la recepción, **solo pacientes**, y `GET /api/staff?q=&role=&accountStatus=&page=&size=`, de la dirección, **solo personal** (`accountStatus` sin `NONE`: todo el personal tiene cuenta; `role` sin `PATIENT`; otro valor, 400). Cada lista va por apellidos y nombres. Cada fila trae tipo, id, cédula, nombres, rol (`PATIENT` para los pacientes), correo, teléfono, `accountStatus` (`NONE`, `PENDING_ACTIVATION`, `ACTIVE`, `INACTIVE`), `userId`, `emailChangePending` y `needsCompletion`. **Número fijo de sentencias**: token, conteo y página (`PatientDirectory.registry` o `StaffRecordRepository`) y las cuentas de la página en una consulta; son 4 con cualquier tamaño de página, una más con el filtro de rol o de estado (`PeopleRegistryIntegrationTests`).
  - **Cuentas en el registro (MEJ-02, ajuste A):** `GET /api/people` acepta `accountStatus` (`NONE`, sin cuenta: solo pacientes, porque todo el personal tiene una; `PENDING_ACTIVATION`, `ACTIVE` o `INACTIVE`, con `AccountProvisioning.idsWithStatus`: una sentencia más, 7 en total) y cada fila trae `lastLoginAt`. `AccountProvisioning.statistics()` (un solo `GROUP BY`) da por rol las cuentas, las activas y las pendientes de activación (`AccountStatistics.RoleAccounts`). Desde MEJ-05, `GET /api/people/stats` da los indicadores de la recepción (pacientes registrados, `PatientDirectory.registryCounts()`; portal activo y pendiente; sin acceso al portal) y `GET /api/staff/stats` los de la dirección (cuentas activas del personal y cuentas de fisioterapeutas, recepción y dirección en cualquier estado). `AccountSummary`, `PersonResponse.account`/`PatientResponse.portalAccount` traen `lastLoginAt`. No se audita.
  - `GET /api/people/matches?nationalId=&email=` (recepción y dirección, MEJ-05): pacientes y personal que ya tienen la cédula o el correo (también por su cuenta, incluido `pending_email`), para que el formulario impida un duplicado o avise. Datos personales: no se audita.
  - **Acceso al portal** (`POST /api/patients/{id}/portal-account`, `PortalAccountService.create`): crea la cuenta `PATIENT` con el correo y los nombres del paciente, la vincula (`portal_user_id`) y envía la invitación, sin cuerpo en la petición. 409 si ya tiene cuenta (`existingUserId`) u otra cuenta usa su correo; 503 si el correo falla (no se crea nada). Audita `CREATE User`, `UPDATE Patient` e `INVITATION_SENT User`. `PatientResponse.portalAccount` trae la cuenta (id, estado, `emailChangePending`) o `null`. Se eliminaron `GET /api/patients/portal-candidates` y el evento `PortalAccountCreated`.
  - **Cambio de correo** (`AccountProvisioning.changeEmail`, al editar el correo de un paciente con cuenta o de un miembro del personal):
    - cuenta pendiente de activación: toma el correo nuevo y recibe ahí un enlace de activación nuevo (los anteriores dejan de servir);
    - cuenta activa: el correo nuevo queda en `users.pending_email` y recibe el enlace "Confirme su nuevo correo de Rehab Elite" (`/confirmar-correo?token=…`, propósito `EMAIL_CHANGE`, 48 h como la activación). Hasta confirmarlo, la cuenta sigue ingresando con el correo anterior y la misma contraseña. El correo anterior recibe el aviso **sin enlace** "Se solicitó cambiar el correo de su cuenta de Rehab Elite", que sale después del commit en otro hilo: si el aviso falla, el cambio sigue adelante (solo se registra en el log). Solo el enlace al correo nuevo exige el envío: si falla, 503 y no cambia nada;
    - `POST /api/auth/confirm-email` `{token}` (público, `AccountAccessService.confirmEmail`): el correo nuevo pasa a ser el de la cuenta, verificado, e incrementa `session_version` (cierra las sesiones abiertas). Un enlace inválido responde el 400 genérico de MEJ-01. Audita `EMAIL_CONFIRMATION User` atribuida a la propia cuenta;
    - volver al correo actual cancela el cambio pendiente; una cuenta desactivada no cambia de correo (422 "La cuenta de esta persona está desactivada. Reactívela antes de cambiar el correo.");
    - el cambio audita `EMAIL_CHANGE User` **sin ninguno de los dos correos** (`audit_logs` no tiene descripción), y los logs nunca los escriben.
  - **Datos existentes (V7):** cada cuenta del personal recibe su registro con el correo de la cuenta y los nombres separados de `full_name` (1 palabra: solo nombre; 2: uno y uno; 3: el primero y los dos últimos; 4 o más: los dos primeros y el resto; `staff.domain.NameSplit` aplica la misma regla al administrador inicial). Cédula, nacimiento, sexo, teléfono y dirección quedan vacíos, con "Datos por completar" (`StaffRecord.needsCompletion()`), y se exigen al editar. Las cuentas de portal toman los nombres de su paciente.
  - Pruebas: `StaffRegistrationIntegrationTests` (registro con invitación, 503, `full_name` derivado, registro por completar, administrador inicial con su registro), `PersonIdentityUniquenessIntegrationTests` (cédula y correo cruzados, dos pacientes con el mismo correo y el segundo acceso al portal en 409), `PortalAccountIntegrationTests`, `EmailChangeIntegrationTests` (confirmación, aviso al correo anterior, aviso que falla, enlace que falla, reenvío y cancelación, cuenta desactivada, cuenta del paciente), `PeopleRegistryIntegrationTests` y `FlywayMigrationTests` (V7 sobre datos de V6). `TestStaff` registra personal por la API en las pruebas.
- Separación de funciones (MEJ-05, Jira RHE-89): la recepción registra pacientes y la dirección operativa gestiona el personal.
  - **Recepción = rol `ADMINISTRATOR`** (el valor no cambia en la base, los JWT ni el código; solo lo visible): registra y edita **solo pacientes** y administra su acceso al portal. `GET /api/people` y `/api/people/stats` son solo de pacientes. Cualquier operación sobre el personal (registro, edición, cuentas y edición de horarios) le responde 403; lee los horarios (`GET /api/staff/*/schedule`).
  - **Dirección operativa:** `/api/staff/**` (lista, indicadores, registro, edición y horarios del personal: fisioterapeutas, recepción y dirección) y las acciones sobre las cuentas del personal. Las reglas de cuentas (cada lado las suyas y la última dirección activa) están en "Estado de cuentas".
  - **Cuenta inicial:** `DirectorAccountInitializer` crea al arrancar la dirección operativa de `app.initial-director` (`INITIAL_DIRECTOR_EMAIL`, `INITIAL_DIRECTOR_PASSWORD`) **solo si no hay una dirección activa**, ya activada y con su registro del personal (`StaffAccountCreated`). Sin las variables, o con un correo de otra cuenta, solo registra un error en el log y la aplicación arranca. Ninguna cuenta existente cambia: las `ADMINISTRATOR` siguen entrando como recepción.
  - Pruebas: `RoleAccessIntegrationTests` (matriz por rol con las áreas nuevas), `UserStatusIntegrationTests` (cada lado sus cuentas, 403 auditado y dos directores que se desactivan a la vez), `UserManagementServiceTests`, `PeopleRegistryIntegrationTests` (las dos listas, sus indicadores y sus sentencias), `StaffScheduleIntegrationTests` (la recepción lee y no edita), `AuditLogControllerIntegrationTests` (alcance de la recepción), `DirectorAccountInitializerIntegrationTests` y `DirectorAccountInitializerTests`, y `FlywayMigrationTests` (V9 sobre una base en V8).
- Horarios de los fisioterapeutas y asignación según disponibilidad (MEJ-04, Jira RHE-87):
  - **Horario semanal** (`staff`, tabla `staff_schedule_slots`, entidad `StaffScheduleSlot`): franjas de lunes (1) a sábado (6), hora de inicio y de fin (`time`, hora local de Quito), del registro del personal de un fisioterapeuta. Una franja nunca se edita: el horario se reemplaza completo.
    - `GET` y `PUT /api/staff/{id}/schedule` (`StaffScheduleService`): desde MEJ-05 el `GET` es de la **recepción y la dirección operativa** (la recepción lo necesitará para agendar) y el `PUT`, **solo de la dirección**: el `PUT` recibe `{slots: [{day, start, end}]}` (`HH:mm`; lista vacía, sin horario) y reemplaza la semana con el registro bloqueado (`StaffRecordRepository.findForUpdate`: dos `PUT` simultáneos van uno tras otro). Borra las franjas anteriores con un `DELETE` inmediato antes de insertar las nuevas (Hibernate haría los `INSERT` primero y chocarían con las anteriores). Un cambio real audita `UPDATE StaffRecord`; enviar el mismo horario no cambia ni audita nada.
    - **Reglas** (`staff.domain.WeeklySchedule`, 400 en español): de lunes a sábado ("La clínica atiende de lunes a sábado: el domingo no puede tener franjas."), horas y minutos sin segundos, inicio anterior al fin, dentro de la jornada ("La franja del lunes (06:30–09:00) está fuera de la jornada de la clínica, de 07:00 a 20:00.") y sin superposición en el mismo día ("Las franjas del martes se superponen: 08:00–12:00 y 11:00–13:00."). **Las franjas contiguas del mismo día se unen al guardar** (08:00–12:00 y 12:00–14:00 quedan como 08:00–14:00) y la respuesta trae las franjas unidas, ordenadas por día y hora. Un registro que no es de un fisioterapeuta responde 422 "Solo los fisioterapeutas tienen horario.".
    - **Jornada de la clínica:** `app.clinic.opening-time` (07:00) y `app.clinic.closing-time` (20:00) en `application.yml` (`AppProperties.Clinic`). V8 fija el marco más amplio, 07:00 a 20:00 (`ck_staff_schedule_slots_clinic_hours`): las propiedades pueden acotarlo, nunca ampliarlo; si no, la aplicación no arranca (`ClinicHoursPropertiesTests`). Ampliarlo requiere una migración nueva.
    - **Respaldo en la base (V8):** la restricción `ex_staff_schedule_slots_overlap` (`EXCLUDE USING gist` con `btree_gist` y el tipo de rango `time_range` de V8, intervalos `[inicio, fin)`) impide dos franjas superpuestas del mismo día; las contiguas sí se admiten. La base de Render debe permitir `btree_gist` (`docs/deployment/base-de-datos.md`, a verificar en el sprint 6).
    - `GET /api/physio/schedule` ("Mi horario"): el fisioterapeuta lee **solo el suyo**, identificado por el token; no edita (403 en `PUT /api/staff/{id}/schedule`). Sin registro del personal, 404 "Su cuenta no tiene un registro del personal.".
    - Las respuestas traen `staffId`, `fullName`, la jornada (`openingTime`, `closingTime`) y las franjas. Leer horarios no se audita: no son datos clínicos.
  - **Módulo `assignments`** (`AssignmentService`, `/api/operations/**`, **solo `OPERATIONS_DIRECTOR`**): usa `staff`, `clinicalrecords`, `patients`, `auth` y `audit` solo por sus interfaces públicas; nadie depende de `assignments`. Así `staff` no depende de `clinicalrecords`.
    - `GET /api/operations/schedules`: el tablero semanal, todos los fisioterapeutas (activos o no, `active`) por apellidos y nombres, con sus franjas, su carga (`activeTreatments`) y la jornada. Cinco lecturas además del token.
    - `GET /api/operations/availability?day=&from=&to=` (`HH:mm`): los fisioterapeutas **con la cuenta activa** y **una franja que cubre todo el rango** (las contiguas ya están unidas: con 08:00–12:00 y 12:00–14:00 guardadas, el rango 11:00–13:00 lo cubre), **de menor a mayor carga y luego por apellidos**, cada uno con sus franjas de ese día. Domingo, rango invertido o fuera de la jornada: 400 (`assignments.domain.AvailabilityRange`). No se audita.
    - `GET /api/operations/treatments?inactiveResponsible=&page=&size=`: tratamientos `ACTIVE`, del inicio más reciente al más antiguo, con `id`, `patientName`, `startDate`, `responsible` (id y nombre), `responsibleInactive` (el responsable vigente tiene la cuenta desactivada) y `scheduleDays` (días del horario del responsable). **Exactamente esas seis propiedades: nunca diagnóstico, plan, notas ni sesiones** (LOPDP, lo mínimo para asignar; `OperationsIntegrationTests` lo comprueba). Con `inactiveResponsible=true`, solo los de responsable inactivo. Audita `READ Treatment` por cada tratamiento listado (`recordAll`).
    - `PUT /api/operations/treatments/{id}/responsible` `{physiotherapistId}`: reasigna con `TreatmentResponsibles` (la lógica de HU-08): `UPDATE Treatment` y revisión de Envers del director. **El backend no exige la disponibilidad** (D8), solo un fisioterapeuta activo y un tratamiento activo (422); la disponibilidad la filtra el diálogo. Devuelve la fila de la lista.
    - **Número fijo de sentencias** (`OperationsPerformanceIntegrationTests`): tablero y disponibilidad 6 (token, cuentas de fisioterapeutas, registros, franjas, cuentas activas y cargas); lista 8 (token, cuentas activas, página, total, nombres de pacientes, nombres de responsables, días de horario e `INSERT` de auditoría), una más con `inactiveResponsible`; la carga, 1. La prueba mide la lista con el filtro, cuyas filas siempre tienen responsable: en la base compartida, la primera página sin filtro puede traer tratamientos de otras pruebas sin responsable, y entonces los nombres y los días no necesitan sentencia.
  - **Responsable inicial (limitación conocida, D7):** el responsable de un tratamiento sigue siendo el fisioterapeuta que crea el historial o el tratamiento (HU-07); la dirección lo reasigna después. **No hay preasignación** de un fisioterapeuta a un paciente nuevo antes de su primer tratamiento. Queda como trabajo futuro.
  - Pruebas: `WeeklyScheduleTests` (unión de contiguas, superposición, jornada, domingo, segundos), `StaffScheduleIntegrationTests` (reemplazo, unión, mensajes, 422, permisos, concurrencia y el `EXCLUDE` y los `CHECK` de V8), `OperationsIntegrationTests` (tablero, disponibilidad filtrada y ordenada, responsable inactivo, lista sin datos clínicos, reasignación con auditoría y Envers), `OperationsPerformanceIntegrationTests`, `ClinicHoursPropertiesTests` y `FlywayMigrationTests` (V8 sobre una base en V7).
- Registro de pacientes (HU-05):
  - `POST /api/patients` (`PatientRegistrationService.register`). El número de historia clínica es la cédula y lo asigna el servidor.
  - La edad no se guarda: `Patient.ageOn(fecha)`, y la respuesta trae `age` calculada a la fecha de hoy en Quito.
  - Etnia y religión son opcionales; si llegan en blanco se guardan como `null`.
  - Sin fecha de ingreso, se usa la de hoy.
  - Una cédula repetida responde 409 con la propiedad `existingPatientId`, o `existingStaffId` si es de un miembro del personal (MEJ-02, ver "Registro unificado").
  - Cada registro audita `CREATE Patient`.
  - `GET /api/patients/{id}` devuelve solo datos personales y no se audita: la auditoría `READ` es para datos clínicos.
  - Validador de cédula: `patients.domain.NationalId` (10 dígitos, provincia 01–24 o 30, tercer dígito menor que 6, módulo 10), con la anotación `@ValidNationalId` en los requests.
  - **Campos de registro (MEJ-03, Jira RHE-86)**, iguales en `POST` y `PUT`:
    - **Nombres y apellidos** se guardan sin espacios sobrantes y con mayúscula inicial (`shared.domain.NameCase`: "MARÍA  josé" → "María José"; de, del, la, las, los e y en minúscula salvo al inicio). El frontend aplica la misma regla (`shared/text/name-case.ts`). Un nombre antiguo en mayúsculas cuenta como cambio al guardarlo.
    - **Teléfono** (`patients.domain.PhoneNumber`, `@ValidPhone`): celular `09` y 8 dígitos o fijo `0`, código de provincia de 2 a 7 y 7 dígitos; solo dígitos. Mensaje: "Ingrese un celular (09XXXXXXXX) o un teléfono fijo con código de provincia".
    - **Dirección estructurada** (`address`, `AddressRequest`, embebida como `PatientAddress`): `provinceCode` (2 dígitos), `cantonCode` (4), `parishCode` (6) del catálogo del INEC, `mainStreet`, `streetNumber` (opcional), `crossStreet` y `reference`. La parroquia debe pertenecer al cantón y el cantón a la provincia (`@ConsistentPlace`, 400 en `address.parishCode`: "la parroquia debe pertenecer al cantón y el cantón a la provincia"). La dirección nunca se envía a un servicio externo.
    - **Seguro de salud:** `hasHealthInsurance` (Sí/No), obligatorio.
    - Las respuestas traen `address` con los códigos, los nombres con mayúscula inicial, `formatted` ("Av. Ilaló N12-34 y Río Pita, Conocoto, Distrito Metropolitano de Quito, Pichincha. Referencia: …", `AddressText`) y `complete`, y `needsCompletion` ("Datos por completar"): dirección registrada antes de MEJ-03 o teléfono fuera de la regla (`Patient.needsCompletion()`). `PatientSummaryResponse` también trae `needsCompletion`.
    - **Pacientes anteriores a MEJ-03:** V6 guardó su dirección de texto en `address_reference` y la dejó incompleta (sin parroquia ni calles), y su teléfono se conserva aunque no cumpla la regla. Al editarlos, el formulario exige la dirección completa y un teléfono válido.
- Búsqueda y actualización de pacientes (HU-06), en `PatientManagementService`:
  - `GET /api/patients?q=&page=&size=`:
    - Si `q` son solo dígitos, busca las cédulas que empiezan así.
    - Si no, cada palabra (hasta 5) debe aparecer en `normalize_text(last_name || ' ' || first_name)`. Es la expresión exacta del índice trigram `ix_patients_full_name_trgm`; no cambiarla sin cambiar el índice.
    - Orden por apellidos y nombres normalizados. `size` de 1 a 50 (10 por defecto).
    - Responde `PageResponse` (`shared.web`) con un resumen sin datos clínicos. La SQL vive en `PatientSearchImpl`, un fragmento de `PatientRepository`.
  - `PUT /api/patients/{id}` reemplaza la fecha de ingreso y los datos personales; la cédula y el número de historia nunca cambian.
    - `Patient.update` llena `updated_by` y `updated_at`, y se audita `UPDATE Patient`.
    - Enviar los mismos datos no cambia ni audita nada.
- Historial clínico (HU-07), módulo `clinicalrecords`, solo `PHYSIOTHERAPIST`:
  - `POST /api/patients/{patientId}/clinical-record` (`ClinicalRecordService.create`) crea el historial (hábitos y antecedentes) con el primer tratamiento `ACTIVE`.
    - El fisioterapeuta del token queda como responsable vigente (`responsible_assignments` con `valid_to` nulo).
    - Paciente inexistente: 404. Historial existente: 409 con `existingClinicalRecordId`.
    - Diagnóstico fuera del catálogo: 422. EVA fuera de 0–10 o sesiones ≤ 0: 400 (y la entidad lo vuelve a comprobar).
  - `POST /api/clinical-records/{id}/treatments` abre otro tratamiento. Un historial tiene **a lo sumo un tratamiento activo**; si ya hay uno, responde 422.
  - Se auditan `CREATE ClinicalRecord` y `CREATE Treatment`.
  - Un tratamiento nuevo sobre un historial ya gestionado se guarda con `TreatmentRepository.save`: hacer `save` del historial haría `merge`, y `merge` no propaga los hijos nuevos con `CascadeType.PERSIST`.
  - `GET /api/icd10-diagnoses?q=` busca por código (prefijo) o descripción sin tildes; devuelve hasta 20 resultados.
  - `GET /api/physio/patients?q=&page=&size=`:
    - Pacientes de `PatientDirectory` con el resumen del tratamiento vigente (el activo o, si no hay, el más reciente).
    - El resumen sale de **una** consulta por página (`ClinicalRecordRepository.findCurrentTreatments`).
    - La paginación y la búsqueda son las de pacientes: no se filtra por datos clínicos.
- Responsable y estado del tratamiento (HU-08), `TreatmentService`, solo `PHYSIOTHERAPIST` (cualquier fisioterapeuta, no solo el responsable vigente):
  - **Transiciones de estado permitidas** (regla en `Treatment.changeStatus`):
    - `ACTIVE → COMPLETED` y `ACTIVE → ABANDONED`.
    - `ABANDONED → ACTIVE`, solo si el historial no tiene otro tratamiento activo (si lo tiene, 422 "El paciente ya tiene un tratamiento activo.").
    - `COMPLETED` es un estado final: si el paciente vuelve, se abre un tratamiento nuevo.
    - Cualquier otra transición, incluido el cambio al mismo estado, responde 422 "Transición de estado no permitida.".
    - `Treatment.allowedNextStatuses()` da los destinos permitidos; la API los devuelve en `allowedNextStatuses` y el frontend solo ofrece esos.
  - `PATCH /api/treatments/{id}/status` con `newStatus` y `reason` (obligatorio, hasta 1000 caracteres):
    - Guarda en `treatment_status_changes` el estado anterior, el nuevo, el motivo, el usuario y la fecha.
    - Llena `updated_by` y `updated_at` del tratamiento y audita `STATUS_CHANGE Treatment`.
  - `PUT /api/treatments/{id}/responsible` con `physiotherapistId`:
    - Cierra la asignación vigente (`valid_to` = ahora) y abre una nueva. Solo en tratamientos `ACTIVE` (si no, 422).
    - El nuevo responsable debe ser un usuario activo con rol `PHYSIOTHERAPIST` (si no, 422). Elegir al responsable vigente no cambia ni audita nada; un cambio real audita `UPDATE Treatment`.
    - Hibernate ejecuta los INSERT antes que los UPDATE y el índice único parcial solo admite un responsable vigente: por eso `Treatment.reassignResponsible` devuelve la nueva asignación sin añadirla a la colección, y el servicio hace `flush` del cierre antes de guardarla.
  - Los cambios leen el tratamiento con bloqueo `PESSIMISTIC_WRITE` (`TreatmentRepository.findForUpdate`).
  - `GET /api/treatments/{id}/history`: estado, `allowedNextStatuses`, responsable vigente, responsables y cambios de estado (del más reciente al más antiguo) con los nombres de `StaffDirectory`. `PUT` y `PATCH` devuelven este mismo cuerpo.
  - `GET /api/treatments/{id}` (plan, estado, paciente y responsable vigente) usa `findWithDetailsById` con `@EntityGraph`. Un grafo de tipo fetch vuelve diferido todo lo que no lista: hay que incluir también las relaciones EAGER que se mapean (como `diagnosis`).
  - `GET /api/physio/physiotherapists`: fisioterapeutas activos (id y nombre) para elegir el responsable.
  - `GET /api/treatments/{id}` y `GET /api/treatments/{id}/history` auditan `READ Treatment` (HU-18). `PUT` y `PATCH` devuelven el historial sin `READ`: ya auditan su cambio.
- Consulta y edición del historial (HU-09), solo `PHYSIOTHERAPIST`:
  - `GET /api/patients/{patientId}/clinical-record` (`ClinicalRecordViewService.view`):
    - Devuelve el expediente completo: datos personales (`PatientProfile`), hábitos y antecedentes, `activeTreatment` (o `null`) con `currentResponsible`, y `previousTreatments` (del más reciente al más antiguo, cada uno con su último responsable).
    - Cada consulta correcta audita `READ ClinicalRecord` con el id del historial.
    - Errores: 404 "El paciente no existe." o "El paciente no tiene historial clínico.". Ninguno se audita.
    - **Número fijo de sentencias, sin N+1**: el paciente, el historial con tratamientos y diagnósticos (`ClinicalRecordRepository.findWithTreatmentsByPatientId`, `JOIN FETCH`), los responsables vigentes de todos los tratamientos en una consulta `IN` (`ResponsibleAssignmentRepository.findCurrentByTreatmentIds`) y sus nombres (`StaffDirectory.fullNames`).
    - Con la validación del token y el `INSERT` de auditoría son 6 sentencias, tenga el paciente 1 o 50 tratamientos.
    - No se pueden traer los responsables en el mismo `JOIN FETCH`: `treatments` y `responsibleAssignments` son dos `List` (bags), y Hibernate no admite cargar dos bags a la vez (`MultipleBagFetchException`).
    - `ClinicalRecordViewPerformanceIntegrationTests` (200 pacientes con 5 tratamientos cada uno) exige menos de 2 s por consulta y como máximo 6 sentencias, y compara el conteo con 1 y con 5 tratamientos. Las sentencias se cuentan con Hibernate Statistics, activadas solo en `application-test.yml` (`hibernate.generate_statistics`).
    - El servicio es `@Transactional` sin `readOnly`, porque la auditoría inserta en la misma transacción.
  - `PUT /api/patients/{patientId}/clinical-record` (`ClinicalRecordService.updateHistory`):
    - Reemplaza hábitos, actividad física, alergias, medicación y antecedentes personales, quirúrgicos y familiares. No toca los tratamientos.
    - Usa las mismas validaciones que HU-07 (400).
    - Errores 404: los mismos dos mensajes que el `GET`.
    - `ClinicalRecord.update` llena `updated_by` y `updated_at`, y se audita `UPDATE ClinicalRecord`. Enviar los mismos datos no cambia ni audita nada.
    - Responde los hábitos y antecedentes con `updatedAt`.
- Sesiones terapéuticas (HU-10), módulo `sessions` (`TherapySessionService`), solo `PHYSIOTHERAPIST`:
  - `POST /api/treatments/{treatmentId}/therapy-sessions` registra la sesión: fecha, hora, tipo (hasta 50), actividades, observaciones clínicas, indicaciones para el paciente (opcional; en blanco se guarda `null`), EVA 0–10, evolución y estado.
    - El servidor asigna el correlativo: el mayor del tratamiento + 1, leído con el tratamiento bloqueado (`TreatmentQueryPort.lockForSessionRegistration`). Así dos registros simultáneos nunca repiten número (`TherapySessionConcurrencyIntegrationTests`); `uq_therapy_sessions_treatment_sequence` es el respaldo.
    - El responsable es siempre el usuario del token. El request no tiene `responsibleId` ni `sequenceNumber`; si el cliente los envía, se ignoran.
    - Errores: campo vacío, EVA fuera de 0–10 o fecha posterior a hoy (`@PastOrPresent`): 400. Tratamiento inexistente: 404. Tratamiento que no está `ACTIVE`, o fecha anterior a su inicio: 422.
  - `GET /api/treatments/{treatmentId}/therapy-sessions?page=&size=`: `PageResponse` ordenada por correlativo (`size` 1–100, 20 por defecto), con el nombre del responsable de `StaffDirectory.fullNames`. 404 si el tratamiento no existe.
  - `GET /api/therapy-sessions/{id}`: la sesión con su responsable.
  - `PUT /api/therapy-sessions/{id}` corrige los campos editables, con las mismas validaciones (400 y 422 por la fecha). El correlativo y el responsable nunca cambian (`updatable = false`).
    - **Regla: solo el fisioterapeuta que registró la sesión (`responsible_id`) puede corregirla.** Si otro fisioterapeuta envía el `PUT`, la respuesta es 403 con el detalle "Solo el fisioterapeuta que registró la sesión puede corregirla." y queda `ACCESS_DENIED` en `audit_logs` (entidad `therapy-sessions` con el id de la ruta). Primero se comprueba que la sesión exista (404).
    - Se puede corregir aunque el tratamiento ya no esté activo.
    - Enviar los mismos datos no cambia ni audita nada.
    - Envers guarda la versión anterior en `therapy_sessions_aud`.
  - `GET /api/therapy-sessions/{id}/versions`: versiones de la más reciente a la más antigua, con tipo (`CREATE` registro, `UPDATE` corrección), fecha, autor (`changedBy`, id y nombre) y el contenido de la sesión en esa versión. Cualquier fisioterapeuta las ve.
  - No existe `DELETE`: una sesión nunca se elimina (405, con `Allow: GET, PUT`).
  - `TherapySession` guarda `treatmentId` y `responsibleId` como ids, sin relación JPA, porque son de otros módulos.
- Línea de tiempo terapéutica (HU-11), módulo `sessions` (`TherapySessionService.timeline`), solo `PHYSIOTHERAPIST`:
  - `GET /api/treatments/{treatmentId}/timeline?from=&to=` devuelve:
    - `treatment`: id, paciente, `active`, fecha de inicio, EVA inicial, sesiones planificadas, `completedSessions` (las `COMPLETED` de todo el tratamiento, sin importar el filtro) y diagnóstico. Sale de `TreatmentQueryPort.find`.
    - `sessions`: las sesiones del rango en orden cronológico (fecha, hora y correlativo), con el mismo cuerpo que `GET /api/therapy-sessions/{id}`. Los nombres de los responsables salen de **una** llamada a `StaffDirectory.fullNames`.
    - `painCurve`: la curva de EVA (`PainPoint.curveOf`, en `sessions.domain`). El primer punto es siempre la EVA inicial con la etiqueta "Evaluación inicial" y la fecha de inicio, aunque haya filtro; después, solo las sesiones `COMPLETED` del rango ("Sesión N"). Las `RESCHEDULED` y `NO_SHOW` aparecen en `sessions`, pero no en la curva.
    - `from` y `to`: el filtro aplicado.
  - `from` y `to` son opcionales e inclusivos, en días de Quito (`session_date`). La consulta es una `Specification` (`TherapySessionRepository.findTimeline`): solo los límites enviados se vuelven predicados, porque PostgreSQL no acepta `:param IS NULL` con un parámetro nulo sin tipo.
  - Errores: `from` posterior a `to` responde 400 con `InvalidRequestException` "La fecha inicial no puede ser posterior a la fecha final." antes de leer nada. Tratamiento inexistente: 404. Ninguno se audita.
  - Cada consulta correcta audita `READ Treatment`.
  - **Número fijo de sentencias, sin N+1**: validación del token, tratamiento con historial y diagnóstico, sesiones, conteo de `COMPLETED`, nombres e `INSERT` de auditoría. Son 6, con 1 o con 100 sesiones. Lo comprueba `TherapyTimelinePerformanceIntegrationTests` (100 sesiones de cuatro fisioterapeutas: menos de 2 s, como máximo 6 sentencias y el mismo conteo que con 1 sesión), con Hibernate Statistics como en HU-09.
  - No es paginada: la vista es la historia completa del tratamiento.
- Portal del paciente (HU-12, HU-13), módulo `portal` (`PortalService`), solo `PATIENT` y solo lectura:
  - **Regla de identidad:** el paciente es siempre el vinculado a la cuenta autenticada (`PatientDirectory.findByPortalUser` con el usuario del token). Ningún endpoint del portal recibe `patientId`, ni en la ruta ni en el cuerpo. No añadir uno.
  - `portal` usa `patients`, `clinicalrecords`, `sessions`, `auth` y `audit` solo por sus interfaces públicas; ninguno de ellos depende de `portal`.
  - `GET /api/portal/treatments`: los tratamientos del paciente, el activo primero y luego los demás del más reciente al más antiguo. Cada uno trae diagnóstico, estado, fecha de inicio, nombre del responsable vigente (o `null`), sesiones planificadas y sesiones realizadas (solo `COMPLETED`). Sin tratamientos responde `[]`.
  - `GET /api/portal/treatments/{id}/progress` (`portal.domain.TreatmentProgress`):
    - sesiones realizadas, planificadas y `percentage` (realizadas sobre planificadas, redondeado hacia abajo y con tope de 100);
    - `painCurve`, con las mismas reglas que HU-11: empieza en "Evaluación inicial" (EVA inicial y fecha de inicio) y sigue con las sesiones `COMPLETED` en orden cronológico ("Sesión N"). Las `RESCHEDULED` y `NO_SHOW` no cuentan ni aparecen;
    - `initialPainScore`, `lastPainScore` (EVA de la última sesión realizada) y `painReduction` (inicial menos última: positiva si el dolor bajó, negativa si subió). Los dos últimos son `null` si aún no hay sesiones realizadas.
  - `GET /api/portal/treatments/{id}/sessions` (HU-13): todas las sesiones del tratamiento en orden cronológico (fecha, hora y correlativo), sea cual sea su estado. Cada una trae correlativo, fecha, hora, tipo, nombre del fisioterapeuta que la registró (o `null`), EVA, actividades realizadas, estado e indicaciones para el paciente (o `null`). No es paginada, como la línea de tiempo. Sin sesiones responde `[]`.
    - **La respuesta nunca incluye las observaciones clínicas ni la evolución.** `PortalSessionsIntegrationTests` comprueba que el JSON no tiene esas propiedades ni sus valores, y que cada sesión tiene exactamente las nueve propiedades anteriores.
  - `GET /api/portal/treatments/{id}/instructions` (HU-13): la última indicación para el paciente (`instructions`), con la fecha y el correlativo de su sesión y el nombre del fisioterapeuta. Es la de la sesión más reciente que tiene una, sea cual sea su estado. **Si ninguna sesión tiene indicaciones, responde 200 sin cuerpo** (no 204 ni 404).
  - Las respuestas no llevan `patientId`, ids del personal, observaciones clínicas, evolución ni el contenido clínico del tratamiento (motivo, objetivo...).
  - Errores:
    - Cuenta sin paciente vinculado: 404 "La cuenta no tiene un paciente vinculado.", sin auditoría.
    - **Tratamiento de otro paciente o inexistente: el mismo 403** "No tiene acceso a ese tratamiento." (`ForbiddenOperationException`), sin datos del tratamiento y con `ACCESS_DENIED`. El portal no responde 404 por un id que no existe, para no revelar qué ids existen. El resto de la API conserva el 404.
  - Auditoría: `READ Treatment` por cada tratamiento listado (un solo `INSERT`, `recordAll`) y por cada consulta de avance, de sesiones o de indicaciones, también cuando no hay indicaciones.
  - **Número fijo de sentencias, sin N+1**:
    - Lista: validación del token, paciente de la cuenta, tratamientos con diagnóstico, responsables vigentes, nombres (`StaffDirectory.fullNames`), conteo de realizadas e `INSERT` de auditoría. Son 6 lecturas y 1 `INSERT`, con 1 o con 5 tratamientos.
    - Avance: validación del token, paciente, tratamiento, sesiones e `INSERT` de auditoría. Son 5, con 1 o con 100 sesiones.
    - Sesiones: validación del token, paciente, tratamiento, sesiones, nombres (una llamada a `StaffDirectory.fullNames`) e `INSERT` de auditoría. Son 6, con 1 o con 100 sesiones.
    - Indicaciones: validación del token, paciente, tratamiento, una sesión, el nombre de su fisioterapeuta e `INSERT` de auditoría. Son 6, con 1 o con 100 sesiones.
    - Lo comprueba `PortalPerformanceIntegrationTests` con Hibernate Statistics, como en HU-09 y HU-11.
  - No hay `POST`, `PUT`, `PATCH` ni `DELETE` en `/api/portal/**` (405).
- Reportes de dirección operativa (HU-14, HU-15), módulo `reports` (`ReportService`), solo `OPERATIONS_DIRECTOR` y solo lectura:
  - `reports` usa `clinicalrecords`, `sessions`, `patients`, `auth` y `audit` solo por sus interfaces públicas; ninguno depende de `reports`.
  - **Regla de los filtros:** `status` (estado del tratamiento), `physiotherapistId`, `from` y `to` son opcionales y se aplican distinto a cada parte del reporte:
    - **Tratamientos** (activos, finalizados, abandonados y la lista): por su estado, por su **responsable vigente** y por su **periodo** (HU-15: en curso algún día del rango, ver `TreatmentQueryPort`).
    - **Sesiones** (registradas, por estado, por fisioterapeuta y promedio): por **quien registró la sesión** (`therapy_sessions.responsible_id`), por el estado del tratamiento al que pertenecen y por **su fecha** (`session_date`).
    - Así, tras un reemplazo, las sesiones que registró el fisioterapeuta anterior siguen contando para él, pero el tratamiento aparece en la lista del nuevo responsable (`ReportSummaryIntegrationTests`).
  - **Rango de fechas (HU-15):** `from` y `to` (`yyyy-MM-dd`) son días de Quito, ambos incluidos; uno solo también vale. `from` posterior a `to` responde 400 "La fecha inicial no puede ser posterior a la fecha final." antes de leer nada y sin auditar (`ReportFilter` lo comprueba al crearse), en el resumen, la lista y el PDF. `ReportDateRangeIntegrationTests` prueba los bordes, el periodo (también un cierre a las 23:30 de Quito, que en UTC ya es el día siguiente) y los filtros combinados.
  - **El estado es siempre el actual**, también con un rango: un tratamiento que estuvo en curso en el rango y finalizó después cuenta como finalizado. Con un rango, la vista y el PDF lo dicen: "El estado corresponde al estado actual de cada tratamiento."
  - `GET /api/reports/summary?status=&physiotherapistId=&from=&to=` (`reports.domain.OperationsSummary`):
    - `treatments`: activos, finalizados y abandonados, desde el estado de cada tratamiento (HU-08);
    - `sessions`: total, `completed`, `rescheduled` y `noShow`;
    - `sessionsByPhysiotherapist`: id, nombre y total de cada fisioterapeuta con al menos una sesión, de más a menos sesiones y luego por nombre;
    - `averageSessionsPerPhysiotherapist`: total de sesiones entre esos fisioterapeutas, con un decimal (redondeo `HALF_UP`); 0.0 sin sesiones.
  - `GET /api/reports/treatments?status=&physiotherapistId=&from=&to=&page=&size=`: `PageResponse` (`size` 1–50, 10 por defecto) de la fecha de inicio más reciente a la más antigua, con id, `patientName`, diagnóstico CIE-10, `responsibleName` (o `null`), estado y fecha de inicio. **Sin cédula, número de historia, `patientId` ni otros datos personales** (minimización de datos, LOPDP).
  - `GET /api/reports/summary.pdf?status=&physiotherapistId=&from=&to=` (HU-15): el informe en PDF, con **las mismas consultas que la vista** (`ReportService.export` usa los mismos métodos que `summary` y `treatments`; la lista trae todos los tratamientos del filtro con `TreatmentQueryPort.list`, no una página, y sin tope de filas).
    - Contiene el título, los filtros aplicados (estado, fisioterapeuta y periodo: "Del 01/10/2026 al 31/10/2026", "Desde el …", "Hasta el …" o "Todas las fechas"), la nota del estado actual si hay rango, la fecha y hora de generación en Quito y el nombre del usuario (del token), los indicadores con las sesiones por estado, "Sesiones por fisioterapeuta" con el promedio y "Tratamientos (N)" (paciente, diagnóstico, responsable, estado e inicio). Sin cédula. Pie "Página N de M".
    - `Content-Disposition: attachment; filename="informe-operativo-AAAA-MM-DD.pdf"` (fecha de Quito) y `Cache-Control: no-store`, porque lleva datos clínicos.
    - Se genera con **Apache PDFBox 3.0.8** (licencia Apache 2.0; se descartaron iText por AGPL y OpenPDF 3 porque trae bibliotecas nativas). `OperationsReportPdf` arma el contenido y `PdfLayout` (`reports.application`) es una maqueta mínima: párrafos con ajuste de línea y tablas cuya fila nunca se parte y cuyo encabezado se repite en cada página. Usa Helvetica (fuente estándar, no se incrusta, cubre el español); un carácter que no puede escribir sale como "?", nunca falla.
    - El PDF se arma dentro de la transacción: si falla, no queda auditoría.
    - `ReportPdfIntegrationTests` lee el PDF con PDFBox y compara sus totales, sus sesiones por fisioterapeuta y su número de tratamientos con el JSON de la vista para cada combinación de filtros (criterio de HU-15: el reporte y la exportación reflejan el mismo resultado).
  - `GET /api/reports/physiotherapists`: opciones del filtro, todos los fisioterapeutas (`StaffDirectory.physiotherapists()`). No se audita.
  - Un `status` que no es un estado del tratamiento, o una fecha mal escrita, responde 400 (enum `TreatmentStatusParam`, `@DateTimeFormat`). Un fisioterapeuta sin datos devuelve ceros y una lista vacía, no 404.
  - Auditoría: `READ Report` (sin id) por cada resumen, `READ Treatment` por cada tratamiento de la página en un solo `INSERT` (`recordAll`), y por cada PDF `EXPORT Report` (sin id) más `READ Treatment` por cada tratamiento del PDF en un solo `INSERT`.
  - **Número fijo de sentencias, sin N+1:**
    - Resumen: validación del token, tratamientos por estado, ids de los tratamientos del estado, sesiones por fisioterapeuta y estado, nombres e `INSERT` de auditoría. Son 6, con pocos o muchos tratamientos y con cualquier filtro, también con rango.
    - Lista: validación del token, página, total, nombres de los pacientes, nombres de los responsables e `INSERT` de auditoría. Son 6, también en la última página y con rango.
    - PDF: validación del token, tratamientos por estado, ids, sesiones, todos los tratamientos, nombres de los pacientes, nombres del personal (autores, responsables y el del filtro en una llamada) y los dos `INSERT`. Son 9, con 1 o con 60 tratamientos.
    - Lo comprueba `ReportPerformanceIntegrationTests` (1 tratamiento frente a 60 con 300 sesiones) con Hibernate Statistics.
  - No hay `POST`, `PUT`, `PATCH` ni `DELETE` en `/api/reports/**` (405).
  - El administrador no ve reportes: llevan datos clínicos (HU-06). El prototipo `12-admin-reports.jpg` queda superado por esta regla.
- Auditoría de información clínica (HU-18, LOPDP):
  - **Regla:** toda operación sobre datos clínicos registra en `audit_logs` el usuario, la acción, la entidad, su id, la fecha (`occurred_at`) y la IP. Los datos personales del paciente no se auditan al leerlos (sí al crearlos o modificarlos). Las peticiones rechazadas (400, 404, 422) no se auditan; los 403 sí (`ACCESS_DENIED`). Un servicio que lee y audita es `@Transactional` sin `readOnly`, porque la auditoría inserta en la misma transacción.

    | Endpoint | Acción registrada |
    |---|---|
    | `POST /api/staff` (MEJ-02) | `CREATE User` + `CREATE StaffRecord` + `INVITATION_SENT User` |
    | `PUT /api/staff/{id}` (MEJ-02) | `UPDATE StaffRecord` (solo si cambia algo); `EMAIL_CHANGE User` si cambia el correo |
    | `GET /api/staff/{id}`, `GET /api/people`, `GET /api/people/matches` (MEJ-02) | ninguna (datos personales) |
    | `POST /api/patients/{id}/portal-account` (MEJ-02) | `CREATE User` + `UPDATE Patient` + `INVITATION_SENT User` |
    | `POST /api/auth/confirm-email` (MEJ-02) | `EMAIL_CONFIRMATION User`, atribuida a la propia cuenta |
    | `POST /api/users/{id}/resend-invitation` (MEJ-01, MEJ-02) | `INVITATION_RESENT User` |
    | `POST /api/auth/activate` (MEJ-01) | `ACTIVATION User`, atribuida a la propia cuenta |
    | `POST /api/auth/forgot-password` (MEJ-01) | `RESET_REQUEST User`, sin usuario; sin id si el correo no existe |
    | `POST /api/auth/reset-password` (MEJ-01) | `PASSWORD_RESET User`, atribuida a la propia cuenta |
    | `POST /api/patients` | `CREATE Patient` |
    | `PUT /api/patients/{id}` | `UPDATE Patient` (solo si cambia algo); con cuenta, `EMAIL_CHANGE User` si cambia el correo (MEJ-02) |
    | `GET /api/patients`, `GET /api/patients/{id}` | ninguna (datos personales) |
    | `POST /api/patients/{id}/clinical-record` | `CREATE ClinicalRecord` + `CREATE Treatment` |
    | `GET /api/patients/{id}/clinical-record` | `READ ClinicalRecord` |
    | `PUT /api/patients/{id}/clinical-record` | `UPDATE ClinicalRecord` (solo si cambia algo) |
    | `POST /api/clinical-records/{id}/treatments` | `CREATE Treatment` |
    | `GET /api/treatments/{id}`, `GET /api/treatments/{id}/history` | `READ Treatment` |
    | `PUT /api/treatments/{id}/responsible` | `UPDATE Treatment` (solo si cambia algo) |
    | `PATCH /api/treatments/{id}/status` | `STATUS_CHANGE Treatment` |
    | `GET /api/physio/patients` | `READ ClinicalRecord` por cada historial de la página (`PhysioPatientService`, `recordAll`) |
    | `GET /api/icd10-diagnoses`, `GET /api/physio/physiotherapists` | ninguna (catálogo y personal) |
    | `GET /api/catalogs/locations` (MEJ-03) | ninguna (catálogo público del INEC) |
    | `POST /api/treatments/{id}/therapy-sessions` | `CREATE TherapySession` |
    | `GET /api/treatments/{id}/therapy-sessions` | `READ Treatment` |
    | `GET /api/therapy-sessions/{id}`, `GET /api/therapy-sessions/{id}/versions` | `READ TherapySession` |
    | `PUT /api/therapy-sessions/{id}` | `UPDATE TherapySession` (solo si cambia algo); si no es el autor, `ACCESS_DENIED therapy-sessions` |
    | `GET /api/treatments/{id}/timeline` | `READ Treatment` |
    | `GET /api/portal/treatments` | `READ Treatment` por cada tratamiento listado (`PortalService`, `recordAll`) |
    | `GET /api/portal/treatments/{id}/progress`, `GET /api/portal/treatments/{id}/sessions`, `GET /api/portal/treatments/{id}/instructions` | `READ Treatment`; si el tratamiento no es del paciente o no existe, `ACCESS_DENIED portal/treatments` |
    | `GET /api/reports/summary` | `READ Report` (sin id) |
    | `GET /api/reports/treatments` | `READ Treatment` por cada tratamiento de la página (`ReportService`, `recordAll`) |
    | `GET /api/reports/summary.pdf` | `EXPORT Report` (sin id) + `READ Treatment` por cada tratamiento del PDF (`recordAll`) |
    | `GET /api/reports/physiotherapists` | ninguna (personal) |
    | `PUT /api/staff/{id}/schedule` (MEJ-04) | `UPDATE StaffRecord` (solo si cambia algo) |
    | `GET /api/staff/{id}/schedule`, `GET /api/physio/schedule`, `GET /api/operations/schedules`, `GET /api/operations/availability` (MEJ-04) | ninguna (horarios, sin datos clínicos) |
    | `GET /api/operations/treatments` (MEJ-04) | `READ Treatment` por cada tratamiento listado (`AssignmentService`, `recordAll`) |
    | `PUT /api/operations/treatments/{id}/responsible` (MEJ-04) | `UPDATE Treatment` (solo si cambia algo) |

    Al añadir un endpoint clínico, agregarlo a esta tabla y a `ClinicalAuditIntegrationTests`.
  - **IP del cliente:** `server.forward-headers-strategy: framework`. La IP se toma de `X-Forwarded-For` (`ForwardedHeaderFilter`) **solo porque en producción la aplicación está detrás del proxy de Render**, que pone esa cabecera. El filtro confía en la cabecera de cualquier cliente, así que el backend nunca debe quedar expuesto sin el proxy. Sin la cabecera (desarrollo, pruebas) se usa la dirección de la conexión.
  - **`audit_logs` es de solo inserción:** los triggers de V1 rechazan `UPDATE`, `DELETE` y `TRUNCATE` (`AuditLogAppendOnlyIntegrationTests`). La entidad `AuditLog` es `@Immutable`.
  - **`GET /api/audit-logs`** (`AuditLogQueryService`), solo `ADMINISTRATOR` y `OPERATIONS_DIRECTOR`, de solo lectura (otros métodos: 405):
    - Paginado (`size` 1–100, 20 por defecto), de lo más reciente a lo más antiguo.
    - Filtros opcionales `userId`, `action`, `entityType`, `entityId`, `from` y `to`. Las fechas son días de Quito, ambos incluidos (`AuditLogFilter`, una Specification). `from` posterior a `to` responde 400.
    - Responde solo metadatos: `id`, `occurredAt`, `userId`, `userName`, `action`, `entityType`, `entityId` e `ipAddress`.
    - `GET /api/audit-logs/entity-types` y `GET /api/audit-logs/users` alimentan los filtros (vía `AuditUserDirectory`).
    - Consultar la auditoría no se audita.
  - **Hibernate Envers** (`hibernate-envers`), versiones completas de `treatments` y `therapy_sessions`:
    - V3 crea `revinfo` (`rev`, `revision_timestamp`, `user_id`) y las tablas `treatments_aud` y `therapy_sessions_aud` (columnas de la tabla, todas opcionales, más `rev` y `revtype`: 0 alta, 1 cambio, 2 baja).
    - `AuditRevision` (`audit.domain`, `@RevisionEntity`) y `AuditRevisionListener` llenan `revinfo.user_id` con el usuario del token, o `null` sin usuario autenticado (inicializadores, seed `dev`).
    - `Treatment` es `@Audited`. `clinicalRecord` y `diagnosis` guardan solo el id (`RelationTargetAuditMode.NOT_AUDITED`), y `responsibleAssignments` es `@NotAudited` porque ya es un historial.
    - Configuración en `application.yml` (`org.hibernate.envers.*`): sufijo `_aud`, `rev`, `revtype` y `store_data_at_delete`.
    - **Regla:** todo cambio futuro de columnas en `treatments` o `therapy_sessions` incluye el mismo cambio en su tabla `_aud` **en la misma migración**. Hibernate valida ambas (`ddl-auto: validate`), y `FlywayMigrationTests` compara sus columnas.
    - `TherapySession` (HU-10) es `@Audited` sobre `therapy_sessions_aud` tal como la creó V3. `FlywayMigrationTests` comprueba que cada tabla `_aud` tiene las mismas columnas, tipos y largos que su tabla. Si cambia algo, lo hace una migración nueva, nunca editando V3.
- Respuestas paginadas: devolver `PageResponse.of(page, mapper)` (`content`, `page`, `size`, `totalElements`, `totalPages`), nunca el `Page` de Spring Data.
- Acceso por rol (HU-04): la tabla vive en `auth.config.RoleAccessRules`. Una regla puede limitarse a un método HTTP y gana la primera que coincide. Al añadir un área de API nueva, registrarla ahí y en `RoleAccessIntegrationTests`.

  | Área | Roles |
  |---|---|
  | `/api/admin/**` y `/api/people/**` (MEJ-05: el registro de pacientes de la recepción) | `ADMINISTRATOR` |
  | `/api/patients/*/portal-account` (MEJ-02), `POST /api/patients` y `PUT /api/patients/**` (van antes de `/api/patients/**`) | `ADMINISTRATOR` |
  | `/api/patients/*/clinical-record` (va antes de `/api/patients/**`) | `PHYSIOTHERAPIST` |
  | `/api/patients/**` | `ADMINISTRATOR`, `PHYSIOTHERAPIST` |
  | `/api/clinical-records/**`, `/api/treatments/**`, `/api/therapy-sessions/**`, `/api/ai-summaries/**`, `/api/icd10-diagnoses/**`, `/api/physio/**` | `PHYSIOTHERAPIST` |
  | `/api/reports/**` (HU-14: datos clínicos, la recepción no los ve) | `OPERATIONS_DIRECTOR` |
  | `/api/audit-logs/**` (MEJ-05: la recepción ve solo las entradas de los pacientes) | `OPERATIONS_DIRECTOR`, `ADMINISTRATOR` |
  | `/api/portal/**` | `PATIENT` |
  | `GET /api/staff/*/schedule` (MEJ-04, MEJ-05, va antes de `/api/staff/**`) | `ADMINISTRATOR`, `OPERATIONS_DIRECTOR` |
  | `/api/operations/**` (MEJ-04, sin datos clínicos) | `OPERATIONS_DIRECTOR` |
  | Resto de `/api/**` | Cualquier usuario autenticado |

- Respuestas de seguridad:
  - `ProblemDetailAuthenticationEntryPoint`: 401 sin auditoría (también para el token de una cuenta inactiva).
  - `AuditingAccessDeniedHandler`: 403 y `ACCESS_DENIED` en `audit_logs`. La entidad se toma de la ruta (`/api/patients/12` → `patients`, 12); nunca se guarda la query string.
    - En un área que agrupa recursos, el id va después del segundo segmento y la entidad son los dos (`/api/portal/treatments/9/progress` → `portal/treatments`, 9). Sin id, sigue siendo el primer segmento (`/api/portal/treatments` → `portal`).
  - `GlobalExceptionHandler` relanza `AccessDeniedException` y `AuthenticationException`, para que un futuro `@PreAuthorize` también quede auditado.

### Buenas prácticas backend

- Inyección por constructor; nunca `@Autowired` en campos.
- Lombok **solo**: `@Getter`, `@Setter`, `@NoArgsConstructor` en entidades y `@RequiredArgsConstructor` en servicios. Nada de `@Data`, `@Builder`, `@Slf4j`, etc.
- DTO siempre como `record`; las entidades **nunca** se exponen en la API (mapear con MapStruct).
- Errores: lanzar `InvalidRequestException` (400, para lo que Bean Validation no expresa, p. ej. un rango de fechas invertido), `ResourceNotFoundException` (404), `BusinessRuleException` (422) o `ConflictException` (409) de `ec.rehabelite.shared.domain`, con mensaje en español; `GlobalExceptionHandler` los convierte en `ProblemDetail` en español. Nunca lanzar `ResponseStatusException`: su motivo se reemplaza por el detalle genérico de su código.
  - `ConflictException` añade sus propiedades a la respuesta: solo ids o códigos, nunca datos personales.
  - `ExternalServiceException` (`shared.domain`, MEJ-01) es para un servicio externo del que depende la operación y que falló (el SMTP del correo de activación): 503 "Servicio no disponible" con su mensaje como `detail`. El log solo registra el tipo de la causa, que puede llevar direcciones de correo.
  - `ForbiddenOperationException` (`shared.domain`, extiende `AccessDeniedException`) es para un rol que puede usar el endpoint pero no sobre ese recurso (HU-10: corregir la sesión de otro fisioterapeuta; HU-12: el tratamiento de otro paciente en el portal). `GlobalExceptionHandler` la relanza y `AuditingAccessDeniedHandler` responde 403 con su mensaje como `detail` y audita `ACCESS_DENIED`. Los demás 403 conservan el detalle genérico.
  - El locale está fijo en `es-EC`, así que los mensajes de Bean Validation también salen en español.
  - Las excepciones propias de Spring MVC salen con el título y el detalle en español de su código, sin cambiar el código. Son el 405 (conserva la cabecera `Allow`), el 415, el cuerpo ilegible, el parámetro faltante o de tipo inválido (400) y la ruta inexistente (`NoResourceFoundException`, 404). El detalle nunca repite el valor enviado por el cliente. Las excepciones de Spring que extienden `ErrorResponseException` (p. ej. `ResponseStatusException`) tampoco conservan el suyo: solo las excepciones de la aplicación (`shared.domain`) conservan su mensaje. Un parámetro fuera de rango (`@Min`/`@Max`, `HandlerMethodValidationException`) responde 400 "Datos no válidos" con la lista `errors`, igual que la validación del cuerpo. Lo hace `GlobalExceptionHandler.handleExceptionInternal`, que usa el `ErrorResponse` de la excepción cuando Spring no trae cuerpo, y lo comprueba `SpringErrorProblemDetailIntegrationTests`.
- Fechas de calendario ("hoy", edades, fecha de ingreso): usar `ClinicCalendar.today(clock)` de `shared.domain`, con la zona `America/Guayaquil`. Los instantes se guardan en UTC. Las anotaciones `@Past` y `@PastOrPresent` usan el mismo reloj (`ValidationConfig`).
- Fechas, horas y zona horaria en la base:
  - **Regla:** un momento exacto (`created_at`, `occurred_at`, `valid_from`...) es una columna `timestamptz` mapeada con `Instant`. Una fecha o una hora de la clínica (`session_date`, `session_time`, `birth_date`, `start_date`...) es `date` o `time` mapeada con `LocalDate` o `LocalTime`: es la fecha y la hora local de Quito, y se guarda tal como llega, sin conversión.
  - Nunca usar `LocalDateTime`, `java.util.Date` ni `timestamp` sin zona.
  - **Nunca configurar `hibernate.jdbc.time_zone`.** Con esa propiedad, una JVM en otra zona (UTC en la CI y en la imagen Docker) guardaba y leía `session_time` corrida 5 horas. El resultado no debe depender de la zona de la JVM: no fijar `TZ` ni `user.timezone` para compensar.
  - "Hoy" nunca se calcula con la zona de la JVM (`LocalDate.now()`), sino con `ClinicCalendar.today(clock)`. El bean `Clock` es UTC y `ClinicCalendar` le aplica `America/Guayaquil`.
  - Lo comprueba `TherapySessionTimeZoneIntegrationTests`: pone la JVM en UTC, registra una sesión por la API y lee la base con JDBC directo (`session_date` y `session_time` como texto, `created_at` como época).
- Logs con SLF4J (`LoggerFactory.getLogger`), en inglés, **sin datos personales ni clínicos** (nada de cédulas, nombres, correos, diagnósticos ni valores de columnas; usar ids).
- Configuración propia bajo `app.*` en el record validado `ec.rehabelite.shared.config.AppProperties`.
- Perfiles: **no hay perfil por defecto**; sin perfil solo aplica la configuración común.
  - `application.yml`: común; `server.port` lee `PORT`.
  - `application-dev.yml`: se activa explícitamente; carga los datos sintéticos y los logs `DEBUG`.
  - `application-test.yml`: Testcontainers, en `src/test/resources`.
  - `prod`: lo activa la imagen Docker; solo configuración común.
- Datos sintéticos de desarrollo (TEC-06), **solo con el perfil `dev`**:
  - `db/dev-seed/R__dev_seed.sql` es una migración repetible e idempotente (`ON CONFLICT DO NOTHING`) que `application-dev.yml` añade a `spring.flyway.locations`.
  - Las cuentas del seed usan el dominio `@dev-seed.rehabelite.test` y se crean con un marcador que no es BCrypt.
  - `DevSeedPasswordInitializer` (`@Profile("dev")`) les guarda el BCrypt de `DEV_SEED_PASSWORD` al arrancar. Nunca escribir contraseñas ni hashes en el SQL.
  - Toda cédula nueva del seed debe pasar `NationalId` (lo comprueba `DevSeedDataTests`).
  - `spring.flyway.ignore-migration-patterns` incluye `repeatable:missing` para que una base sembrada arranque sin `dev`.
  - Las pruebas de integración nunca activan `dev` sobre el contenedor compartido (`DevSeedIntegrationTests` usa uno propio).
- El seed `dev` (MEJ-02) da a las cuentas del personal un registro completo y válido (`ON CONFLICT (user_id) DO UPDATE ... WHERE staff_records.national_id IS NULL`: en una base ya migrada a V7 completa los registros vacíos, una sola vez). `DevSeedDataTests` comprueba sus cédulas y teléfonos.
- Desde MEJ-05 el seed tiene la recepción (`recepcion@dev-seed.rehabelite.test`, rol `ADMINISTRATOR`, con su registro completo): 8 cuentas, una de recepción, una de dirección, tres fisioterapeutas y tres pacientes., los horarios de los tres (Andrade de lunes a viernes mañana y tarde, Cevallos tres días largos y el sábado, Paredes solo martes y jueves por la tarde) y ocho historiales con su tratamiento, para que las cargas sean distintas (Andrade 4 activos, Cevallos 2 activos y 1 finalizado, Paredes 1). Cada sentencia es idempotente: franjas solo para un fisioterapeuta sin franjas, historial solo para un paciente sin historial (`ON CONFLICT (patient_id)`), tratamiento solo para un historial sin tratamientos, responsable solo para un tratamiento sin responsable. El texto clínico es genérico y sintético. `DevSeedIntegrationTests` comprueba los horarios, las cargas y que correr el seed otra vez no cambia nada.
- Pruebas de integración: extender `ec.rehabelite.AbstractIntegrationTest` (contexto completo, perfil `test`, un único `postgres:16` de Testcontainers compartido). Los correos nunca salen: `RecordingMailSender` (MEJ-01) los guarda en memoria; `messagesTo(correo)` lee los de una dirección, `awaitMessagesTo` espera los que salen en otro hilo, `tokenOf` extrae el token del enlace y `failFor(correo)` simula un SMTP caído. No asumir base vacía: cada prueba crea sus propios datos con valores únicos. Para pacientes de prueba que no son el objeto de la prueba, usar `ec.rehabelite.TestPatients`: los inserta por SQL, sin auditoría, con cédulas sintéticas de dígito verificador válido y el número de historia igual a la cédula. `TestTreatments` (historial, tratamiento y responsable) y `TestSessions.insert` hacen lo mismo con los tratamientos y las sesiones que la prueba solo necesita leer.

### Endpoints REST

En inglés, plural, kebab-case, bajo `/api`:

`/api/auth`, `/api/users`, `/api/people` y `/api/staff` (MEJ-02), `/api/patients`, `/api/clinical-records`, `/api/treatments`, `/api/therapy-sessions`, `/api/ai-summaries`, `/api/reports`, `/api/audit-logs`, `/api/portal`, `/api/icd10-diagnoses`, `/api/catalogs` (MEJ-03: `GET /api/catalogs/locations`, cualquier usuario autenticado, `Cache-Control: max-age=86400, private`).

Excepciones acordadas en HU-07 (fuera del patrón de recurso plural):
- `POST`, `GET` (HU-09) y `PUT` (HU-09) `/api/patients/{patientId}/clinical-record`: singular, porque es el único historial del paciente.
- `GET /api/physio/patients`: vista de pacientes con datos clínicos, separada de `/api/patients`, que no los incluye. `GET /api/physio/physiotherapists` (HU-08) sigue el mismo criterio.
- HU-08: `PUT /api/treatments/{id}/responsible` y `PATCH /api/treatments/{id}/status` (sub-recursos en singular del tratamiento).
- HU-10: `POST` y `GET /api/treatments/{treatmentId}/therapy-sessions`, porque una sesión siempre pertenece a un tratamiento. La sesión ya creada se maneja en `/api/therapy-sessions/{id}`.
- HU-12 y HU-13: `GET /api/portal/treatments` y sus sub-recursos `GET /api/portal/treatments/{id}/progress`, `/sessions` e `/instructions`: vista de los tratamientos del propio paciente, separada de `/api/treatments` y de `/api/therapy-sessions`, que son del fisioterapeuta.
- HU-14: `GET /api/reports/summary` (singular: los indicadores del reporte), `/api/reports/treatments` y `/api/reports/physiotherapists`: vistas de solo lectura del reporte, separadas de `/api/treatments` y de `/api/physio/physiotherapists`, que son del fisioterapeuta.
- MEJ-01: `POST /api/users/{id}/resend-invitation` (acción sobre la cuenta) y `POST /api/auth/activate`, `/api/auth/forgot-password` y `/api/auth/reset-password` (acciones públicas de autenticación).
- HU-15: `GET /api/reports/summary.pdf`: el mismo reporte como archivo; la extensión distingue el formato (Spring no hace coincidir sufijos, así que es una ruta propia).
- MEJ-02: `GET /api/people/matches` (búsqueda de cédula o correo registrados), `POST /api/patients/{id}/portal-account` (singular: la única cuenta del paciente) y `POST /api/auth/confirm-email` (acción pública de autenticación).
- MEJ-04: `GET` y `PUT /api/staff/{id}/schedule` (singular: el único horario semanal del fisioterapeuta), `GET /api/physio/schedule` (el propio, como `/api/physio/patients`) y el área `/api/operations` de la dirección operativa: `GET /api/operations/schedules` (el tablero), `/availability` (una consulta, no un recurso), `/treatments` y `PUT /api/operations/treatments/{id}/responsible` (vista sin datos clínicos, separada de `/api/treatments`, que es del fisioterapeuta).

Ejemplos: `GET /api/patients?query=...`, `PATCH /api/treatments/{id}/status`, `POST /api/ai-summaries/{id}/approve`.

## Modelo de datos

Fuente de verdad: `backend/src/main/resources/db/migration/` (`V1__initial_schema.sql`, `V2__icd10_initial_catalog.sql`, `V3__envers_audit_tables.sql`: `revinfo`, `treatments_aud`, `therapy_sessions_aud` e índice `ix_audit_logs_occurred_at`; `V4__audit_logs_export_action.sql` (HU-15): agrega `EXPORT` a `ck_audit_logs_action`; `V5__account_activation.sql` (MEJ-01): `users.password_hash` admite nulos, `users.email_verified_at` y `users.session_version`, la tabla `account_tokens` y las acciones nuevas de auditoría; las cuentas existentes quedan verificadas desde su creación; `V6__patient_address_and_insurance.sql` (MEJ-03): `patients.has_health_insurance` (`'Ninguno'` → `false`, lo demás → `true`) en lugar de `health_insurance`, y la dirección estructurada `province_code`, `canton_code`, `parish_code`, `main_street`, `street_number`, `cross_street` y `address_reference` en lugar de `address`, cuyo texto queda en `address_reference`; `ck_patients_address_parts` exige el lugar y las calles juntos o ninguno, y `ck_patients_address_codes`, los códigos anidados; `V7__staff_records_and_email_change.sql` (MEJ-02): la tabla `staff_records` con sus restricciones únicas (`user_id`, `national_id`, `email`) y las de dirección de V6, el registro de cada cuenta del personal existente, `full_name` derivado de los nombres, `users.pending_email` con índice único parcial, el propósito `EMAIL_CHANGE` de `account_tokens` y las acciones `EMAIL_CHANGE` y `EMAIL_CONFIRMATION`; `V8__staff_schedule_slots.sql` (MEJ-04): la extensión `btree_gist`, el tipo de rango `time_range` (con `time_subtype_diff`) y la tabla `staff_schedule_slots` con `ck_staff_schedule_slots_day` (1 a 6), `ck_staff_schedule_slots_order`, `ck_staff_schedule_slots_clinic_hours` (07:00 a 20:00) y `ex_staff_schedule_slots_overlap`; `V9__keep_an_active_operations_director.sql` (MEJ-05): la función `keep_an_active_operations_director()` y el trigger `trg_users_keep_active_director`, sin cambiar datos). `FlywayMigrationTests` comprueba que `ck_audit_logs_action` admite exactamente los valores de `AuditAction`.

- Tablas en inglés, plural, snake_case: `users`, `patients`, `staff_records` (MEJ-02), `staff_schedule_slots` (MEJ-04), `clinical_records`, `icd10_diagnoses`, `treatments`, `responsible_assignments`, `treatment_status_changes`, `therapy_sessions`, `ai_summaries`, `audit_logs`, `account_tokens` (MEJ-01).
- Prefijos: `pk_`, `fk_`, `uq_`, `ck_`, `ix_`, `ex_` (restricciones `EXCLUDE`, MEJ-04); triggers `trg_`.
- Columnas de auditoría: `created_by`, `created_at`, `updated_by`, `updated_at`.
- Función de búsqueda: `normalize_text(text)` (minúsculas sin tildes; índice trigram sobre `patients`).

Valores de enums / CHECK:

| Campo | Valores |
|---|---|
| `users.role` | `ADMINISTRATOR`, `PHYSIOTHERAPIST`, `PATIENT`, `OPERATIONS_DIRECTOR` |
| `patients.sex`, `staff_records.sex` | `MALE`, `FEMALE` |
| `treatments.status` | `ACTIVE`, `COMPLETED`, `ABANDONED` |
| `treatments.severity` | `MILD`, `MODERATE`, `SEVERE` |
| `therapy_sessions.status` | `COMPLETED`, `RESCHEDULED`, `NO_SHOW` |
| `therapy_sessions.progress_status` | `STABLE`, `IMPROVING`, `NEEDS_ATTENTION` |
| `ai_summaries.status` | `PENDING_REVIEW`, `APPROVED`, `DISCARDED` |
| `audit_logs.action` | `CREATE`, `READ`, `UPDATE`, `STATUS_CHANGE`, `ACCESS_DENIED`, `LOGIN`, `EXPORT` (V4, HU-15), `INVITATION_SENT`, `INVITATION_RESENT`, `ACTIVATION`, `RESET_REQUEST`, `PASSWORD_RESET` (V5, MEJ-01), `EMAIL_CHANGE`, `EMAIL_CONFIRMATION` (V7, MEJ-02; máximo 20 caracteres, el largo de la columna) |
| `account_tokens.purpose` | `ACTIVATION`, `PASSWORD_RESET`, `EMAIL_CHANGE` (V7, MEJ-02) |

## Reglas de datos y seguridad (no negociables)

1. Todo cambio de esquema es una **migración Flyway nueva** (`V<n>__description.sql`, en inglés).
2. Nunca `ddl-auto: update` (se usa `validate`).
3. Nunca editar una migración ya aplicada.
4. No borrar datos clínicos: se cambia su estado (p. ej. tratamiento `ABANDONED`, usuario `active = false`).
5. La edad **no se almacena**: se calcula desde `birth_date`.
6. El fisioterapeuta responsable y el `sequence_number` de la sesión los **asigna el servidor**, nunca el cliente.
7. `audit_logs` solo admite `INSERT` (un trigger bloquea UPDATE/DELETE/TRUNCATE).
8. Contraseñas con BCrypt.
9. Bloqueo de cuenta 15 minutos tras 5 intentos fallidos.
10. Toda respuesta 403 registra `ACCESS_DENIED` en `audit_logs`.
11. Secretos **solo** en `.env` (raíz, ignorado por Git). `.env.example` lleva solo valores de ejemplo.
12. Datos de prueba siempre sintéticos; las cédulas deben tener dígito verificador válido (módulo 10 ecuatoriano).

### Variables de entorno

`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `POSTGRES_DB` (solo para el PostgreSQL de Docker), `JWT_SECRET` (≥ 32 caracteres), `JWT_EXPIRATION_MINUTES`, `INITIAL_DIRECTOR_EMAIL`, `INITIAL_DIRECTOR_PASSWORD` (MEJ-05, reemplazan a `ADMIN_EMAIL` y `ADMIN_PASSWORD`), `ANTHROPIC_API_KEY`, `CORS_ALLOWED_ORIGINS`, `DEV_SEED_PASSWORD` (solo perfil `dev`) y, desde MEJ-01, `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM` y `APP_FRONTEND_URL` y, desde TEC-16, `MAIL_TRANSPORT` (`smtp` o vacío en desarrollo, `api` en producción) y `BREVO_API_KEY` (obligatoria con `api`).

Se mapean a `app.jwt.secret`, `app.jwt.expiration-minutes`, `app.initial-director.email`, `app.initial-director.password`, `app.cors.allowed-origins`, `app.ai.anthropic-api-key`, `spring.mail.host`, `spring.mail.port`, `spring.mail.username`, `spring.mail.password`, `app.mail.from`, `app.mail.transport`, `app.mail.brevo.api-key`, `app.frontend-url` (base de los enlaces de los correos) y, solo en `application-dev.yml`, `app.dev-seed.password`. Sin valor, el correo va a `localhost:1025` (Mailpit) y los enlaces a `http://localhost:4200`; en producción hay que definirlas. Las duraciones de los enlaces son `app.tokens.activation-ttl` (48h) y `app.tokens.reset-ttl` (1h), en `application.yml`. La jornada de la clínica (MEJ-04) tampoco es una variable de entorno: `app.clinic.opening-time` y `app.clinic.closing-time` en `application.yml`.

## Frontend (Angular 22 + Material)

- Requiere Node.js `^22.22.3`. `npm start` sirve en `http://localhost:4200` y redirige `/api` a `http://localhost:8080` (`proxy.conf.json`).
- Carpetas: `src/app/core` (`AuthService` con login y sesión en signals, `authGuard`/`roleGuard`, `authInterceptor`, roles, locale `es-EC`), `src/app/shared/layout` (`AppShell`, `nav-items.ts`, `PlaceholderPage`), `src/app/shared/ui` (componentes reutilizables), `src/app/shared/clinical` (etiquetas clínicas que comparten varias áreas), `src/app/features/auth`, `features/admin`, `features/physiotherapist`, `features/director`, `features/patient`, `features/dev` (solo desarrollo).
- Rutas: `/login`, las páginas públicas de los enlaces de las cuentas (MEJ-01: `/activar-cuenta`, `/restablecer-contrasena` y `/olvide-contrasena`; MEJ-02: `/confirmar-correo`; en español por la excepción de la regla de idioma) y, dentro de `AppShell`, un `*.routes.ts` por rol: `/physio` (`patients`, `clinical-records`, `timeline`, `schedule` (MEJ-04), `ai-assistant`), `/admin` (`patients`, `patients/:id`, `audit`; `/admin/people`, `/admin/users` y `/admin/people/staff/:id` redirigen a `patients` y `/admin/people/patients/:id` a `patients/:id`; sin `reports`, HU-14), `/director` (`reports`, `staff` y `staff/:id` (MEJ-05), `schedules` (MEJ-04), `audit`), `/patient`. Cada rol protegido por `authGuard` + `roleGuard(rol)` y cargado de forma diferida. Las secciones sin historia implementada usan `PlaceholderPage` (título y subtítulo en `data`); la historia reemplaza ese `component` por su página real.
- Componentes de `shared/ui` (standalone, con pruebas): `page-header`, `stat-card` (variante `compact` para filas de cuatro tarjetas y `stacked`, con la etiqueta a todo el ancho y el icono junto al valor, para filas de cinco), `status-badge`, `data-table` (paginación en cliente, o en servidor con `[totalItems]` y `[(pageIndex)]`; `appDataTableCell` para celdas personalizadas, `[appDataTableCellOf]="filas"` tipa `let-row`, y `wrapHeader: true` en una columna deja que su encabezado se parta en líneas), `form-section`, `empty-state` (admite varias acciones, separadas), `confirm-dialog` (`openConfirmDialog()` devuelve `Observable<boolean>`) y `hero-banner`. Usarlos antes de crear componentes nuevos. Catálogo visual en `/ui-kit` (solo `isDevMode()`).
- Diseño: seguir `docs/prototype/`. Capturas de comparación por tarea en `docs/screenshots/<tarea>/`.
- Nombres según la guía de estilo de Angular CLI 22, en inglés: archivo `patient-list.ts`, clase `PatientList`, selector `app-patient-list`; guards/interceptores `*-guard.ts` / `*-interceptor.ts`. Textos de la interfaz en español.
- Tema: tokens CSS `--re-*` definidos en `src/styles/_tokens.scss` (colores, grises, estados, radios, sombras, espaciados, tipografía Plus Jakarta Sans): `--re-navy` (#0F172A, barra lateral y cabeceras), `--re-teal` (#14B8A6, acción primaria e ítem activo **con texto navy**, contraste 7:1), `--re-text-teal` (#0F766E, texto teal sobre blanco), `--re-field` (#F3F4F6, campos), `--re-card` (tarjetas blancas, `--re-radius-lg` 16px). Usar siempre tokens, nunca colores sueltos; no poner texto blanco sobre `#14B8A6` (contraste 2.5:1).
- Nuevas entradas de menú por rol en `shared/layout/nav-items.ts`.
- Acceso por rol (HU-04):
  - `roleGuard(rol)` protege cada grupo de rutas. Si el rol no coincide, redirige al panel del rol del usuario y `AppShell` muestra el aviso "No tiene acceso a esa sección." (`SectionAccessNotice`).
  - Un 403 de la API lleva a `/access-denied` (`AccessDeniedPage`) sin cerrar la sesión.
  - Las rutas desconocidas van al panel del rol.
- Sesión: `AuthService` guarda token, expiración y usuario (`sessionStorage`, o `localStorage` con "Recordarme"); el usuario actual se lee con `user()` / `role()`. En pruebas unitarias iniciar sesión con `logInForTest(rol, nombre?, id?)` de `core/auth/auth-testing.ts`.
- Errores de la API: mostrar el `detail` en español con `problemDetailMessage(error, textoAlternativo)` de `core/http/problem-detail.ts`; en una descarga (`responseType: 'blob'`) el error llega como Blob y se lee con `blobProblemDetailMessage` (asíncrona). Las páginas de resultados usan el tipo `Page<T>` de `core/http/page.ts`. Los archivos de la API se guardan con `FileDownload` (`core/http/file-download.ts`).
- `AppShell` vuelve al inicio el área de contenido (`mat-sidenav-content`, que es la que se desplaza) en cada navegación.
- Historial clínico del fisioterapeuta (HU-07):
  - `/physio/patients` (`PhysioPatientList`): tabla paginada en el servidor con el resumen del tratamiento.
    - Las acciones de cada fila están en un menú (`mat-menu`, SCRUM-51), que abre un botón de icono con el `aria-label` "Acciones de {paciente}". En botones no caben: la tabla se salía de su tarjeta. No volver a ponerlas como botones en la fila.
    - El menú ofrece "Ver historial" o "Crear historial", "Nuevo tratamiento" si no hay tratamiento activo y, en cada fila con tratamiento, "Plan y asignación" y "Línea de tiempo" (HU-11, la de su tratamiento vigente).
    - El nombre del paciente es un enlace a la acción principal: el expediente o, si no tiene historial, su creación.
  - `PhysioPatientSearch` (`patients/physio-patient-search.ts`) es la búsqueda de `GET /api/physio/patients` (espera de 300 ms, paginación en el servidor, carga y error en signals). La comparten `PhysioPatientList` y `TimelineEntry`; se crea en un inicializador de campo del componente. Los estilos de las filas están en `patients/_patient-rows.scss`.
  - `ClinicalRecordForm` sirve dos rutas:
    - `/physio/patients/:patientId/clinical-record/new`: cuatro secciones.
    - `/physio/patients/:patientId/clinical-record/:recordId/treatments/new`: sin "Hábitos y antecedentes".
  - `Icd10Picker` es un control de formulario (`ControlValueAccessor`) con autocompletado. Su valor es el `Icd10Diagnosis` elegido de la lista, o `null`.
  - `PatientSummaryCard` es la tarjeta del paciente (prototipo 04) de las páginas clínicas. Recibe un `SummaryCardPatient`, que cumplen tanto el paciente de `GET /api/patients/{id}` como el del expediente. Con `[showContact]="true"` añade la fecha de ingreso, el correo, la dirección y el seguro.
  - `CLINICAL_HISTORY_FIELDS` e `initialsOf()` viven en `clinical-labels.ts`. `painScoreTone()`, `TreatmentStatus` y las etiquetas y tonos del estado del tratamiento viven en `shared/clinical/treatment-labels.ts`, porque también los usa el portal del paciente; `clinical-labels.ts` los reexporta. Un área (`features/patient`) nunca importa de otra (`features/physiotherapist`): lo compartido va a `shared/`.
- Plan terapéutico y asignación (HU-08):
  - `/physio/patients/:patientId/treatments/:treatmentId` (`TreatmentPage`): tarjeta del paciente y `TreatmentPlanCard`. Si el tratamiento es de otro paciente, muestra un aviso. La lista enlaza aquí con "Plan y asignación".
  - `TreatmentPlanCard` ("Plan terapéutico y asignación clínica") carga el historial con `rxResource` y tiene las acciones "Cambiar responsable" (solo tratamientos activos) y "Cambiar estado" (solo si hay destinos permitidos). La vista del expediente (HU-09) no la reutiliza: muestra una versión de solo lectura con los datos de su única consulta y enlaza aquí con "Plan y asignación".
  - `ChangeStatusDialog` ofrece solo los estados de `allowedNextStatuses` y pide un motivo obligatorio. `ChangeResponsibleDialog` lista los fisioterapeutas activos, excepto el responsable vigente.
  - En pruebas, un componente con `rxResource` pendiente nunca queda estable: llamar `TestBed.tick()` para lanzar la petición, responderla y solo después `whenStable()`.
- Expediente del paciente (HU-09):
  - `/physio/patients/:patientId/clinical-record` (`ClinicalRecordView`) es de solo lectura (prototipo 04) y se arma con una sola petición. Muestra:
    - la tarjeta del paciente con contacto;
    - "Información diagnóstica" (con la gravedad);
    - "Evaluación del dolor" (EVA con color según `painScoreTone`);
    - "Plan terapéutico y asignación clínica" con el responsable;
    - "Hábitos y antecedentes" (con la última edición);
    - "Tratamientos anteriores".
  - Sin tratamiento activo, ofrece "Nuevo tratamiento". Sin historial (404), ofrece "Crear historial".
  - "Editar historial" abre `/physio/patients/:patientId/clinical-record/edit`.
  - "Ver sesiones" abre las sesiones del tratamiento activo o, si no hay, del más reciente. La tarjeta del plan activo tiene "Registrar sesión", y cada tratamiento anterior tiene un enlace "Sesiones" (HU-10).
  - "Línea de tiempo" (HU-11) está en la cabecera (mismo tratamiento que "Ver sesiones"), en la tarjeta del plan activo y en cada tratamiento anterior.
  - `ClinicalRecordForm` tiene tres modos:
    - `record` y `treatment`, los de HU-07;
    - `edit`, que se activa con `data: { mode: 'edit' }` en la ruta y muestra solo "Hábitos y antecedentes", precargado desde el expediente, con `PUT`.
  - Al guardar en cualquier modo, el formulario abre el expediente.
  - La lista del fisioterapeuta muestra "Ver historial" a los pacientes que ya tienen historial.
- Sesiones terapéuticas (HU-10), en `features/physiotherapist/sessions`:
  - `/physio/patients/:patientId/treatments/:treatmentId/sessions` (`TherapySessionList`):
    - tabla paginada en el servidor (10 por página) con N.º, fecha y hora, tipo, EVA, estado, evolución y responsable;
    - "Corregir" aparece solo en las sesiones del usuario autenticado (`responsible.id`), y "Ver versiones" en todas;
    - "Registrar sesión" aparece solo si el tratamiento está activo.
  - `TherapySessionForm` (prototipo 06) sirve dos rutas:
    - `…/sessions/new` registra la sesión y vuelve desde el expediente;
    - `…/sessions/:sessionId/edit` corrige con `PUT`. Si la sesión es de otro fisioterapeuta, muestra el aviso "Solo el fisioterapeuta que registró la sesión puede corregirla." con el formulario deshabilitado.
  - En el formulario, el paciente, el número de sesión y el especialista responsable son de solo lectura. Al registrar, el número que se muestra es el siguiente (total de sesiones + 1) y lo confirma el servidor.
  - Al guardar, el formulario abre la lista. En un tratamiento que no está activo, no permite registrar.
  - `SessionVersionsDialog` (`openSessionVersionsDialog`) muestra las versiones de la más reciente a la más antigua, con su autor y fecha.
  - Etiquetas de estado, evolución e intensidad de la EVA en `session-labels.ts`. `SessionStatus` y las etiquetas y tonos del estado de la sesión ("Realizada", "Reprogramada", "No asistió") viven en `shared/clinical/session-labels.ts`, porque también los usa el portal del paciente; `session-labels.ts` los reexporta.
  - La lista tiene "Línea de tiempo" en la cabecera, sea cual sea el estado del tratamiento.
- Línea de tiempo terapéutica (HU-11), en `features/physiotherapist/sessions`:
  - `/physio/patients/:patientId/treatments/:treatmentId/timeline` (`TherapyTimeline`, prototipo 05) se arma con `GET /api/treatments/{id}/timeline` y `GET /api/patients/{id}` (cédula y nombre; datos personales, no se auditan). Muestra:
    - la barra del paciente con la patología, el inicio, la EVA inicial y "Progreso: N / M Sesiones Planificadas";
    - el filtro "Desde" / "Hasta" con "Limpiar", como en la auditoría: cada cambio recarga; con "Desde" posterior a "Hasta" no busca y solo muestra el aviso;
    - "Curva de EVA por sesión" (`PainCurveChart`) cuando hay al menos una sesión realizada; si no, un aviso;
    - una tarjeta por sesión sobre un riel vertical: "Sesión NN", fecha, hora, tipo, responsable, EVA, estado, evolución, actividades, observaciones clínicas (interno) e indicaciones para el paciente;
    - el estado vacío "Aún no hay sesiones registradas" o, con filtro, "No hay sesiones en ese rango de fechas".
  - "Registrar Sesión de Hoy" solo si el tratamiento está activo; "Ver sesiones" siempre. Si el tratamiento es de otro paciente, muestra un aviso.
  - `/physio/timeline` (`TimelineEntry`, "Línea de Tiempo Terapéutica") es la entrada del menú. La línea de tiempo pertenece a un tratamiento, así que esta página sirve para elegir al paciente:
    - usa `GET /api/physio/patients` tal cual, con su búsqueda por nombre o cédula, su paginación y su total (`PhysioPatientSearch`). No hay endpoint propio;
    - un paciente con tratamiento muestra el diagnóstico, las sesiones planificadas y el estado, y "Abrir línea de tiempo" abre la de su tratamiento vigente (el activo o, si no hay, el más reciente);
    - un paciente sin tratamiento aparece con la etiqueta "Sin tratamiento" y sin acción;
    - no muestra las sesiones realizadas: ese endpoint no las devuelve;
    - el estado vacío es "No hay pacientes registrados"; una búsqueda sin resultados muestra "Ningún paciente coincide con la búsqueda.".
  - `PainCurveChart` envuelve `BaseChartDirective` de **ng2-charts 11** (compatible con Angular 22) y **chart.js 4**. Registra solo lo que usa una línea (`provideCharts` en el componente). Es una sola serie: línea de 2 px en `--re-text-teal`, puntos de 8 px, la evaluación inicial hueca, eje EVA fijo de 0 a 10, tooltip con punto, fecha e intensidad, y sin leyenda. Los colores y la fuente se leen de los tokens `--re-*` con `getComputedStyle`, porque el canvas no entiende variables CSS. Una tabla `re-visually-hidden` repite los puntos para lectores de pantalla.
  - En pruebas, `stubCanvas()` de `session-testing.ts` simula el contexto 2D (jsdom no tiene canvas) y `ResizeObserver`, para que Chart.js cree el gráfico de verdad. Devuelve la función que restaura el original.
- Portal del paciente (HU-12, HU-13), en `features/patient`:
  - `/patient` (`PatientDashboard`, prototipo 14) se arma con `PortalApi`: `GET /api/portal/treatments` y, para el tratamiento elegido, tres peticiones en paralelo (`forkJoin`): `/progress`, `/sessions` e `/instructions`. Ninguna petición lleva el id del paciente. Si una de las tres falla, no se muestra nada a medias: solo el aviso con "Reintentar". Muestra:
    - el banner (`HeroBanner`) con el saludo y el mensaje "Tu tratamiento para {diagnóstico} está a cargo de {fisioterapeuta}."; si el tratamiento no está activo, "finalizó" o "figura como abandonado". El sistema no afirma que el tratamiento "avanza satisfactoriamente", como dice el prototipo;
    - el selector "Tratamiento" (`mat-select`), solo si hay más de uno; el activo es el primero y queda elegido;
    - `TreatmentProgressCard` ("Progreso del tratamiento"): anillo SVG con las sesiones realizadas frente a las planificadas y "¡Completado un N% de tu plan!";
    - `PainEvolutionCard` ("Evolución del dolor (escala EVA)"): una barra por punto de la curva ("Evaluación inicial", "Sesión 01"...), con el color de `painScoreTone`, la etiqueta "Disminución de 7/10 a 4/10" y "Reducción de dolor lograda: N puntos". Si la EVA sube: "Aumento de..." y "El dolor aumentó N puntos respecto a la evaluación inicial."; si no cambia: "Sin cambios respecto a la evaluación inicial."; sin sesiones realizadas: "Aún no hay sesiones realizadas.". Son barras, como en el prototipo, no la curva de Chart.js de HU-11.
    - `TherapistInstructionCard` ("Indicación del terapeuta", HU-13): "Fisioterapeuta: {nombre}", la última indicación entre comillas, su sesión y fecha ("Sesión 06 · 05 de noviembre") y el pie "Sigue estas pautas para consolidar la mejoría entre sesiones.". Sin indicaciones: "Tu fisioterapeuta aún no ha registrado indicaciones.";
    - `PortalSessionList` ("Listado de sesiones", HU-13): la etiqueta "Total realizadas: N" (solo las `COMPLETED`) y un `app-data-table` de 10 filas por página con las columnas "Sesión y fecha" (con la hora y el tipo), "Fisioterapeuta responsable", "Intensidad EVA", "Actividades realizadas" y "Estado" ("Realizada", "Reprogramada", "No asistió"). Las indicaciones de cada sesión van debajo de sus actividades, con el prefijo "Indicación:".
  - Las tres tarjetas van en una fila, como en el prototipo. Con menos de 1150 px de ancho de ventana quedan dos y la indicación debajo; con menos de 760 px, una por fila.
  - **La tabla de sesiones no se desborda:** los textos largos se ajustan en varias líneas (`.wrap`, `overflow-wrap: anywhere`) y los encabezados de dos palabras pueden partirse (`wrapHeader`). Medido en el navegador: 850 px de tabla en 850 px de contenedor a 1366 y a 1920 px, y sin desborde también a 1024 px. A 390 px la tabla se desplaza dentro de su tarjeta, sin desbordar la página. Vitest corre en jsdom, que no calcula el diseño: su prueba protege la estructura, y los anchos se miden en el navegador al tomar las capturas.
  - Estados: "Aún no tienes un tratamiento registrado" (lista vacía), "Tu cuenta aún no está vinculada a un paciente" (404) y un aviso con "Reintentar" si falla la carga.
  - **Solo lectura:** la página no tiene enlaces, campos ni botones que cambien datos; solo el selector, el paginador de la tabla y "Reintentar". Tampoco notificaciones ni próxima cita (fuera del alcance).
  - Datos de prueba en `portal-testing.ts` (`fakeTreatment`, `fakeProgress`, `fakeCurve`, `fakeSession`, `fakeInstruction`).
- Reportes de dirección operativa (HU-14, HU-15), en `features/director/reports`:
  - `/director/reports` (`OperationsReport`, prototipo 13) se arma con `ReportApi`: `GET /api/reports/summary`, `GET /api/reports/treatments` y, para el filtro, `GET /api/reports/physiotherapists`. Muestra:
    - el encabezado con el botón "Exportar informe operativo" (HU-15) y el aviso "Modo de Dirección Operativa: acceso de solo consulta…";
    - "Filtros operativos": "Estado del tratamiento" y "Fisioterapeuta responsable" (`mat-select`) y, debajo, "Desde" y "Hasta" (`<input type="date">`, HU-15, cada una con un botón para borrarla), con la nota de cómo se filtran los tratamientos y las sesiones. La opción "Todos" vale `''`, no `null`: `mat-select` no muestra el texto de una opción con valor `null`;
    - cinco `stat-card stacked`: tratamientos activos, finalizados, abandonos, sesiones registradas (con "N realizadas · N reprogramadas · N no asistió" al pie) y sesiones por fisioterapeuta (el promedio, con coma decimal de `es-EC`). Las filas de las cinco tarjetas se alinean con `grid-template-rows: subgrid`;
    - `SessionsByPhysiotherapistCard` ("Sesiones por fisioterapeuta"): iniciales, nombre, total y una barra proporcional al que más tiene, con la etiqueta "Promedio: N ses/esp";
    - `ReportTreatmentList` ("Tratamientos (N)", con los filtros aplicados y, con rango, "• Periodo: …" y la nota "El estado corresponde al estado actual de cada tratamiento."): `app-data-table` paginado en el servidor, 5 por página para que mida casi lo mismo que la tarjeta de al lado, con paciente, diagnóstico, responsable, estado e inicio. Sin cédula.
  - Cada cambio de filtro recarga el resumen y la primera página; un cambio de página solo recarga la lista. Con "Desde" posterior a "Hasta" no busca: muestra solo el aviso, como la auditoría. Si falla el resumen o la lista, se muestra el aviso con "Reintentar"; si falla la lista de fisioterapeutas, el reporte carga igual.
  - **Exportar (HU-15):** el botón descarga `GET /api/reports/summary.pdf` con los filtros aplicados, los mismos que muestran los indicadores y la lista (`ReportApi.exportPdf`, `responseType: 'blob'`). Está deshabilitado mientras carga el reporte, mientras exporta ("Exportando…") y con un rango inválido. El archivo se llama `informe-operativo-AAAA-MM-DD.pdf` (fecha de Quito) y se guarda con `FileDownload` (`core/http/file-download.ts`: enlace temporal con `download` y `URL.revokeObjectURL`). Si falla, muestra el `detail` de la API, que llega como Blob y se lee con `blobProblemDetailMessage` (`core/http/problem-detail.ts`).
  - **Solo lectura:** la página no tiene enlaces ni botones que cambien datos; solo los filtros (dos selects y dos fechas), "Exportar informe operativo", el paginador y "Reintentar". Sin notificaciones.
  - Medido en el navegador: sin desborde de la página, de la tabla ni de las etiquetas de los indicadores a 1366, 1568, 1920, 1024 y 390 px.
  - Datos de prueba en `report-testing.ts` (`fakeSummary`, `fakeReportTreatment`, `fakeTreatmentPage`, `FAKE_PHYSIOTHERAPISTS`).
  - El administrador no tiene "Reportes Consolidados" en su menú ni la ruta `/admin/reports` (HU-06, HU-14): el prototipo 12 queda superado.
- Auditoría (HU-18):
  - `features/audit/AuditLogList` se reutiliza en `/admin/audit` y `/director/audit`, con la entrada "Auditoría" en el menú de ambos roles.
  - Tabla paginada en el servidor (20 por página) con fecha y hora de Quito, usuario, acción (badge), entidad con id e IP. Solo metadatos.
  - Filtros por usuario (`GET /api/audit-logs/users`), acción, entidad (`/entity-types`) y rango de fechas. Cada cambio recarga la primera página. Con "Desde" posterior a "Hasta" no busca y solo muestra el aviso.
  - Etiquetas en `audit-labels.ts` (`Report` → "Reporte"; la acción `EXPORT` → "Exportación", HU-15; MEJ-01: "Invitación enviada", "Invitación reenviada", "Activación de cuenta", "Solicitud de restablecimiento" y "Restablecimiento de contraseña"; MEJ-02: "Cambio de correo", "Confirmación de correo" y la entidad `StaffRecord` → "Personal"). Un `ACCESS_DENIED` muestra el área de la ruta tal cual (p. ej. `patients` o `portal/treatments`).
- Cuentas (MEJ-01, MEJ-02), en `shared/people` y `features/auth`:
  - **"Gestión de Usuarios" ya no existe** (MEJ-02, ajuste A): sus indicadores, el último acceso y sus acciones pasaron a las listas de personas y a las fichas. Se eliminaron `UserList`, `user-roles.ts` y `CreateUserDialog`; `UserApi` (`changeStatus`, `resendInvitation`), `last-access.ts` y `user-testing.ts` viven en `shared/people` (MEJ-05).
  - `AccountActions` (`account-actions.ts`) concentra las acciones de una cuenta para la lista y las fichas: activar o desactivar tras la confirmación de siempre, y reenviar el enlace pendiente, con sus avisos y el mensaje de la API si fallan.
  - `ConfirmEmailPage` (`/confirmar-correo`, MEJ-02) confirma el correo nuevo con un clic en "Confirmar mi nuevo correo", nunca al cargar (un antivirus de correo que abre el enlace no lo gasta), con la misma protección del token (`takeLinkToken`).
  - `SetPasswordPage` sirve `/activar-cuenta` y `/restablecer-contrasena` (`data: { mode: 'activate' | 'reset' }`): contraseña, confirmación y las reglas visibles, cada una con su marca al cumplirse (`passwordRules`). Al terminar, ofrece "Ir a iniciar sesión". Si el enlace no sirve (falta el token o la API lo rechaza), lo dice y, en el restablecimiento, ofrece "Solicitar un enlace nuevo".
  - **Protección del token** (`features/auth/link-privacy.ts`, `takeLinkToken()`): mientras la página está abierta pone `<meta name="referrer" content="no-referrer">` en el `head` (y la quita al salir), para que ninguna petición mande el token en el `Referer`; lee el token una vez y, tras el primer render (cuando el router ya no reescribe la URL), lo quita de la barra con `Location.replaceState` (`history.replaceState`). El token queda solo en memoria, nunca en `sessionStorage` ni `localStorage`. Si el usuario recarga, el enlace ya no está: debe abrirlo de nuevo desde el correo.
  - `ForgotPasswordPage` (`/olvide-contrasena`) pide el correo y muestra la respuesta de la API, la misma exista o no la cuenta. El login tiene el enlace "¿Olvidó su contraseña?".
  - Las tres páginas usan la tarjeta del login (`features/auth/_auth-card.scss`, clases `auth-page` y `auth-card`). `AccountApi` (`features/auth/account-api.ts`) llama a los endpoints públicos.
- Horarios y asignaciones (MEJ-04), en `src/app/shared/schedules` (lo comparten las tres áreas) y `features/director/schedules`:
  - `ScheduleApi` (`ofStaff`, `replace`, `own`), `schedule-rules.ts` (`WORK_DAYS`, `DAY_LABELS`, `DAY_SHORT_LABELS`, `formatRange`, `slotsByDay`, `weeklyHours` y `slotProblems`, las reglas de la API para avisar en el formulario) y `schedule-testing.ts` (`slot`, `fakeSchedule`).
  - `WeeklySchedule` (`app-weekly-schedule`): el horario de solo lectura, una fila por día de lunes a sábado ("Sin atención" sin franjas) y el total semanal.
  - `ScheduleEditorDialog` (`openScheduleEditorDialog(dialog, horario)`): "Editar horario", una fila por franja con día (`mat-select`) y desde/hasta con **`mat-timepicker`** (cada 30 minutos, acotado a la jornada), "Agregar franja" y quitar. Cada fila muestra su problema en cuanto lo tiene (superposición, fuera de la jornada, invertida) y no guarda hasta corregirlo; avisa que las franjas seguidas se unen al guardar. Cierra con el horario guardado.
  - `StaffSchedulePanel` (`app-staff-schedule-panel`): la sección "3. Horario" de la ficha de un **fisioterapeuta** en `/director/staff/:id` (`StaffDetail`, MEJ-05), con la jornada, el horario y "Editar horario". Las fichas de recepción y dirección no la tienen.
  - `mat-timepicker` necesita `parse.timeInput`, `display.timeInput` y `display.timeOptionLabel` en `MAT_DATE_FORMATS`: `EC_DATE_FORMATS` los define con reloj de 24 horas (`ec-date-adapter.ts`, con `toHourMinute` y `fromHourMinute` para pasar de `Date` a `HH:mm`).
  - `/physio/schedule` (`MySchedule`, menú "Mi Horario"): el horario propio, de solo lectura, con la nota de que lo registran la administración y la dirección operativa.
  - `/director/schedules` (`SchedulesPage`, menú "Horarios y asignaciones"): el aviso de que la vista no muestra datos clínicos, `ScheduleBoard` y `AssignmentList` (`AssignmentsApi`):
    - `ScheduleBoard`: en pantallas anchas, una fila por fisioterapeuta (nombre, "N tratamientos activos", "Cuenta inactiva" y "Editar horario") y una columna por día, con cada franja como un bloque sobre la jornada y su rango; con menos de 1024 px, los mismos datos como una lista por día ("Nadie atiende este día.") y los botones "Editar horario" de cada uno, sin desplazamiento lateral. Las dos vistas se alternan con CSS.
    - `AssignmentList`: "Tratamientos activos (N)", paginada en el servidor (10), con paciente e inicio, responsable vigente ("Responsable inactivo" si su cuenta está desactivada), días del horario y "Reasignar"; el filtro "Todos" / "Responsable inactivo" vuelve a la primera página. Sin diagnóstico ni datos clínicos.
    - `ReassignDialog` ("Reasignar fisioterapeuta"): día (por defecto el primero del horario del responsable vigente) y desde/hasta con `mat-timepicker`; busca al abrir y con "Buscar disponibles", y lista solo a los disponibles (radio), de menor a mayor carga, con su carga y sus franjas del día. El responsable vigente aparece deshabilitado; un rango invertido no busca. Al reasignar, la página recarga el tablero y la lista.
  - Datos de prueba en `features/director/schedules/assignments-testing.ts`. En pruebas, los componentes con `rxResource` se responden después de `TestBed.tick()` y antes de `whenStable()`.
- Validaciones compartidas en `src/app/shared/validation`:
  - `national-id.ts` aplica las mismas reglas de cédula que el backend: `nationalIdValidator` da `{ nationalId: regla }` y `NATIONAL_ID_MESSAGES` tiene el mensaje de cada regla.
  - `password.ts` (MEJ-01): `PASSWORD_VALIDATORS` (la regla de la API), `passwordsMatchValidator` (marca `mismatch` en la confirmación) y `passwordRules()`, las reglas visibles con su estado.
  - `date-range.ts` (HU-15): `dateRangeValidator`, validador de un grupo con `from` y `to` (`yyyy-MM-dd`) que da `{ range: true }` si "Desde" es posterior a "Hasta", y `DATE_RANGE_MESSAGE`, el mismo texto de la API. Lo usan la auditoría y los reportes.
- Separación de funciones (MEJ-05, Jira RHE-89) en el frontend:
  - La recepción abre `/admin/patients` (`PatientRegistry`, ver "Separación de funciones"): los cuatro indicadores de los pacientes (`GET /api/people/stats`, se recargan tras cada acción sobre una cuenta), la búsqueda (espera de 300 ms), el filtro "Acceso al portal" y `PeopleTable`, con las columnas Persona y rol (enlace a su ficha, "Datos por completar"), Contacto, Cuenta (estado, "Correo por confirmar" y "Último acceso") y Acciones (`resend-action`, `status-action` y "Editar"). Nunca datos clínicos.
  - Fichas: `/admin/patients/:id` (`PatientDetail`, de la recepción) y `/director/staff/:id` (`StaffDetail`, de la dirección, con "Datos por completar" en un registro anterior a MEJ-02). Ambas usan `PersonAccountCard`: el paciente sin cuenta ve "Crear acceso al portal" (confirmación con el correo al que irá el enlace; nada se escribe); con cuenta, su estado, el último acceso, "Reenviar invitación" si está pendiente o "Reenviar verificación" con un correo por confirmar, y "Activar cuenta" o "Desactivar cuenta" (nunca la propia).
  - `PersonFormDialog` (`openPersonFormDialog(dialog, { patient?, staff?, type? })`, emite `{ type: 'PATIENT', patient }` o `{ type: 'STAFF', staff }`), "Registrar usuario" (o "Editar usuario"), es un `mat-stepper` lineal con Anterior y Siguiente fijos al pie; Siguiente marca los campos del paso y avanza solo si es válido, y Enter en un campo equivale a Siguiente. Al registrar empieza en el **paso 0, "Tipo de usuario"** (`PersonKindStep`): Paciente o Personal y, para Personal, el rol. El paciente sigue los pasos de abajo; el personal, Identificación (sin N.º de historia), Datos personales y contacto, y Revisión, que avisa que se creará la cuenta y se enviará el enlace. En edición no hay paso 0 y el rol no cambia.
    1. **Identificación:** la cédula primero, validada mientras se escribe (el N.º de historia clínica en vivo solo para pacientes). Una validación asíncrona (pausa de 300 ms) consulta `GET /api/people/matches?nationalId=` y, si un paciente o un miembro del personal la tiene, muestra el aviso con "Ver registro existente" (enlace a su ficha) y bloquea el paso. El 409 del servidor sigue como respaldo y vuelve a este paso (o al del correo, si `field` es `email`).
    - **Correo** (MEJ-02, `GET /api/people/matches?email=`): para el personal, cualquier coincidencia bloquea; para un paciente, bloquea el correo de un miembro del personal y solo avisa si otros pacientes lo comparten ("Puede guardar igual; solo uno de ellos podrá tener acceso al portal"). Al editar el correo de una persona con cuenta, el paso explica lo que hará la cuenta (enlace de activación, enlace de confirmación y aviso al anterior, o reactivar antes).
    2. **Datos personales y contacto:** nombres, apellidos, fecha de nacimiento con la edad al lado, sexo, teléfono, correo y la dirección estructurada.
    3. **Datos de admisión:** fecha de ingreso (hoy), ocupación, nivel de instrucción, "¿Tiene seguro de salud?" (Sí/No) y el panel plegable "Datos opcionales" (etnia y religión, con la nota de que son opcionales).
    4. **Revisión:** cada sección con su resumen y "Editar", y el botón "Registrar".
    - Los errores aparecen solo después de tocar el campo o de pulsar Siguiente, nunca al abrir el diálogo.
    - En edición, el stepper no es lineal, la cédula es de solo lectura y un paciente con datos por completar muestra el aviso; el formulario no guarda hasta completar la dirección y el teléfono.
  - `PatientDetail` muestra la ficha con la dirección compuesta (o "Dirección incompleta"), "Seguro de salud: Sí/No", la última actualización, el acceso al portal y el botón "Editar datos". Se abre tras registrar y desde el aviso de cédula existente.
  - La edad del formulario se calcula con `ageOn()` de `patient-age.ts`.
- Campos compartidos de formularios (MEJ-03), en `src/app/shared/forms`, usados en el registro y en la edición:
  - `app-date-field` (`DateField`): `mat-datepicker` con escritura y calendario, dd/mm/aaaa; el control guarda `yyyy-MM-dd`, el formato de la API. `EcDateAdapter` (`ec-date-adapter.ts`, registrado en `app.config.ts` con `provideEcDates()`: `DateAdapter`, `MAT_DATE_FORMATS` y `MAT_DATE_LOCALE` `es-EC`) lee estricto: una fecha inexistente (31/02, 29/02 de un año no bisiesto) o un año de dos dígitos es inválida (`invalidDate`), nunca se corrige.
  - `app-phone-field` con `phoneValidator` y `PHONE_MESSAGE` (`phone.ts`), la misma regla del backend.
  - `app-address-fields` (`createAddressForm`, `addressValueOf`): provincia, cantón y parroquia en listas dependientes (otra provincia vacía cantón y parroquia; otro cantón, la parroquia), calle principal, número (opcional), calle secundaria y referencia. `LocationCatalogApi` (`location-catalog.ts`) pide el catálogo una vez por sesión (`shareReplay`; un error no queda en caché) y `formatAddress()` compone la dirección igual que el backend. Muestra los `displayName`.
  - `app-yes-no-toggle`: pregunta Sí/No con `mat-button-toggle`.
  - `field-errors.ts`: mensajes por error y un `ErrorStateMatcher` que solo muestra errores tras tocar el campo.
  - En pruebas, `FAKE_LOCATIONS` (`shared/people/patient-testing.ts`) es un pedazo del catálogo con códigos reales; `shared/people/people-testing.ts` tiene las personas, el personal (`fakeStaff`, `legacyStaff`), las cuentas y las coincidencias de MEJ-02. Las pruebas que esperan la validación asíncrona usan tiempo real (`setTimeout`), no temporizadores falsos: estos detienen la detección de cambios de la aplicación sin zona.
- Indentación de 2 espacios (ver `.editorconfig`).

## Convenciones de repositorio

- **Clave de Jira:** cada tarea tiene una clave `RHE-XX`, que va en la rama, en los commits y en el título del PR.
- Commits en inglés con Conventional Commits y la clave de Jira después del tipo: `fix(physio): RHE-51 keep the patient table inside its card`.
- Ramas, creadas siempre desde `develop`: `feature/RHE-XX-short-description` (historias y tareas técnicas), `fix/RHE-XX-...` (correcciones) y `chore/RHE-XX-...` (mantenimiento y documentación). Ejemplo: `fix/RHE-51-physio-patients-table-overflow`.
- Las ramas y los commits anteriores al 17/11/2026 usan la clave `SCRUM-XX` y no se reescriben. Jira resuelve las claves antiguas.
- Las ramas y los commits anteriores a SCRUM-51 usan `feature/HU-XX-...`, `feature/TEC-XX-...` o `chore/...` y el número de issue (`#8`). No se reescriben.
- Flujo de ramas: los PR de `feature/*`, `fix/*` y `chore/*` van **siempre hacia `develop`**, nunca hacia `main`. `develop` se fusiona en `main` **solo al cierre de cada sprint**, mediante un PR `develop → main`. Antes de crear un PR, comprobar que la rama base sea `develop` (GitHub propone `main` por defecto).
- **Regla de fusión** (ruleset "Protección develop y main"): PR obligatorio y los checks Backend y Frontend en verde, sin aprobación ni omisión de la regla. El desarrollo lo ejecuta un solo integrante y GitHub no permite aprobar un PR propio, así que no hay revisor: el autor completa la lista de verificación de la definición de terminado en la descripción del PR antes de fusionar.
- **Claude Code no fusiona PR**: nunca `gh pr merge` ni fusiones locales hacia `develop` o `main`. Daniel fusiona cada PR. Claude Code llega hasta crear o actualizar el PR y reportar su CI; si una tarea depende de un PR abierto, pide que lo fusionen y luego actualiza `develop` con `git pull`.
- `.editorconfig`: UTF-8, LF, 4 espacios en Java/XML, 2 en TypeScript, HTML, SCSS, YAML y JSON.
- `README.md` en inglés; documentación funcional en español en `docs/` (`docs/user-stories.md`, evidencias por sprint en `docs/evidence/sprint-N/`).
- Cada historia es un issue con la plantilla **Historia de usuario** (Como/quiero/para + Dado/Cuando/Entonces); cada PR usa `.github/pull_request_template.md`, lleva la clave `RHE-XX` en el título e incluye en la descripción `Closes #N`, cuando cierra un issue de GitHub, y la lista de verificación de la definición de terminado.
- CI (`.github/workflows/ci.yml`) corre en push y PR hacia `main` y `develop`: backend `./mvnw -B verify`, frontend `npm ci` + `ng test` + `ng build`. No fusionar con CI en rojo. `backend/mvnw` debe conservar el modo ejecutable en Git (`git update-index --chmod=+x backend/mvnw`).

## Definición de terminado

Una historia está terminada cuando:

- [ ] Cumple todos sus criterios de aceptación.
- [ ] Tiene pruebas unitarias y de integración con Testcontainers.
- [ ] Sus endpoints aparecen documentados en OpenAPI.
- [ ] `.\mvnw.cmd verify` (en `backend/`) y `ng test` (en `frontend/`) están en verde, también en la CI de GitHub Actions.
- [ ] Se entrega un resumen de cambios.

El autor marca esta lista en la descripción del PR (sección "Definición de terminado" de la plantilla) antes de fusionar. Si un punto no corresponde al cambio, lo indica como "no aplica".

## Forma de trabajo

- Presentar un **plan antes de implementar** cada historia de usuario.
- **Una historia por rama.**
- Cada historia tiene su issue en GitHub, con la etiqueta de su sección PB y el milestone de su sprint. Las pendientes son HU-16 y HU-17 (#40 y #41); las ya implementadas están cerradas con su PR. El PR de la historia lleva la clave `RHE-XX` en el título y `Closes #N` en la descripción.

### Plan vigente

| Sprint | Alcance |
|---|---|
| 3 | HU-01 a HU-11 y HU-18 (terminadas) |
| 4 | HU-12 a HU-15 (terminadas) |
| 5 | HU-16, HU-17 y SonarQube |
| 6 | TEC-07 (despliegue en Render) y Playwright |
| 7 | k6, OWASP ZAP, UptimeRobot y compatibilidad |
| 8 | Aceptación, SUS y `v1.0.0` |

## Docker

- La base de desarrollo principal es el **PostgreSQL de Windows (5432)**; nunca modificarla desde Docker.
- `docker-compose.yml`: servicio `db` (postgres:16 en **5433**), `backend` (perfil `full`, imagen de `backend/Dockerfile`, `DB_URL` → `db:5432`, `MAIL_HOST` → `mailpit` y `MAIL_TRANSPORT` → `smtp`, así que nunca envía correos reales) y `mailpit` (MEJ-01, perfiles `dev` y `full`, `axllent/mailpit`: SMTP en **1025** e interfaz en **8025**; nada sale de la máquina). Uso opcional.
- `backend/Dockerfile` es la imagen de despliegue en Render: multi-etapa, usuario no root, `PORT`, `HEALTHCHECK` en `/actuator/health`. No copiar `.env` ni secretos a la imagen.

## Comandos

```powershell
# Backend (desde backend/)
.\mvnw.cmd -q -DskipTests compile   # compilar
.\mvnw.cmd clean verify             # pruebas (requiere Docker para Testcontainers)
.\mvnw.cmd spring-boot:run          # arrancar sin perfil (lee ../.env; sin datos sintéticos)
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"   # con datos sintéticos (se insertan en la base de DB_URL)

# Docker (desde la raíz)
docker compose up -d db                     # PostgreSQL 16 en localhost:5433
docker compose --profile dev up -d mailpit  # correos de las cuentas en http://localhost:8025
docker compose --profile full up --build    # db + backend en :8080
docker compose --profile full down          # detener (el volumen se conserva)

# Frontend (desde frontend/)
npm start                           # http://localhost:4200 con proxy /api -> :8080
npx ng build                        # compilar
npx ng test --watch=false           # pruebas (Vitest)
```
