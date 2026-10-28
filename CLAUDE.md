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
| `shared` | Módulo abierto: `config` (`AppProperties`, locale), `domain` (excepciones base), `web` (`GlobalExceptionHandler`) |

Cada módulo tiene:

- `web/` — controller REST + DTO como `record`.
- `application/` — servicios (casos de uso).
- `domain/` — entidades JPA + repositorios.

Reglas:

- Un módulo usa otro **solo** por las interfaces públicas del paquete raíz del módulo (p. ej. `ec.rehabelite.patients.PatientLookup`). Nunca importar `web`, `application` ni `domain` de otro módulo.
- `ModularityTests` debe pasar siempre.

### Buenas prácticas backend

- Inyección por constructor; nunca `@Autowired` en campos.
- Lombok **solo**: `@Getter`, `@Setter`, `@NoArgsConstructor` en entidades y `@RequiredArgsConstructor` en servicios. Nada de `@Data`, `@Builder`, `@Slf4j`, etc.
- DTO siempre como `record`; las entidades **nunca** se exponen en la API (mapear con MapStruct).
- Errores: lanzar `ResourceNotFoundException` (404) o `BusinessRuleException` (422) de `ec.rehabelite.shared.domain` con mensaje en español; `GlobalExceptionHandler` los convierte en `ProblemDetail` en español. El locale está fijo en `es-EC`, así que los mensajes de Bean Validation también salen en español.
- Logs con SLF4J (`LoggerFactory.getLogger`), en inglés, **sin datos personales ni clínicos** (nada de cédulas, nombres, correos, diagnósticos ni valores de columnas; usar ids).
- Configuración propia bajo `app.*` en el record validado `ec.rehabelite.shared.config.AppProperties`.
- Perfiles: `application.yml` (común), `application-dev.yml` (por defecto; logs SQL apagados), `application-test.yml` (Testcontainers, en `src/test/resources`).

### Endpoints REST

En inglés, plural, kebab-case, bajo `/api`:

`/api/auth`, `/api/users`, `/api/patients`, `/api/clinical-records`, `/api/treatments`, `/api/therapy-sessions`, `/api/ai-summaries`, `/api/reports`, `/api/audit-logs`, `/api/portal`.

Ejemplos: `GET /api/patients?query=...`, `POST /api/treatments/{id}/status-changes`, `POST /api/ai-summaries/{id}/approve`.

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

`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET` (≥ 32 caracteres), `JWT_EXPIRATION_MINUTES`, `ADMIN_EMAIL`, `ADMIN_PASSWORD`, `ANTHROPIC_API_KEY`, `CORS_ALLOWED_ORIGINS`.

Se mapean a `app.jwt.secret`, `app.jwt.expiration-minutes`, `app.admin.email`, `app.admin.password`, `app.cors.allowed-origins`, `app.ai.anthropic-api-key`.

## Frontend (Angular 22 + Material)

- Requiere Node.js `^22.22.3`. `npm start` sirve en `http://localhost:4200` y redirige `/api` a `http://localhost:8080` (`proxy.conf.json`).
- Carpetas: `src/app/core` (sesión `AuthSession`, `authGuard`/`roleGuard`, `authInterceptor`, roles), `src/app/shared` (`layout/Shell` con barra lateral, `ui/` reutilizables), `src/app/features/auth`, `features/admin`, `features/physiotherapist`, `features/director`, `features/patient`.
- Rutas: `/login`, `/admin`, `/physio`, `/director`, `/patient`; cada panel protegido por `authGuard` + `roleGuard(rol)` y cargado de forma diferida.
- Nombres según la guía de estilo de Angular CLI 22, en inglés: archivo `patient-list.ts`, clase `PatientList`, selector `app-patient-list`; guards/interceptores `*-guard.ts` / `*-interceptor.ts`. Textos de la interfaz en español.
- Tema: tokens `--re-navy` (#0F172A, barra lateral y cabeceras), `--re-teal` (#14B8A6, acción primaria con texto navy), `--re-field` (#F3F4F6, campos), `--re-card` (tarjetas blancas, radio 16px) en `src/styles.scss`. Usar esos tokens, no colores sueltos.
- Nuevas entradas de menú por rol en `shared/layout/nav-items.ts`.
- El bloque "Acceso de desarrollo" del login (solo `isDevMode()`) es temporal: **eliminarlo en HU-01**.
- Indentación de 2 espacios (ver `.editorconfig`).

## Convenciones de repositorio

- Commits en inglés con Conventional Commits y número de issue: `feat(patients): register patient #8`.
- Ramas: `feature/HU-XX-short-description` creadas desde `develop`.
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

## Comandos

```powershell
# Backend (desde backend/)
.\mvnw.cmd -q -DskipTests compile   # compilar
.\mvnw.cmd clean verify             # pruebas (requiere Docker para Testcontainers)
.\mvnw.cmd spring-boot:run          # arrancar con perfil dev (lee ../.env)

# Frontend (desde frontend/)
npm start                           # http://localhost:4200 con proxy /api -> :8080
npx ng build                        # compilar
npx ng test --watch=false           # pruebas (Vitest)
```
