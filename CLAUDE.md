# CLAUDE.md — Rehab Elite

Reglas permanentes del proyecto. Leer antes de cualquier cambio.

## Proyecto

Rehab Elite es un sistema web de gestión clínica y seguimiento terapéutico para una clínica de fisioterapia en Quito (proyecto Capstone).

### Roles

| Rol (`users.role`) | Uso principal |
|---|---|
| `ADMINISTRATOR` | Gestión de usuarios y configuración |
| `PHYSIOTHERAPIST` | Pacientes, historial, tratamientos, sesiones, resúmenes IA |
| `PATIENT` | Portal de solo lectura de su propio tratamiento |
| `OPERATIONS_DIRECTOR` | Reportes e indicadores |

### Fuera de alcance

Facturación, pagos, agenda médica, telemedicina, app móvil, integración con sistemas externos y diagnóstico automático. No implementar nada de esto aunque parezca útil.

## Regla de idioma

**En inglés:** carpetas, paquetes, clases, interfaces, métodos, variables, constantes, enums, tablas, columnas, índices, constraints, endpoints REST, rutas y componentes de Angular, nombres de archivos, variables de entorno, logs, comentarios de código/Javadoc y mensajes de commit.

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

- **Backend:** Java 21, Spring Boot 4, Spring Security con JWT HS256 (OAuth2 Resource Server), Spring Data JPA, Flyway, Spring Modulith, Bean Validation, MapStruct, springdoc-openapi.
- **Frontend:** Angular 22 con Angular Material.
- **Base de datos:** PostgreSQL 16.
- **IA:** API de Claude (Sprint 7, módulo `aisummary`).
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
| `shared` | Módulo abierto: `config` (`AppProperties`, locale, reloj, validación), `domain` (excepciones base, `ClinicCalendar`), `web` (`GlobalExceptionHandler`) |

Cada módulo tiene:

- `web/` — controller REST + DTO como `record`.
- `application/` — servicios (casos de uso).
- `domain/` — entidades JPA + repositorios.

Reglas:

- Un módulo usa otro **solo** por las interfaces públicas del paquete raíz del módulo (p. ej. `ec.rehabelite.patients.PatientLookup`). Nunca importar `web`, `application` ni `domain` de otro módulo.
- `ModularityTests` debe pasar siempre.
- APIs públicas disponibles:
  - `ec.rehabelite.auth.CurrentUserProvider`: usuario autenticado (`CurrentUser`: id, email, fullName, `Role`).
  - `ec.rehabelite.audit.AuditLogService`: `record(AuditAction, entityType, entityId)` toma el usuario del token y la IP de la petición.
  - `audit` no depende de `auth`, para evitar un ciclo: `auth` registra el `LOGIN` con `recordFor(userId, ...)`.
  - `ec.rehabelite.auth.PortalAccountCreated(userId, patientId)` (HU-02):
    - Evento que `auth` publica al crear una cuenta `PATIENT`.
    - `patients` lo escucha de forma síncrona (`@EventListener`, misma transacción), llena `portal_user_id` y audita `UPDATE Patient`. Si el vínculo falla, la cuenta tampoco se crea.
    - `patients` puede depender de `auth`, pero `auth` nunca de `patients`.
  - `ec.rehabelite.patients.PatientDirectory` (HU-07):
    - `find(id)` y `search(q, pageable)` devuelven `PatientCard` (id, cédula, número de historia, nombres, apellidos, fecha de nacimiento y `ageOn(fecha)`).
    - `findProfile(id)` (HU-09) devuelve `PatientProfile`: además, sexo, contacto, ocupación, instrucción, seguro y fecha de ingreso. Sin etnia ni religión.
    - Es la única forma en que otros módulos (`clinicalrecords`, `sessions`, ...) consultan pacientes.
    - `patients` nunca depende de `clinicalrecords`.
  - `ec.rehabelite.auth.StaffDirectory` (HU-08):
    - `findActivePhysiotherapist(id)`, `activePhysiotherapists()` y `fullNames(ids)` devuelven `StaffMember` (id y nombre) o los nombres, también de cuentas desactivadas.
    - Es la única forma en que otros módulos consultan cuentas del personal; `auth` nunca depende de ellos.
