# Rehab Elite — Frontend

Angular 22 + Angular Material single-page app. The user interface is in Spanish; code, files and routes are in English.

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
src/app/
├── core/auth/        session (AuthSession), guards, HTTP interceptor, roles
├── shared/layout/    Shell: navy sidebar + header for authenticated pages
├── shared/ui/        reusable presentational components
└── features/
    ├── auth/login/       /login
    ├── admin/            /admin     (ADMINISTRATOR)
    ├── physiotherapist/  /physio    (PHYSIOTHERAPIST)
    ├── director/         /director  (OPERATIONS_DIRECTOR)
    └── patient/          /patient   (PATIENT)
```

Every page under the shell requires a session (`authGuard`) and the matching role (`roleGuard`).
Feature components are lazy loaded.

## Theme

Defined in `src/styles.scss` on top of a Material 3 palette generated from the brand teal
(`src/styles/_theme-colors.scss`):

| Token | Color | Use |
|---|---|---|
| `--re-navy` | `#0F172A` | sidebar, headers, toolbar |
| `--re-teal` | `#14B8A6` | primary actions (filled buttons, navy text) |
| `--re-field` | `#F3F4F6` | form field background |
| `--re-card` | `#FFFFFF` | cards (16px radius) |

## Development login

Until HU-01 delivers `POST /api/auth/login`, development builds show an "Acceso de desarrollo" block on
the login page to open each role panel without a backend. It is hidden in production builds
(`isDevMode()`) and must be removed in HU-01.
