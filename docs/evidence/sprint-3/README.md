# Evidencias — Sprint 3

Registro de las evidencias del Sprint 3. Guardar los archivos en esta carpeta con los nombres indicados y actualizar la columna **Estado** (✅ entregada, ⏳ pendiente).

| Fecha de inicio | Fecha de cierre | Objetivo del sprint |
|---|---|---|
| 26/10/2026 | 08/11/2026 | Entregar la autenticación y el control de acceso por rol (PB-01: HU-01 a HU-04) y la gestión de datos personales de pacientes (PB-02: HU-05 y HU-06), según `docs/user-stories.md`: 21 puntos en 6 historias |

Historias del sprint (puntos según [`docs/user-stories.md`](../../user-stories.md)):

| Historia | Puntos | PR |
|---|---|---|
| HU-01 Iniciar sesión | 3 | [#9](https://github.com/Ptrickill/Titulacion/pull/9) |
| HU-02 Crear usuarios y asignar rol | 5 | [#11](https://github.com/Ptrickill/Titulacion/pull/11) |
| HU-03 Activar o desactivar cuentas | 2 | [#12](https://github.com/Ptrickill/Titulacion/pull/12) |
| HU-04 Bloquear funciones ajenas al rol | 3 | [#10](https://github.com/Ptrickill/Titulacion/pull/10) |
| HU-05 Registrar paciente | 5 | [#13](https://github.com/Ptrickill/Titulacion/pull/13) |
| HU-06 Buscar y actualizar pacientes | 3 | [#14](https://github.com/Ptrickill/Titulacion/pull/14) |

## Evidencias

| # | Evidencia | Descripción | Archivo / enlace | Estado |
|---|---|---|---|---|
| 1 | Tablero al inicio | Captura del tablero (GitHub Projects) con el backlog del sprint comprometido | `board-start.png` | ⏳ |
| 2 | Tablero al cierre | Captura del tablero con las historias terminadas y las que pasan al siguiente sprint | `board-end.png` | ⏳ |
| 3 | Burndown | Gráfico de puntos restantes por día del sprint | `burndown.png` | ⏳ |
| 4 | Pull requests | PR fusionados hacia `develop` con su historia y revisión | Tabla [Pull requests](#pull-requests) | ✅ PR, historia, CI y fecha · ⏳ revisión (ver [Observaciones](#observaciones)) |
| 5 | Integración continua | Ejecución en verde del workflow **CI** (backend y frontend) del último PR del sprint | `ci-run.png` + [ejecución del PR #14](https://github.com/Ptrickill/Titulacion/actions/runs/41004910223) y [ejecución en `develop` tras la fusión](https://github.com/Ptrickill/Titulacion/actions/runs/41005233539) | ⏳ captura `ci-run.png` |
| 6 | Pruebas | Resultado de `mvnw verify` (artefacto `surefire-reports`) y de `ng test` | `tests-backend.png`, `tests-frontend.png` · totales en [Resultados de pruebas](#resultados-de-pruebas) | ⏳ capturas |
| 7 | Pantallas | Capturas de las funcionalidades entregadas en el sprint | [`docs/screenshots/`](../../screenshots/) (detalle en [Pantallas](#pantallas)) | ✅ |
| 8 | Acta | Acta de la revisión y retrospectiva del sprint (asistentes, acuerdos, acciones de mejora) | `sprint-review-minutes.pdf` | ⏳ |

## Pull requests

Datos tomados de `git log` (commits de fusión en `develop`, hora de Ecuador) y del estado de los checks de GitHub Actions de cada PR. Todos los PR tienen como base `develop`.

| PR | Historia | Descripción | Estado CI | Fusionado |
|---|---|---|---|---|
| [#6](https://github.com/Ptrickill/Titulacion/pull/6) | — (chore) | Sincroniza `develop` con `main` (TEC-01, TEC-02, TEC-04) y documenta el flujo de ramas: los PR de `feature/*` y `chore/*` van hacia `develop` | No aplica: el workflow CI se agregó en #7 | 27/10/2026 23:50 |
| [#7](https://github.com/Ptrickill/Titulacion/pull/7) | TEC-03 | Pipeline de GitHub Actions (backend y frontend), plantillas de issue y PR, documentación del proyecto e historias HU-01 a HU-18 | ✅ Backend · ✅ Frontend | 28/10/2026 00:15 |
| [#8](https://github.com/Ptrickill/Titulacion/pull/8) | TEC-05 | Base de la interfaz según el prototipo: tema, layout por rol y componentes compartidos de `shared/ui` | ✅ Backend · ✅ Frontend | 28/10/2026 23:29 |
| [#9](https://github.com/Ptrickill/Titulacion/pull/9) | HU-01 | Inicio de sesión con JWT, bloqueo de cuenta y auditoría; página de login y sesión en `AuthService` | ✅ Backend · ✅ Frontend | 29/10/2026 00:04 |
| [#10](https://github.com/Ptrickill/Titulacion/pull/10) | HU-04 | Acceso a la API por rol con 403 auditado; aviso de sección no permitida y página de acceso denegado | ✅ Backend · ✅ Frontend | 29/10/2026 00:28 |
| [#11](https://github.com/Ptrickill/Titulacion/pull/11) | HU-02 | Creación de cuentas con rol y vínculo del portal a un paciente; página de gestión de usuarios y diálogo de creación | ✅ Backend · ✅ Frontend | 29/10/2026 01:23 |
| [#12](https://github.com/Ptrickill/Titulacion/pull/12) | HU-03 | Activación y desactivación de cuentas desde la tabla de usuarios | ✅ Backend · ✅ Frontend | 29/10/2026 22:14 |
| [#13](https://github.com/Ptrickill/Titulacion/pull/13) | HU-05 | Registro de pacientes con validación de cédula; diálogo de registro y ficha del paciente | ✅ Backend · ✅ Frontend | 29/10/2026 23:07 |
| [#14](https://github.com/Ptrickill/Titulacion/pull/14) | HU-06 | Búsqueda de pacientes y actualización de datos personales; tabla paginada y formulario de edición | ✅ Backend · ✅ Frontend | 29/10/2026 23:51 |

## Resultados de pruebas

Ejecutadas el 30/10/2026 sobre `develop` (commit `2016e39`, fusión del PR #14):

| Comando | Resultado |
|---|---|
| `.\mvnw.cmd clean verify` (en `backend/`) | 221 pruebas, 0 fallos, 0 errores, 0 omitidas — BUILD SUCCESS |
| `npx ng test --watch=false` (en `frontend/`) | 125 pruebas en 28 archivos, todas en verde |

## Pantallas

Capturas de la verificación de cada historia (datos sintéticos):

| Historia | Carpeta | Capturas |
|---|---|---|
| TEC-05 | [`tec-05-ui-foundation`](../../screenshots/tec-05-ui-foundation/) | 15 pantallas del prototipo por rol, catálogo `/ui-kit` y vista móvil |
| HU-01 | [`hu-01-login`](../../screenshots/hu-01-login/) | [login](../../screenshots/hu-01-login/01-login.png), [validación](../../screenshots/hu-01-login/02-login-validation.png), [credenciales inválidas](../../screenshots/hu-01-login/03-login-invalid-credentials.png), [formulario lleno](../../screenshots/hu-01-login/04-login-filled.png), [panel tras el login](../../screenshots/hu-01-login/05-admin-panel-after-login.png), [móvil](../../screenshots/hu-01-login/06-mobile-login.png) |
| HU-02 | [`hu-02-user-management`](../../screenshots/hu-02-user-management/) | [lista de usuarios](../../screenshots/hu-02-user-management/01-users-list.png), [validación](../../screenshots/hu-02-user-management/02-create-dialog-validation.png), [correo duplicado](../../screenshots/hu-02-user-management/03-create-dialog-duplicate-email.png), [vínculo con paciente](../../screenshots/hu-02-user-management/04-create-dialog-patient-search.png), [filtro por rol](../../screenshots/hu-02-user-management/05-users-filter-physio.png), [búsqueda sin tildes](../../screenshots/hu-02-user-management/06-users-search-accents.png), [móvil](../../screenshots/hu-02-user-management/07-mobile-users.png) |
| HU-03 | [`hu-03-account-status`](../../screenshots/hu-03-account-status/) | [acciones de estado](../../screenshots/hu-03-account-status/01-users-status-actions.png), [propia cuenta deshabilitada](../../screenshots/hu-03-account-status/02-own-account-disabled.png), [confirmar desactivación](../../screenshots/hu-03-account-status/03-deactivate-confirm.png), [cuenta desactivada](../../screenshots/hu-03-account-status/04-deactivated.png), [login de cuenta inactiva](../../screenshots/hu-03-account-status/05-login-inactive-account.png), [sesión abierta cortada](../../screenshots/hu-03-account-status/06-open-session-cut.png), [confirmar activación](../../screenshots/hu-03-account-status/07-activate-confirm.png), [cuenta reactivada](../../screenshots/hu-03-account-status/08-reactivated.png) |
| HU-04 | [`hu-04-role-access`](../../screenshots/hu-04-role-access/) | [fisioterapeuta en sección de administrador](../../screenshots/hu-04-role-access/01-physio-tries-admin.png), [paciente en sección de fisioterapeuta](../../screenshots/hu-04-role-access/02-patient-tries-physio.png), [administrador en sección de dirección](../../screenshots/hu-04-role-access/03-admin-tries-director.png), [acceso denegado](../../screenshots/hu-04-role-access/04-access-denied-page.png), [móvil](../../screenshots/hu-04-role-access/05-mobile-patient-tries-admin.png) |
| HU-05 | [`hu-05-patient-registration`](../../screenshots/hu-05-patient-registration/) | [página de pacientes](../../screenshots/hu-05-patient-registration/01-patients-page.png), [diálogo de registro](../../screenshots/hu-05-patient-registration/02-register-dialog.png), [cédula inválida](../../screenshots/hu-05-patient-registration/03-validation-errors.png), [formulario lleno](../../screenshots/hu-05-patient-registration/04-dialog-filled.png), [campos opcionales](../../screenshots/hu-05-patient-registration/05-dialog-filled-bottom.png), [paciente registrado](../../screenshots/hu-05-patient-registration/06-patient-registered.png), [cédula existente](../../screenshots/hu-05-patient-registration/07-duplicate-notice.png), [registro existente](../../screenshots/hu-05-patient-registration/08-existing-record.png), [móvil](../../screenshots/hu-05-patient-registration/09-mobile-dialog.png) |
| HU-06 | [`hu-06-patient-search`](../../screenshots/hu-06-patient-search/) | [lista de pacientes](../../screenshots/hu-06-patient-search/01-patients-list.png), [segunda página](../../screenshots/hu-06-patient-search/02-second-page.png), [búsqueda sin tildes](../../screenshots/hu-06-patient-search/03-search-without-accents.png), [cédula parcial](../../screenshots/hu-06-patient-search/04-search-partial-national-id.png), [edición](../../screenshots/hu-06-patient-search/05-edit-dialog.png), [datos actualizados](../../screenshots/hu-06-patient-search/06-updated.png), [última actualización](../../screenshots/hu-06-patient-search/07-record-last-update.png) |

## Observaciones

- Las historias no tienen issue en GitHub, por eso los PR no usan `Closes #N`: la historia se identifica por su código (HU-XX o TEC-XX) en la rama, en los commits y en la descripción del PR.
- Revisión de los PR: ⏳ completar quién revisó y aprobó cada PR; este dato no está en el repositorio.
- Decisión de alcance en HU-06: el prototipo 10 muestra las columnas "Sesiones" y "Estado" y el filtro Activo/Inactivo. No se implementaron porque dependen del tratamiento, que es dato clínico, y HU-06 exige que el administrador no lo vea.