- Seguridad: JWT HS256 emitido por `POST /api/auth/login` (claims `sub` = id, `email`, `fullName`, `role`; el rol da la autoridad `ROLE_<rol>`). Públicos solo login, `/actuator/health` y la documentación OpenAPI. El reloj se inyecta como bean `Clock` (`shared.config`).
- Estado de cuentas (HU-03):
  - `PATCH /api/users/{id}/status` con `{ "active": boolean }` (`UserManagementService.changeStatus`).
  - Nunca se borra una cuenta: se desactiva. Reactivarla reinicia intentos fallidos y bloqueo.
  - Un administrador no puede desactivar su propia cuenta (422).
  - Cada cambio real audita `STATUS_CHANGE User`; pedir el estado actual no cambia ni audita nada.
  - El login rechaza la cuenta inactiva con el mensaje genérico de HU-01.
  - `ActiveAccountTokenValidator` (validador del `JwtDecoder`, después de firma, expiración y emisor) da 401 a los tokens ya emitidos de una cuenta inactiva o inexistente. Las pruebas deben emitir tokens solo para usuarios guardados.
- Registro de pacientes (HU-05):
  - `POST /api/patients` (`PatientRegistrationService.register`). El número de historia clínica es la cédula y lo asigna el servidor.
  - La edad no se guarda: `Patient.ageOn(fecha)`, y la respuesta trae `age` calculada a la fecha de hoy en Quito.
  - Etnia y religión son opcionales; si llegan en blanco se guardan como `null`.
  - Sin fecha de ingreso, se usa la de hoy.
  - Una cédula repetida responde 409 con la propiedad `existingPatientId`.
  - Cada registro audita `CREATE Patient`.
  - `GET /api/patients/{id}` devuelve solo datos personales y no se audita: la auditoría `READ` es para datos clínicos.
  - Validador de cédula: `patients.domain.NationalId` (10 dígitos, provincia 01–24 o 30, tercer dígito menor que 6, módulo 10), con la anotación `@ValidNationalId` en los requests.
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
  - Estas consultas no se auditan: la auditoría `READ` del resto de lecturas clínicas llega con HU-18.
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
- Respuestas paginadas: devolver `PageResponse.of(page, mapper)` (`content`, `page`, `size`, `totalElements`, `totalPages`), nunca el `Page` de Spring Data.
- Acceso por rol (HU-04): la tabla vive en `auth.config.RoleAccessRules`. Una regla puede limitarse a un método HTTP y gana la primera que coincide. Al añadir un área de API nueva, registrarla ahí y en `RoleAccessIntegrationTests`.

  | Área | Roles |
  |---|---|
  | `/api/admin/**`, `/api/users/**` | `ADMINISTRATOR` |
  | `/api/patients/portal-candidates`, `POST /api/patients` y `PUT /api/patients/**` (van antes de `/api/patients/**`) | `ADMINISTRATOR` |
  | `/api/patients/*/clinical-record` (va antes de `/api/patients/**`) | `PHYSIOTHERAPIST` |
  | `/api/patients/**` | `ADMINISTRATOR`, `PHYSIOTHERAPIST` |
  | `/api/clinical-records/**`, `/api/treatments/**`, `/api/therapy-sessions/**`, `/api/ai-summaries/**`, `/api/icd10-diagnoses/**`, `/api/physio/**` | `PHYSIOTHERAPIST` |
  | `/api/reports/**`, `/api/audit-logs/**` | `OPERATIONS_DIRECTOR`, `ADMINISTRATOR` |
  | `/api/portal/**` | `PATIENT` |
  | Resto de `/api/**` | Cualquier usuario autenticado |

