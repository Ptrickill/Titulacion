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
    ├── core/              session (AuthSession), guards, HTTP interceptor, roles, es-EC locale
    ├── shared/layout/     AppShell (sidebar per role, header with user/role/date), nav-items, PlaceholderPage
    ├── shared/ui/         reusable standalone components (see below)
    └── features/
        ├── auth/login/       /login
        ├── physiotherapist/  /physio, /physio/patients, /physio/clinical-records, /physio/timeline, /physio/ai-assistant
        ├── admin/            /admin, /admin/users, /admin/patients, /admin/reports
        ├── director/         /director/reports
        ├── patient/          /patient
        └── dev/              /ui-kit (development only)
```

Each role has its own `*.routes.ts`, lazy loaded inside `AppShell` and protected by `authGuard` + `roleGuard`. Sections whose user story is not implemented yet render `PlaceholderPage` with the title and subtitle given in the route `data`; the story replaces that component with the real page.

## Shared UI components

| Component | Selector | Purpose |
|---|---|---|
| `PageHeader` | `app-page-header` | Title, subtitle, optional back link and an `[actions]` slot |
| `StatCard` | `app-stat-card` | KPI card with pastel icon tile, label, value and hint |
| `StatusBadge` | `app-status-badge` | Status/role/severity pill (`success`, `warning`, `danger`, `info`, `purple`, `teal`, `neutral`) |
| `DataTable` | `app-data-table` | Card table with client-side pagination; custom cells with `<ng-template appDataTableCell="key" let-row>` |
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

## Development login

Until HU-01 delivers `POST /api/auth/login`, development builds show an "Acceso de desarrollo" block on the login page and a "Cambiar de rol (prueba)" button in the sidebar to open each role panel without a backend. Both are hidden in production builds (`isDevMode()`) and must be removed in HU-01.
