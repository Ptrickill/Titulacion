# Rehab Elite

[![CI](https://github.com/Ptrickill/Titulacion/actions/workflows/ci.yml/badge.svg?branch=develop)](https://github.com/Ptrickill/Titulacion/actions/workflows/ci.yml)

Clinical management and therapy follow-up web system for a physiotherapy clinic in Quito, Ecuador. Capstone project.

It covers patient registration and clinical records, treatments with ICD-10 diagnoses, therapy sessions with pain-score tracking, AI-assisted treatment summaries (always reviewed by a physiotherapist), a read-only patient portal and reports for the operations director. Every action on clinical data is recorded in an append-only audit log.

The user interface and API error messages are in Spanish; code, database objects and technical documentation are in English.

## Tech stack

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot 4.1, Spring Security (JWT HS256 / OAuth2 Resource Server), Spring Data JPA, Flyway, Spring Modulith, Bean Validation, MapStruct, springdoc-openapi |
| Frontend | Angular 22 + Angular Material |
| Database | PostgreSQL 16 |
| AI | Claude API (Anthropic) |
| Testing | JUnit 5, Mockito, Testcontainers, Vitest, Playwright |

## Project structure

```
rehab-elite/
├── backend/                         Spring Boot REST API (modular monolith)
│   └── src/main/
│       ├── java/ec/rehabelite/
│       │   ├── auth/                users, JWT login, security
│       │   ├── patients/            patient personal data
│       │   ├── clinicalrecords/     clinical records and treatments
│       │   ├── sessions/            therapy sessions
│       │   ├── aisummary/           AI summaries
│       │   ├── portal/              patient portal
│       │   ├── reports/             operational reports
│       │   ├── audit/               audit log
│       │   └── shared/              cross-cutting config and error handling
│       └── resources/
│           ├── application.yml      common settings
│           ├── application-dev.yml  local development (default profile)
│           └── db/migration/        Flyway migrations (data model)
├── frontend/                        Angular 22 + Material SPA (see frontend/README.md)
├── docs/                            functional documentation (Spanish)
├── .editorconfig
├── .env.example                     environment variable template
└── CLAUDE.md                        permanent project rules
```

Each module is split into `web/` (controllers and DTOs), `application/` (services) and `domain/` (entities and repositories). Modules talk to each other only through the public types in their root package; `ModularityTests` enforces it.

REST endpoints live under `/api` in plural kebab-case (`/api/patients`, `/api/clinical-records`, `/api/therapy-sessions`, `/api/ai-summaries`, `/api/audit-logs`, ...). Errors are returned as RFC 9457 `ProblemDetail`.

## Requirements

- JDK 21 or later
- Node.js `^22.22.3` (required by Angular CLI 22) — frontend only; the CLI runs through `npx`
- PostgreSQL 16
- Docker (only to run the Testcontainers tests)

## Getting started

1. **Environment variables.** Copy the template and fill in every value marked `TODO`:

   ```powershell
   Copy-Item .env.example .env
   ```

   | Variable | Purpose |
   |---|---|
   | `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | PostgreSQL connection |
   | `JWT_SECRET`, `JWT_EXPIRATION_MINUTES` | Token signing key (≥ 32 chars) and lifetime |
   | `ADMIN_EMAIL`, `ADMIN_PASSWORD` | Initial administrator account |
   | `ANTHROPIC_API_KEY` | Claude API key (AI summaries) |
   | `CORS_ALLOWED_ORIGINS` | Comma-separated allowed origins |

   `.env` is git-ignored and must never be committed.

2. **Database.** Create the `rehab_elite` database and an owner user. If that user cannot create extensions, run once as `postgres`, connected to `rehab_elite`:

   ```sql
   CREATE EXTENSION IF NOT EXISTS pg_trgm WITH SCHEMA public;
   CREATE EXTENSION IF NOT EXISTS unaccent WITH SCHEMA public;
   ```

3. **Backend.** Flyway creates the schema on startup (profile `dev` by default):

   ```powershell
   cd backend
   .\mvnw.cmd spring-boot:run
   ```

   - Health: <http://localhost:8080/actuator/health>
   - OpenAPI UI: <http://localhost:8080/swagger-ui.html> (requires a token until HU-01 opens it)

4. **Frontend.** In a second terminal:

   ```powershell
   cd frontend
   npm install
   npm start
   ```

   Open <http://localhost:4200>. Requests to `/api` are proxied to the backend on port 8080.

5. **Tests:**

   ```powershell
   cd backend;  .\mvnw.cmd clean verify        # Docker must be running
   cd frontend; npx ng test --watch=false
   ```

## Workflow

Branches `feature/HU-XX-short-description` from `develop`; commits in English following Conventional Commits with the issue number, e.g. `feat(patients): register patient #8`. See [CLAUDE.md](CLAUDE.md) for the full rules.

## Continuous integration

[`.github/workflows/ci.yml`](.github/workflows/ci.yml) runs on every push and pull request to `main` and `develop`:

| Job | Steps |
|---|---|
| Backend | Temurin 21 with Maven cache → `./mvnw -B verify` (Testcontainers uses the Docker engine of `ubuntu-latest`) → uploads `backend/target/surefire-reports` as the `surefire-reports` artifact |
| Frontend | Node 22 with npm cache → `npm ci` → `npx ng test --watch=false` → `npx ng build` |

No secrets are needed: the backend tests use the synthetic values in `application-test.yml`. A pull request should only be merged when both jobs are green.

## Project documentation

- [`docs/user-stories.md`](docs/user-stories.md) — user stories and acceptance criteria (Spanish)
- [`docs/evidence/`](docs/evidence/) — sprint evidence (boards, burndown, CI runs, screenshots, minutes)
- GitHub templates: [user story issue](.github/ISSUE_TEMPLATE/user-story.md) and [pull request](.github/pull_request_template.md)