- Respuestas de seguridad:
  - `ProblemDetailAuthenticationEntryPoint`: 401 sin auditoría (también para el token de una cuenta inactiva).
  - `AuditingAccessDeniedHandler`: 403 y `ACCESS_DENIED` en `audit_logs`. La entidad se toma de la ruta (`/api/patients/12` → `patients`, 12); nunca se guarda la query string.
  - `GlobalExceptionHandler` relanza `AccessDeniedException` y `AuthenticationException`, para que un futuro `@PreAuthorize` también quede auditado.

### Buenas prácticas backend

- Inyección por constructor; nunca `@Autowired` en campos.
- Lombok **solo**: `@Getter`, `@Setter`, `@NoArgsConstructor` en entidades y `@RequiredArgsConstructor` en servicios. Nada de `@Data`, `@Builder`, `@Slf4j`, etc.
- DTO siempre como `record`; las entidades **nunca** se exponen en la API (mapear con MapStruct).
- Errores: lanzar `ResourceNotFoundException` (404), `BusinessRuleException` (422) o `ConflictException` (409) de `ec.rehabelite.shared.domain`, con mensaje en español; `GlobalExceptionHandler` los convierte en `ProblemDetail` en español.
  - `ConflictException` añade sus propiedades a la respuesta: solo ids o códigos, nunca datos personales.
  - El locale está fijo en `es-EC`, así que los mensajes de Bean Validation también salen en español.
- Fechas de calendario ("hoy", edades, fecha de ingreso): usar `ClinicCalendar.today(clock)` de `shared.domain`, con la zona `America/Guayaquil`. Los instantes se guardan en UTC. Las anotaciones `@Past` y `@PastOrPresent` usan el mismo reloj (`ValidationConfig`).
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
- Pruebas de integración: extender `ec.rehabelite.AbstractIntegrationTest` (contexto completo, perfil `test`, un único `postgres:16` de Testcontainers compartido). No asumir base vacía: cada prueba crea sus propios datos con valores únicos. Para pacientes de prueba que no son el objeto de la prueba, usar `ec.rehabelite.TestPatients`: los inserta por SQL, sin auditoría, con cédulas sintéticas de dígito verificador válido y el número de historia igual a la cédula.

### Endpoints REST

En inglés, plural, kebab-case, bajo `/api`:

`/api/auth`, `/api/users`, `/api/patients`, `/api/clinical-records`, `/api/treatments`, `/api/therapy-sessions`, `/api/ai-summaries`, `/api/reports`, `/api/audit-logs`, `/api/portal`, `/api/icd10-diagnoses`.

Excepciones acordadas en HU-07 (fuera del patrón de recurso plural):
- `POST`, `GET` (HU-09) y `PUT` (HU-09) `/api/patients/{patientId}/clinical-record`: singular, porque es el único historial del paciente.
- `GET /api/physio/patients`: vista de pacientes con datos clínicos, separada de `/api/patients`, que no los incluye. `GET /api/physio/physiotherapists` (HU-08) sigue el mismo criterio.
- HU-08: `PUT /api/treatments/{id}/responsible` y `PATCH /api/treatments/{id}/status` (sub-recursos en singular del tratamiento).

Ejemplos: `GET /api/patients?query=...`, `PATCH /api/treatments/{id}/status`, `POST /api/ai-summaries/{id}/approve`.

## Modelo de datos

Fuente de verdad: `backend/src/main/resources/db/migration/` (`V1__initial_schema.sql`, `V2__icd10_initial_catalog.sql`).

- Tablas en inglés, plural, snake_case: `users`, `patients`, `clinical_records`, `icd10_diagnoses`, `treatments`, `responsible_assignments`, `treatment_status_changes`, `therapy_sessions`, `ai_summaries`, `audit_logs`.
- Prefijos: `pk_`, `fk_`, `uq_`, `ck_`, `ix_`; triggers `trg_`.
- Columnas de auditoría: `created_by`, `created_at`, `updated_by`, `updated_at`.
- Función de búsqueda: `normalize_text(text)` (minúsculas sin tildes; índice trigram sobre `patients`).

