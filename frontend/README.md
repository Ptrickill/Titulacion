# Rehab Elite — Frontend

Angular 22 + Angular Material single-page app. The user interface is in Spanish; code, files and routes are in English. The visual reference is the prototype in [`docs/prototype/`](../docs/prototype/).

## Commands

```powershell
npm install          # first time only
npm start            # dev server on http://localhost:4200, proxies /api to http://localhost:8080
npx ng build         # production build into dist/rehab-elite
npx ng test --watch=false   # unit tests (Vitest)
```

Requires Node.js `^22.22.3` (Angular CLI 22).

## Structure

```
src/
├── styles.scss            Material theme + component overrides
├── styles/_tokens.scss    design tokens (--re-* CSS custom properties)
└── app/
    ├── core/              AuthService (login + session), guards, HTTP interceptor, roles, es-EC locale,
    │                      problemDetailMessage() for the Spanish "detail" of API errors
    ├── shared/layout/     AppShell (sidebar per role, header with user/role/date), nav-items, PlaceholderPage
    ├── shared/ui/         reusable standalone components (see below)
    ├── shared/validation/ form validators shared by features (Ecuadorian national ID, same rules as the backend)
    └── features/
        ├── auth/login/       /login
        ├── physiotherapist/  /physio, /physio/patients (patients/: list with the treatment summary, HU-07),
        │                     /physio/patients/:id/clinical-record/new and .../treatments/new
        │                     (clinical-records/: initial assessment form + ICD-10 picker, HU-07),
        │                     /physio/clinical-records, /physio/timeline, /physio/ai-assistant
        ├── admin/            /admin, /admin/users (users/: list + create dialog, HU-02; activate/deactivate, HU-03),
        │                     /admin/patients and /admin/patients/:id (patients/: search with server-side pagination,
        │                     register/edit form dialog and patient record, HU-05 and HU-06),
        │                     /admin/reports
        ├── director/         /director/reports
        ├── patient/          /patient
        └── dev/              /ui-kit (development only)
```

Each role has its own `*.routes.ts`, lazy loaded inside `AppShell` and protected by `authGuard` + `roleGuard`. Sections whose user story is not implemented yet render `PlaceholderPage` with the title and subtitle given in the route `data`; the story replaces that component with the real page.

Role access (HU-04):

- **Another role's section:** `roleGuard` sends the user back to their own panel, and the shell shows "No tiene acceso a esa sección.".
- **API 403:** opens `/access-denied`, a page inside the shell with a link to the user's panel.
- **Unknown URLs:** go to the user's panel, or to `/login` without a session.

## Shared UI components

| Component | Selector | Purpose |
|---|---|---|
| `PageHeader` | `app-page-header` | Title, subtitle, optional back link and an `[actions]` slot |
| `StatCard` | `app-stat-card` | KPI card with pastel icon tile, label, value and hint; `compact` for rows of four cards |
| `StatusBadge` | `app-status-badge` | Status/role/severity pill (`success`, `warning`, `danger`, `info`, `purple`, `teal`, `neutral`) |
| `DataTable` | `app-data-table` | Card table with client-side pagination, or server-side with `[totalItems]` and `[(pageIndex)]`; custom cells with `<ng-template appDataTableCell="key" [appDataTableCellOf]="rows" let-row>` (the optional `appDataTableCellOf` types `row`) |
| `FormSection` | `app-form-section` | Numbered form block with a responsive two-column grid (`class="full"` spans both) |
| `EmptyState` | `app-empty-state` | Empty list / not-yet-available message with an optional action |
| `ConfirmDialog` | `openConfirmDialog(dialog, data)` | Navy-header confirmation modal; emits `true` only on confirm |
| `HeroBanner` | `app-hero-banner` | Navy-to-teal welcome banner of the role home pages |

In development builds, `/ui-kit` (any role, after logging in) shows every component with synthetic data.

## Theme

Design tokens live in `src/styles/_tokens.scss` and are exposed as `--re-*` CSS custom properties; the Material 3 palette is generated from the brand teal (`src/styles/_theme-colors.scss`). Typography: Plus Jakarta Sans.

| Token | Color | Use |
|---|---|---|
| `--re-navy` | `#0F172A` | sidebar, headers, dialog headers |
| `--re-teal` | `#14B8A6` | primary actions and active menu item, always with navy text (7:1) |
| `--re-text-teal` | `#0F766E` | teal text on white |
| `--re-field` | `#F3F4F6` | form field background |
| `--re-card` | `#FFFFFF` | cards (`--re-radius-lg`, 16px) |

Status colors (`--re-success`, `--re-warning`, `--re-danger`, `--re-info`, `--re-purple`, `--re-neutral` with `-bg` / `-border` variants), slate grays (`--re-gray-50` … `--re-gray-700`), radii, shadows and spacing are also tokens. Never hard-code colors in components.

## Authentication

`AuthService` (`core/auth/auth-service.ts`) calls `POST /api/auth/login` and keeps the session (JWT, expiry and user) in signals:

- **Storage:** `sessionStorage` by default (ends with the tab); `localStorage` when "Recordarme en este dispositivo" is checked.
- **Expiry:** the session ends when the token expires (timer plus a check in `authGuard`), and the user is sent to `/login`.
- **Requests:** `authInterceptor` adds `Authorization: Bearer <token>` to `/api` calls; a 401 from any endpoint other than the login ends the session and redirects to `/login`.
- **Redirects:** after login, and when opening `/` or `/login` with a valid session, the user goes to the panel of their role (`ROLE_HOME`).

To log in locally, start the backend: the first start creates the administrator from `ADMIN_EMAIL` / `ADMIN_PASSWORD` in `.env`. In unit tests, use `logInForTest(role, fullName?, id?)` from `core/auth/auth-testing.ts`.
