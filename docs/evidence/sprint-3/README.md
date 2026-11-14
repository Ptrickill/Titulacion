# Evidencias — Sprint 3

Registro de las evidencias del Sprint 3. Guardar los archivos en esta carpeta con los nombres indicados y actualizar la columna **Estado** (✅ entregada, ⏳ pendiente).

| Fecha de inicio | Fecha de cierre | Objetivo del sprint | Versión |
|---|---|---|---|
| 26/10/2026 | 08/11/2026 | Entregar HU-01 a HU-11 y HU-18, según `docs/user-stories.md`: la autenticación y el control de acceso por rol (PB-01: HU-01 a HU-04), la gestión de datos personales de pacientes (PB-02: HU-05 y HU-06), el historial clínico y su auditoría (PB-03: HU-07, HU-08, HU-09 y HU-18), el registro de sesiones (PB-04: HU-10) y la línea de tiempo (PB-05: HU-11). Son 53 puntos en 12 historias | [`v0.4.0`](https://github.com/Ptrickill/Titulacion/releases/tag/v0.4.0) (commit `f52ce9c`, fusión del PR #23 en `main`): HU-01 a HU-09 y HU-18 · [`v0.5.0`](https://github.com/Ptrickill/Titulacion/releases/tag/v0.5.0) (13/11/2026, commit `4ff8863`, fusión del PR #55 en `main`): HU-10 y HU-11 |

El sprint se planificó con PB-01 y PB-02 (21 puntos en 6 historias). Dentro de su ventana se incorporaron dos incrementos planificados originalmente para el sprint 4:

- [Incremento: HU-07, HU-08, HU-09, HU-18 y TEC-06](#incremento-hu-07-hu-08-hu-09-hu-18-y-tec-06): 19 puntos en 4 historias.
- [Incremento complementario: HU-10 y HU-11](#incremento-complementario-hu-10-y-hu-11): 13 puntos en 2 historias.

Historias y tareas del sprint (puntos según [`docs/user-stories.md`](../../user-stories.md)):

| Elemento | Puntos | Issue | PR |
|---|---|---|---|
| HU-01 Iniciar sesión | 3 | [#25](https://github.com/Ptrickill/Titulacion/issues/25) | [#9](https://github.com/Ptrickill/Titulacion/pull/9) |
| HU-02 Crear usuarios y asignar rol | 5 | [#26](https://github.com/Ptrickill/Titulacion/issues/26) | [#11](https://github.com/Ptrickill/Titulacion/pull/11) |
| HU-03 Activar o desactivar cuentas | 2 | [#27](https://github.com/Ptrickill/Titulacion/issues/27) | [#12](https://github.com/Ptrickill/Titulacion/pull/12) |
| HU-04 Bloquear funciones ajenas al rol | 3 | [#28](https://github.com/Ptrickill/Titulacion/issues/28) | [#10](https://github.com/Ptrickill/Titulacion/pull/10) |
| HU-05 Registrar paciente | 5 | [#29](https://github.com/Ptrickill/Titulacion/issues/29) | [#13](https://github.com/Ptrickill/Titulacion/pull/13) |
| HU-06 Buscar y actualizar pacientes | 3 | [#30](https://github.com/Ptrickill/Titulacion/issues/30) | [#14](https://github.com/Ptrickill/Titulacion/pull/14) |
| TEC-06 Datos sintéticos de desarrollo | — (tarea técnica) | — | [#18](https://github.com/Ptrickill/Titulacion/pull/18) |
| HU-07 Crear historial clínico | 8 | [#31](https://github.com/Ptrickill/Titulacion/issues/31) | [#19](https://github.com/Ptrickill/Titulacion/pull/19) |
| HU-08 Asignar responsable y estado del tratamiento | 3 | [#32](https://github.com/Ptrickill/Titulacion/issues/32) | [#20](https://github.com/Ptrickill/Titulacion/pull/20) |
| HU-09 Consultar y editar el historial clínico | 3 | [#33](https://github.com/Ptrickill/Titulacion/issues/33) | [#21](https://github.com/Ptrickill/Titulacion/pull/21) |
| HU-18 Auditoría de información clínica | 5 (técnica) | [#42](https://github.com/Ptrickill/Titulacion/issues/42) | [#22](https://github.com/Ptrickill/Titulacion/pull/22) |
| HU-10 Registrar sesión terapéutica | 8 | [#34](https://github.com/Ptrickill/Titulacion/issues/34) | [#45](https://github.com/Ptrickill/Titulacion/pull/45) |
| HU-11 Línea de tiempo de sesiones | 5 | [#35](https://github.com/Ptrickill/Titulacion/issues/35) | [#48](https://github.com/Ptrickill/Titulacion/pull/48) y [#50](https://github.com/Ptrickill/Titulacion/pull/50) |
| **Total** | **53** | | |

Las secciones [Pull requests](#pull-requests), [Resultados de pruebas](#resultados-de-pruebas), [Pantallas](#pantallas) y [Observaciones](#observaciones) corresponden a HU-01 a HU-06. Cada incremento tiene las suyas dentro de su sección.

## Evidencias

| # | Evidencia | Descripción | Archivo / enlace | Estado |
|---|---|---|---|---|
| 1 | Tablero al inicio | Captura del tablero (GitHub Projects) con el backlog del sprint comprometido | `board-start.png` | ⏳ |
| 2 | Tablero al cierre | Captura del tablero con las historias terminadas y las que pasan al siguiente sprint | `board-end.png` | ⏳ |
| 3 | Burndown | Gráfico de puntos restantes por día del sprint | `burndown.png` | ⏳ |
| 4 | Pull requests | PR fusionados hacia `develop` con su historia | Tablas de [HU-01 a HU-06](#pull-requests), del [historial clínico y la auditoría](#pull-requests-historial-clínico-y-auditoría) y de las [sesiones y la línea de tiempo](#pull-requests-sesiones-y-línea-de-tiempo) | ✅ PR, historia y fecha; estado de la CI hasta el PR #43. Sin aprobaciones registradas (ver [Defectos del proceso](#defectos-del-proceso)) |
| 5 | Integración continua | Ejecución en verde del workflow **CI** (backend y frontend) del último PR de cada incremento | `ci-run.png` + [ejecución del PR #14](https://github.com/Ptrickill/Titulacion/actions/runs/41004910223) y [en `develop` tras su fusión](https://github.com/Ptrickill/Titulacion/actions/runs/41005233539) · [ejecución del PR #43](https://github.com/Ptrickill/Titulacion/actions/runs/41970821629) y [en `develop` tras su fusión](https://github.com/Ptrickill/Titulacion/actions/runs/41971221105) · [ejecución del PR #50](https://github.com/Ptrickill/Titulacion/actions/runs/42758690409) | ⏳ captura `ci-run.png` |
| 6 | Pruebas | Resultado de `mvnw clean verify` (artefacto `surefire-reports`) y de `ng test` | `tests-backend.png`, `tests-frontend.png` · totales de [HU-01 a HU-06](#resultados-de-pruebas), del [historial clínico y la auditoría](#resultados-de-pruebas-historial-clínico-y-auditoría) y de las [sesiones y la línea de tiempo](#resultados-de-pruebas-sesiones-y-línea-de-tiempo) | ⏳ capturas |
| 7 | Pantallas | Capturas de las funcionalidades entregadas en el sprint | [`docs/screenshots/`](../../screenshots/): [HU-01 a HU-06](#pantallas), [historial clínico y auditoría](#pantallas-historial-clínico-y-auditoría) y [sesiones y línea de tiempo](#pantallas-sesiones-y-línea-de-tiempo) | ✅ |
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

- Desde el 05/11/2026 cada historia tiene su issue (#25 a #42), cerrado con el PR que la implementa.
- Revisión de los PR: GitHub no registra aprobaciones en los PR #6 a #14. El desarrollo lo ejecuta un solo integrante y GitHub no permite aprobar un PR propio (ver [Defectos del proceso](#defectos-del-proceso)).
- Decisión de alcance en HU-06: el prototipo 10 muestra las columnas "Sesiones" y "Estado" y el filtro Activo/Inactivo. No se implementaron porque dependen del tratamiento, que es dato clínico, y HU-06 exige que el administrador no lo vea.

## Incremento: HU-07, HU-08, HU-09, HU-18 y TEC-06

Este incremento entrega el historial clínico (PB-03: HU-07, HU-08 y HU-09) y la auditoría de información clínica (HU-18), con los datos sintéticos de desarrollo (TEC-06): 19 puntos en 4 historias. Se planificó para el sprint 4 y se incorporó dentro de la ventana del sprint 3 (ver la nota de [`docs/user-stories.md`](../../user-stories.md)).

Se publicó en la versión [`v0.4.0`](https://github.com/Ptrickill/Titulacion/releases/tag/v0.4.0) (commit `f52ce9c`, fusión del PR #23 en `main`). Hasta SCRUM-52, esta sección era el documento `docs/evidence/sprint-4/README.md`.

### Pull requests (historial clínico y auditoría)

Datos tomados de `git log` (commit de fusión de cada PR, hora de Ecuador) y del estado de los checks de GitHub Actions de cada PR.

| PR | Elemento | Descripción | Estado CI | Fusionado |
|---|---|---|---|---|
| [#17](https://github.com/Ptrickill/Titulacion/pull/17) | — (sincronización `main` → `develop`) | Lleva a `develop` la fusión de la publicación del sprint 3 en `main` (PR #16, `v0.3.0`) | ✅ Backend · ✅ Frontend | 30/10/2026 00:30 |
| [#18](https://github.com/Ptrickill/Titulacion/pull/18) | TEC-06 | Datos sintéticos para el perfil `dev`: migración repetible, cuentas del seed y contraseña desde `DEV_SEED_PASSWORD` | ✅ Backend · ✅ Frontend | 30/10/2026 00:53 |
| [#19](https://github.com/Ptrickill/Titulacion/pull/19) | HU-07 | Historial clínico con hábitos, antecedentes, diagnóstico CIE-10, EVA inicial y plan terapéutico; lista de pacientes del fisioterapeuta y formulario del historial | ✅ Backend · ✅ Frontend | 30/10/2026 09:45 |
| [#20](https://github.com/Ptrickill/Titulacion/pull/20) | HU-08 | Fisioterapeuta responsable y estado del tratamiento con motivo e histórico; tarjeta del plan terapéutico y diálogos de cambio | ✅ Backend · ✅ Frontend | 30/10/2026 11:57 |
| [#21](https://github.com/Ptrickill/Titulacion/pull/21) | HU-09 | Consulta del expediente en una consulta optimizada con auditoría `READ` y edición de hábitos y antecedentes; vista del expediente | ✅ Backend · ✅ Frontend | 30/10/2026 14:59 |
| [#22](https://github.com/Ptrickill/Titulacion/pull/22) | HU-18 | Auditoría de todas las operaciones clínicas, versiones con Hibernate Envers y consulta `GET /api/audit-logs`; pantalla de auditoría para administración y dirección | ✅ Backend · ✅ Frontend | 30/10/2026 16:00 |
| [#23](https://github.com/Ptrickill/Titulacion/pull/23) | — (publicación `develop` → `main`) | "Release Sprint 4": lleva a `main` las historias de este alcance (`v0.4.0`) | ✅ Backend · ✅ Frontend | 30/10/2026 16:06 |
| [#24](https://github.com/Ptrickill/Titulacion/pull/24) | — (sincronización `main` → `develop`) | Lleva a `develop` la fusión del PR #23 | ✅ Backend · ✅ Frontend | 30/10/2026 16:10 |
| [#43](https://github.com/Ptrickill/Titulacion/pull/43) | — (chore) | Alinea `docs/user-stories.md` y `CLAUDE.md` con los milestones de GitHub y registra los issues de las historias pendientes | ✅ Backend · ✅ Frontend | 05/11/2026 00:00 |

### Resultados de pruebas (historial clínico y auditoría)

Ejecutadas el 05/11/2026 sobre `develop` (commit `b4441b9`, fusión del PR #43):

| Comando | Total | Fallos | Errores | Omitidas | Resultado |
|---|---|---|---|---|---|
| `.\mvnw.cmd clean verify` (en `backend/`) | 391 | 0 | 0 | 0 | BUILD SUCCESS |
| `npx ng test --watch=false` (en `frontend/`) | 187 (40 archivos) | 0 | 0 | 0 | Todas en verde |

### Pantallas (historial clínico y auditoría)

Capturas de la verificación de cada historia (datos sintéticos):

| Historia | Carpeta | Capturas |
|---|---|---|
| TEC-06 | [`tec-06-dev-seed`](../../screenshots/tec-06-dev-seed/) | [pacientes del seed](../../screenshots/tec-06-dev-seed/01-seed-patients.png), [cuentas del seed](../../screenshots/tec-06-dev-seed/02-seed-accounts.png), [login del fisioterapeuta](../../screenshots/tec-06-dev-seed/03-seed-physio-login.png), [portal del paciente](../../screenshots/tec-06-dev-seed/04-seed-patient-portal.png) |
| HU-07 | [`hu-07-clinical-record`](../../screenshots/hu-07-clinical-record/) | [pacientes sin historial](../../screenshots/hu-07-clinical-record/01-physio-patients-without-records.png), [formulario del historial](../../screenshots/hu-07-clinical-record/02-record-form.png), [validación](../../screenshots/hu-07-clinical-record/03-validation.png), [búsqueda CIE-10](../../screenshots/hu-07-clinical-record/04-icd10-search.png), [plan y responsable](../../screenshots/hu-07-clinical-record/05-plan-and-responsible.png), [historial creado](../../screenshots/hu-07-clinical-record/06-record-created.png), [historial existente](../../screenshots/hu-07-clinical-record/07-record-already-exists.png), [nuevo tratamiento en móvil](../../screenshots/hu-07-clinical-record/08-mobile-new-treatment.png), [paciente con resumen](../../screenshots/hu-07-clinical-record/09-patient-with-summary.png) |
| HU-08 | [`hu-08-treatment-status`](../../screenshots/hu-08-treatment-status/) | [enlace al plan](../../screenshots/hu-08-treatment-status/01-patient-list-plan-link.png), [tarjeta del plan](../../screenshots/hu-08-treatment-status/02-plan-card.png), [cambiar responsable](../../screenshots/hu-08-treatment-status/03-change-responsible-dialog.png), [responsable cambiado](../../screenshots/hu-08-treatment-status/04-responsible-changed.png), [estados desde Activo](../../screenshots/hu-08-treatment-status/05-status-options-from-active.png), [motivo obligatorio](../../screenshots/hu-08-treatment-status/06-reason-required.png), [reanudar solo a Activo](../../screenshots/hu-08-treatment-status/07-resume-only-active.png), [aviso de estado final](../../screenshots/hu-08-treatment-status/08-complete-final-notice.png), [histórico del finalizado](../../screenshots/hu-08-treatment-status/09-completed-history.png), [móvil](../../screenshots/hu-08-treatment-status/10-mobile-plan-card.png) |
| HU-09 | [`hu-09-clinical-record-view`](../../screenshots/hu-09-clinical-record-view/) | [enlace al expediente](../../screenshots/hu-09-clinical-record-view/01-patient-list-view-link.png), [expediente](../../screenshots/hu-09-clinical-record-view/02-record-view.png), [plan y tratamientos anteriores](../../screenshots/hu-09-clinical-record-view/03-plan-history-previous.png), [edición precargada](../../screenshots/hu-09-clinical-record-view/04-edit-form-preloaded.png), [campo obligatorio](../../screenshots/hu-09-clinical-record-view/05-edit-required-field.png), [historial actualizado](../../screenshots/hu-09-clinical-record-view/06-record-updated.png), [sin tratamiento activo](../../screenshots/hu-09-clinical-record-view/07-no-active-treatment.png), [sin historial](../../screenshots/hu-09-clinical-record-view/08-without-record.png), [móvil](../../screenshots/hu-09-clinical-record-view/09-mobile-record-view.png) |
| HU-18 | [`hu-18-audit`](../../screenshots/hu-18-audit/) | [registro de auditoría](../../screenshots/hu-18-audit/01-admin-audit-log.png), [filtro por usuario y entidad](../../screenshots/hu-18-audit/02-filter-user-and-entity.png), [opciones de usuario](../../screenshots/hu-18-audit/03-user-filter-options.png), [rango de fechas inválido](../../screenshots/hu-18-audit/04-invalid-date-range.png), [accesos denegados vistos por la dirección](../../screenshots/hu-18-audit/05-director-access-denied.png), [fisioterapeuta redirigido](../../screenshots/hu-18-audit/06-physio-redirected.png), [móvil](../../screenshots/hu-18-audit/07-mobile-audit-log.png) |

### Observaciones (historial clínico y auditoría)

1. La pantalla de auditoría se incorpora durante la implementación como medio de verificación de HU-18 y no forma parte del prototipo inicial.
2. HU-08 restringe las transiciones de estado del tratamiento a `ACTIVE → COMPLETED`, `ACTIVE → ABANDONED` y `ABANDONED → ACTIVE` (esta última solo si el historial no tiene otro tratamiento activo). `COMPLETED` es un estado final.
3. HU-09 incorpora la edición de hábitos y antecedentes (`PUT /api/patients/{patientId}/clinical-record`), que ya estaba en el contrato de la API.
4. Desde el 05/11/2026 cada historia tiene su issue (#25 a #42), y `develop` y `main` exigen un PR aprobado y los checks de CI en verde (ruleset "Protección develop y main": 1 aprobación y los checks Backend y Frontend). Los PR #17 a #24 y #43 se fusionaron antes, por eso no usan `Closes #N`. Desde el PR #52 la regla ya no exige la aprobación (ver [Defectos del proceso](#defectos-del-proceso)).
5. Revisión de los PR: GitHub no registra aprobaciones en los PR #17 a #24 ni en #43, porque el desarrollo lo ejecuta un solo integrante (ver [Defectos del proceso](#defectos-del-proceso)).

## Incremento complementario: HU-10 y HU-11

El sprint 3 incorpora además el registro de sesiones (PB-04: HU-10) y la línea de tiempo terapéutica (PB-05: HU-11), planificadas originalmente para el sprint 4: 13 puntos en 2 historias. Con ellas, el sprint 3 cubre HU-01 a HU-11 y HU-18.

### Pull requests (sesiones y línea de tiempo)

Fechas tomadas de `git log` (commit de fusión de cada PR en `develop`, hora de Ecuador). Todos los PR tienen como base `develop`. Los PR #49 y #50 se fusionaron el 09/11/2026, un día después de la fecha de cierre del sprint.

| PR | Historia | Descripción | Fusionado |
|---|---|---|---|
| [#45](https://github.com/Ptrickill/Titulacion/pull/45) | HU-10 | Registro y corrección de sesiones terapéuticas, con el correlativo y el responsable asignados por el servidor y las versiones de cada corrección; lista de sesiones, formulario y diálogo de versiones | 06/11/2026 19:54 |
| [#46](https://github.com/Ptrickill/Titulacion/pull/46) | — (corrección, hallazgo de HU-10) | Los errores propios de Spring MVC (405, 415, 400 y 404) responden con el título y el detalle en español | 06/11/2026 20:40 |
| [#47](https://github.com/Ptrickill/Titulacion/pull/47) | — (corrección, continúa el #46) | Solo las excepciones de la aplicación conservan su mensaje; se agrega `InvalidRequestException` (400) | 08/11/2026 22:15 |
| [#48](https://github.com/Ptrickill/Titulacion/pull/48) | HU-11 | Línea de tiempo terapéutica (`GET /api/treatments/{treatmentId}/timeline`) con la curva de EVA por sesión y el filtro por rango de fechas | 08/11/2026 23:13 |
| [#49](https://github.com/Ptrickill/Titulacion/pull/49) | — (corrección) | Se elimina `hibernate.jdbc.time_zone`: la fecha y la hora de la sesión se guardan tal como llegan, sea cual sea la zona horaria de la JVM | 09/11/2026 23:04 |
| [#50](https://github.com/Ptrickill/Titulacion/pull/50) | HU-11 | Página de entrada `/physio/timeline` para elegir al paciente y acción "Línea de tiempo" en la lista de pacientes del fisioterapeuta | 09/11/2026 23:58 |

### Pantallas (sesiones y línea de tiempo)

Capturas de la verificación de cada historia (datos sintéticos):

| Historia | Carpeta | Capturas |
|---|---|---|
| HU-10 | [`hu-10-therapy-sessions`](../../screenshots/hu-10-therapy-sessions/) | 12 |
| HU-11 | [`hu-11-timeline`](../../screenshots/hu-11-timeline/) | 22 |

### Decisiones

1. **Solo el autor corrige la sesión (HU-10).** Si otro fisioterapeuta intenta corregirla, la API responde 403 con el detalle "Solo el fisioterapeuta que registró la sesión puede corregirla." y el intento queda en la auditoría (`ACCESS_DENIED`).
2. **La curva de EVA excluye las sesiones `RESCHEDULED` y `NO_SHOW` (HU-11).** Esas sesiones aparecen en la línea de tiempo, pero la curva solo tiene las sesiones realizadas (`COMPLETED`).
3. **La curva parte de la evaluación inicial (HU-11).** Su primer punto es siempre la EVA inicial del tratamiento, con su fecha de inicio, aunque haya un filtro de fechas.
4. **La página de entrada lista todos los pacientes (HU-11).** Usa `GET /api/physio/patients` sin cambios. Un paciente sin tratamiento aparece con la etiqueta "Sin tratamiento" y sin la acción de abrir la línea de tiempo.

### Defectos corregidos

| PR | Defecto | Severidad | Corrección |
|---|---|---|---|
| [#46](https://github.com/Ptrickill/Titulacion/pull/46) y [#47](https://github.com/Ptrickill/Titulacion/pull/47) | Mensajes de error en inglés: el 405 de `DELETE /api/therapy-sessions/{id}` y otros errores propios de Spring MVC respondían con el título y el detalle en inglés | Baja | Todos los errores responden en español; el detalle nunca repite un valor enviado por el cliente |
| [#49](https://github.com/Ptrickill/Titulacion/pull/49) | Zona horaria: con la JVM en UTC (la CI y la imagen Docker), la hora de la sesión (`session_time`) se guardaba y se leía corrida 5 horas | Media | Se elimina `hibernate.jdbc.time_zone`; `TherapySessionTimeZoneIntegrationTests` lo comprueba con la JVM en UTC |

### Defectos del proceso

| Hallazgo | Acción |
|---|---|
| La regla "Protección develop y main" exige una aprobación que el proyecto no registra: el desarrollo lo ejecuta un solo integrante y GitHub no permite aprobar un PR propio. Los PR #45 a #51 se fusionaron mediante la omisión de la regla por parte del administrador. | Desde el PR #52, la regla "Protección develop y main" exige PR y los checks Backend y Frontend en verde, sin aprobación ni omisión. Desde el PR #53, el autor completa en la descripción del PR la lista de verificación de la definición de terminado antes de fusionar. |

Observación: El PR #55 (publicación de v0.5.0 en main) se fusionó con la descripción de la plantilla sin completar la lista de verificación.

### Resultados de pruebas (sesiones y línea de tiempo)

Ejecutadas el 13/11/2026 sobre `develop` (commit `25decac`, fusión del PR #51, que es posterior a este incremento):

| Comando | Total | Fallos | Errores | Omitidas | Resultado |
|---|---|---|---|---|---|
| `.\mvnw.cmd clean verify` (en `backend/`) | 496 | 0 | 0 | 0 | BUILD SUCCESS |
| `npx ng test --watch=false` (en `frontend/`) | 246 (47 archivos) | 0 | 0 | 0 | Todas en verde |

### Pendiente de completar

Estos datos no están en el repositorio:

| Elemento | Estado |
|---|---|
| Acta de la revisión del sprint | ⏳ |
| Retrospectiva | ⏳ |
| Burndown | ⏳ |