Valores de enums / CHECK:

| Campo | Valores |
|---|---|
| `users.role` | `ADMINISTRATOR`, `PHYSIOTHERAPIST`, `PATIENT`, `OPERATIONS_DIRECTOR` |
| `patients.sex` | `MALE`, `FEMALE` |
| `treatments.status` | `ACTIVE`, `COMPLETED`, `ABANDONED` |
| `treatments.severity` | `MILD`, `MODERATE`, `SEVERE` |
| `therapy_sessions.status` | `COMPLETED`, `RESCHEDULED`, `NO_SHOW` |
| `therapy_sessions.progress_status` | `STABLE`, `IMPROVING`, `NEEDS_ATTENTION` |
| `ai_summaries.status` | `PENDING_REVIEW`, `APPROVED`, `DISCARDED` |
| `audit_logs.action` | `CREATE`, `READ`, `UPDATE`, `STATUS_CHANGE`, `ACCESS_DENIED`, `LOGIN` |

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

`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `POSTGRES_DB` (solo para el PostgreSQL de Docker), `JWT_SECRET` (≥ 32 caracteres), `JWT_EXPIRATION_MINUTES`, `ADMIN_EMAIL`, `ADMIN_PASSWORD`, `ANTHROPIC_API_KEY`, `CORS_ALLOWED_ORIGINS`, `DEV_SEED_PASSWORD` (solo perfil `dev`).

Se mapean a `app.jwt.secret`, `app.jwt.expiration-minutes`, `app.admin.email`, `app.admin.password`, `app.cors.allowed-origins`, `app.ai.anthropic-api-key` y, solo en `application-dev.yml`, `app.dev-seed.password`.

## Frontend (Angular 22 + Material)

- Requiere Node.js `^22.22.3`. `npm start` sirve en `http://localhost:4200` y redirige `/api` a `http://localhost:8080` (`proxy.conf.json`).
- Carpetas: `src/app/core` (`AuthService` con login y sesión en signals, `authGuard`/`roleGuard`, `authInterceptor`, roles, locale `es-EC`), `src/app/shared/layout` (`AppShell`, `nav-items.ts`, `PlaceholderPage`), `src/app/shared/ui` (componentes reutilizables), `src/app/features/auth`, `features/admin`, `features/physiotherapist`, `features/director`, `features/patient`, `features/dev` (solo desarrollo).
- Rutas: `/login` y, dentro de `AppShell`, un `*.routes.ts` por rol: `/physio` (`patients`, `clinical-records`, `timeline`, `ai-assistant`), `/admin` (`users`, `patients`, `reports`), `/director` (`reports`), `/patient`. Cada rol protegido por `authGuard` + `roleGuard(rol)` y cargado de forma diferida. Las secciones sin historia implementada usan `PlaceholderPage` (título y subtítulo en `data`); la historia reemplaza ese `component` por su página real.
- Componentes de `shared/ui` (standalone, con pruebas): `page-header`, `stat-card` (variante `compact` para filas de cuatro tarjetas), `status-badge`, `data-table` (paginación en cliente, o en servidor con `[totalItems]` y `[(pageIndex)]`; `appDataTableCell` para celdas personalizadas, y `[appDataTableCellOf]="filas"` tipa `let-row`), `form-section`, `empty-state` (admite varias acciones, separadas), `confirm-dialog` (`openConfirmDialog()` devuelve `Observable<boolean>`) y `hero-banner`. Usarlos antes de crear componentes nuevos. Catálogo visual en `/ui-kit` (solo `isDevMode()`).
- Diseño: seguir `docs/prototype/`. Capturas de comparación por tarea en `docs/screenshots/<tarea>/`.
- Nombres según la guía de estilo de Angular CLI 22, en inglés: archivo `patient-list.ts`, clase `PatientList`, selector `app-patient-list`; guards/interceptores `*-guard.ts` / `*-interceptor.ts`. Textos de la interfaz en español.
- Tema: tokens CSS `--re-*` definidos en `src/styles/_tokens.scss` (colores, grises, estados, radios, sombras, espaciados, tipografía Plus Jakarta Sans): `--re-navy` (#0F172A, barra lateral y cabeceras), `--re-teal` (#14B8A6, acción primaria e ítem activo **con texto navy**, contraste 7:1), `--re-text-teal` (#0F766E, texto teal sobre blanco), `--re-field` (#F3F4F6, campos), `--re-card` (tarjetas blancas, `--re-radius-lg` 16px). Usar siempre tokens, nunca colores sueltos; no poner texto blanco sobre `#14B8A6` (contraste 2.5:1).
- Nuevas entradas de menú por rol en `shared/layout/nav-items.ts`.
- Acceso por rol (HU-04):
  - `roleGuard(rol)` protege cada grupo de rutas. Si el rol no coincide, redirige al panel del rol del usuario y `AppShell` muestra el aviso "No tiene acceso a esa sección." (`SectionAccessNotice`).
  - Un 403 de la API lleva a `/access-denied` (`AccessDeniedPage`) sin cerrar la sesión.
  - Las rutas desconocidas van al panel del rol.
- Sesión: `AuthService` guarda token, expiración y usuario (`sessionStorage`, o `localStorage` con "Recordarme"); el usuario actual se lee con `user()` / `role()`. En pruebas unitarias iniciar sesión con `logInForTest(rol, nombre?, id?)` de `core/auth/auth-testing.ts`.
- Errores de la API: mostrar el `detail` en español con `problemDetailMessage(error, textoAlternativo)` de `core/http/problem-detail.ts`. Las páginas de resultados usan el tipo `Page<T>` de `core/http/page.ts`.
- `AppShell` vuelve al inicio el área de contenido (`mat-sidenav-content`, que es la que se desplaza) en cada navegación.
- Historial clínico del fisioterapeuta (HU-07):
  - `/physio/patients` (`PhysioPatientList`): tabla paginada en el servidor con el resumen del tratamiento y las acciones "Crear historial" o "Nuevo tratamiento".
  - `ClinicalRecordForm` sirve dos rutas:
    - `/physio/patients/:patientId/clinical-record/new`: cuatro secciones.
    - `/physio/patients/:patientId/clinical-record/:recordId/treatments/new`: sin "Hábitos y antecedentes".
  - `Icd10Picker` es un control de formulario (`ControlValueAccessor`) con autocompletado. Su valor es el `Icd10Diagnosis` elegido de la lista, o `null`.
  - `PatientSummaryCard` es la tarjeta del paciente (prototipo 04) de las páginas clínicas. Recibe un `SummaryCardPatient`, que cumplen tanto el paciente de `GET /api/patients/{id}` como el del expediente. Con `[showContact]="true"` añade la fecha de ingreso, el correo, la dirección y el seguro.
  - `CLINICAL_HISTORY_FIELDS`, `initialsOf()` y `painScoreTone()` viven en `clinical-labels.ts`.
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
  - "Editar historial" abre `/physio/patients/:patientId/clinical-record/edit`. "Ver sesiones" está deshabilitado hasta el sprint 5 (HU-10/HU-11).
  - `ClinicalRecordForm` tiene tres modos:
    - `record` y `treatment`, los de HU-07;
    - `edit`, que se activa con `data: { mode: 'edit' }` en la ruta y muestra solo "Hábitos y antecedentes", precargado desde el expediente, con `PUT`.
  - Al guardar en cualquier modo, el formulario abre el expediente.
  - La lista del fisioterapeuta muestra "Ver historial" a los pacientes que ya tienen historial.
- Validaciones compartidas en `src/app/shared/validation`. `national-id.ts` aplica las mismas reglas de cédula que el backend: `nationalIdValidator` da `{ nationalId: regla }` y `NATIONAL_ID_MESSAGES` tiene el mensaje de cada regla.
- Pacientes del administrador (HU-05, HU-06):
  - `/admin/patients` (`PatientRegistry`) busca con espera de 300 ms y muestra una tabla paginada en el servidor con identificación y contacto. Las columnas de sesiones y estado del prototipo son datos clínicos y no se muestran.
  - `PatientFormDialog` (`openPatientFormDialog(dialog, paciente?)`) registra un paciente o, si recibe uno, edita sus datos con la cédula en solo lectura y `PUT`.
  - `/admin/patients/:id` (`PatientDetail`) muestra la ficha con la última actualización y el botón "Editar datos". Se abre tras registrar y desde el aviso de cédula existente.
  - Fechas con `<input type="date">` (valor `yyyy-MM-dd`); la edad del formulario se calcula con `ageOn()` de `patient-age.ts`.
- Indentación de 2 espacios (ver `.editorconfig`).

## Convenciones de repositorio

- Commits en inglés con Conventional Commits y número de issue: `feat(patients): register patient #8`.
- Ramas: `feature/HU-XX-short-description` (historias), `feature/TEC-XX-...` (tareas técnicas) y `chore/...` (mantenimiento), creadas siempre desde `develop`.
- Flujo de ramas: los PR de `feature/*` y `chore/*` van **siempre hacia `develop`**, nunca hacia `main`. `develop` se fusiona en `main` **solo al cierre de cada sprint**, mediante un PR `develop → main`. Antes de crear un PR, comprobar que la rama base sea `develop` (GitHub propone `main` por defecto).
- `.editorconfig`: UTF-8, LF, 4 espacios en Java/XML, 2 en TypeScript, HTML, SCSS, YAML y JSON.
- `README.md` en inglés; documentación funcional en español en `docs/` (`docs/user-stories.md`, evidencias por sprint en `docs/evidence/sprint-N/`).
- Cada historia es un issue con la plantilla **Historia de usuario** (Como/quiero/para + Dado/Cuando/Entonces); cada PR usa `.github/pull_request_template.md` e incluye `Closes #N`.
- CI (`.github/workflows/ci.yml`) corre en push y PR hacia `main` y `develop`: backend `./mvnw -B verify`, frontend `npm ci` + `ng test` + `ng build`. No fusionar con CI en rojo. `backend/mvnw` debe conservar el modo ejecutable en Git (`git update-index --chmod=+x backend/mvnw`).

## Definición de terminado

Una historia está terminada cuando:

- [ ] Cumple todos sus criterios de aceptación.
- [ ] Tiene pruebas unitarias y de integración con Testcontainers.
- [ ] Sus endpoints aparecen documentados en OpenAPI.
- [ ] `.\mvnw.cmd verify` (en `backend/`) y `ng test` (en `frontend/`) están en verde, también en la CI de GitHub Actions.
- [ ] Se entrega un resumen de cambios.

## Forma de trabajo

- Presentar un **plan antes de implementar** cada historia de usuario.
- **Una historia por rama.**

## Docker

- La base de desarrollo principal es el **PostgreSQL de Windows (5432)**; nunca modificarla desde Docker.
- `docker-compose.yml`: servicio `db` (postgres:16 en **5433**) y `backend` (perfil `full`, imagen de `backend/Dockerfile`, `DB_URL` → `db:5432`). Uso opcional.
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
docker compose --profile full up --build    # db + backend en :8080
docker compose --profile full down          # detener (el volumen se conserva)

# Frontend (desde frontend/)
npm start                           # http://localhost:4200 con proxy /api -> :8080
npx ng build                        # compilar
npx ng test --watch=false           # pruebas (Vitest)
```
