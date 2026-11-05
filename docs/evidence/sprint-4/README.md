# Evidencias — Sprint 4

Registro de las evidencias de las historias del Sprint 4. Guardar los archivos en esta carpeta con los nombres indicados y actualizar la columna **Estado** (✅ entregada, ⏳ pendiente).

Las historias de este alcance se planificaron para el sprint 4 y se incorporaron dentro de la ventana del sprint 3 (ver la nota de [`docs/user-stories.md`](../../user-stories.md)).

| Fecha de inicio | Fecha de cierre | Objetivo del sprint | Versión entregada |
|---|---|---|---|
| 26/10/2026 | 08/11/2026 (ventana del sprint 3) | Entregar el historial clínico (PB-03: HU-07, HU-08 y HU-09) y la auditoría de información clínica (HU-18), con los datos sintéticos de desarrollo (TEC-06): 19 puntos en 4 historias | [`v0.4.0`](https://github.com/Ptrickill/Titulacion/releases/tag/v0.4.0) (commit `f52ce9c`, fusión del PR #23 en `main`) |

Historias del alcance (puntos según [`docs/user-stories.md`](../../user-stories.md)):

| Historia | Puntos | Issue | PR |
|---|---|---|---|
| TEC-06 Datos sintéticos de desarrollo | — (tarea técnica) | — | [#18](https://github.com/Ptrickill/Titulacion/pull/18) |
| HU-07 Crear historial clínico | 8 | [#31](https://github.com/Ptrickill/Titulacion/issues/31) | [#19](https://github.com/Ptrickill/Titulacion/pull/19) |
| HU-08 Asignar responsable y estado del tratamiento | 3 | [#32](https://github.com/Ptrickill/Titulacion/issues/32) | [#20](https://github.com/Ptrickill/Titulacion/pull/20) |
| HU-09 Consultar y editar el historial clínico | 3 | [#33](https://github.com/Ptrickill/Titulacion/issues/33) | [#21](https://github.com/Ptrickill/Titulacion/pull/21) |
| HU-18 Auditoría de información clínica | 5 (técnica) | [#42](https://github.com/Ptrickill/Titulacion/issues/42) | [#22](https://github.com/Ptrickill/Titulacion/pull/22) |

## Evidencias

| # | Evidencia | Descripción | Archivo / enlace | Estado |
|---|---|---|---|---|
| 1 | Tablero al inicio | Captura del tablero (GitHub Projects) con el backlog comprometido | `board-start.png` | ⏳ |
| 2 | Tablero al cierre | Captura del tablero con las historias terminadas y las que pasan al siguiente sprint | `board-end.png` | ⏳ |
| 3 | Burndown | Gráfico de puntos restantes por día del sprint | `burndown.png` | ⏳ |
| 4 | Pull requests | PR fusionados con su elemento y revisión | Tabla [Pull requests](#pull-requests) | ✅ PR, elemento, CI y fecha · ⏳ revisores (ver [Observaciones](#observaciones)) |
| 5 | Integración continua | Ejecución en verde del workflow **CI** (backend y frontend) del último PR | `ci-run.png` + [ejecución del PR #43](https://github.com/Ptrickill/Titulacion/actions/runs/41970821629) y [ejecución en `develop` tras la fusión](https://github.com/Ptrickill/Titulacion/actions/runs/41971221105) | ⏳ captura `ci-run.png` |
| 6 | Pruebas | Resultado de `mvnw clean verify` (artefacto `surefire-reports`) y de `ng test` | `tests-backend.png`, `tests-frontend.png` · totales en [Resultados de pruebas](#resultados-de-pruebas) | ⏳ capturas |
| 7 | Pantallas | Capturas de las funcionalidades entregadas | [`docs/screenshots/`](../../screenshots/) (detalle en [Pantallas](#pantallas)) | ✅ |
| 8 | Acta | Acta de la revisión y retrospectiva (asistentes, acuerdos, acciones de mejora) | `sprint-review-minutes.pdf` | ⏳ |

## Pull requests

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

## Resultados de pruebas

Ejecutadas el 05/11/2026 sobre `develop` (commit `b4441b9`, fusión del PR #43):

| Comando | Total | Fallos | Errores | Omitidas | Resultado |
|---|---|---|---|---|---|
| `.\mvnw.cmd clean verify` (en `backend/`) | 391 | 0 | 0 | 0 | BUILD SUCCESS |
| `npx ng test --watch=false` (en `frontend/`) | 187 (40 archivos) | 0 | 0 | 0 | Todas en verde |

## Pantallas

Capturas de la verificación de cada historia (datos sintéticos):

| Historia | Carpeta | Capturas |
|---|---|---|
| TEC-06 | [`tec-06-dev-seed`](../../screenshots/tec-06-dev-seed/) | [pacientes del seed](../../screenshots/tec-06-dev-seed/01-seed-patients.png), [cuentas del seed](../../screenshots/tec-06-dev-seed/02-seed-accounts.png), [login del fisioterapeuta](../../screenshots/tec-06-dev-seed/03-seed-physio-login.png), [portal del paciente](../../screenshots/tec-06-dev-seed/04-seed-patient-portal.png) |
| HU-07 | [`hu-07-clinical-record`](../../screenshots/hu-07-clinical-record/) | [pacientes sin historial](../../screenshots/hu-07-clinical-record/01-physio-patients-without-records.png), [formulario del historial](../../screenshots/hu-07-clinical-record/02-record-form.png), [validación](../../screenshots/hu-07-clinical-record/03-validation.png), [búsqueda CIE-10](../../screenshots/hu-07-clinical-record/04-icd10-search.png), [plan y responsable](../../screenshots/hu-07-clinical-record/05-plan-and-responsible.png), [historial creado](../../screenshots/hu-07-clinical-record/06-record-created.png), [historial existente](../../screenshots/hu-07-clinical-record/07-record-already-exists.png), [nuevo tratamiento en móvil](../../screenshots/hu-07-clinical-record/08-mobile-new-treatment.png), [paciente con resumen](../../screenshots/hu-07-clinical-record/09-patient-with-summary.png) |
| HU-08 | [`hu-08-treatment-status`](../../screenshots/hu-08-treatment-status/) | [enlace al plan](../../screenshots/hu-08-treatment-status/01-patient-list-plan-link.png), [tarjeta del plan](../../screenshots/hu-08-treatment-status/02-plan-card.png), [cambiar responsable](../../screenshots/hu-08-treatment-status/03-change-responsible-dialog.png), [responsable cambiado](../../screenshots/hu-08-treatment-status/04-responsible-changed.png), [estados desde Activo](../../screenshots/hu-08-treatment-status/05-status-options-from-active.png), [motivo obligatorio](../../screenshots/hu-08-treatment-status/06-reason-required.png), [reanudar solo a Activo](../../screenshots/hu-08-treatment-status/07-resume-only-active.png), [aviso de estado final](../../screenshots/hu-08-treatment-status/08-complete-final-notice.png), [histórico del finalizado](../../screenshots/hu-08-treatment-status/09-completed-history.png), [móvil](../../screenshots/hu-08-treatment-status/10-mobile-plan-card.png) |
| HU-09 | [`hu-09-clinical-record-view`](../../screenshots/hu-09-clinical-record-view/) | [enlace al expediente](../../screenshots/hu-09-clinical-record-view/01-patient-list-view-link.png), [expediente](../../screenshots/hu-09-clinical-record-view/02-record-view.png), [plan y tratamientos anteriores](../../screenshots/hu-09-clinical-record-view/03-plan-history-previous.png), [edición precargada](../../screenshots/hu-09-clinical-record-view/04-edit-form-preloaded.png), [campo obligatorio](../../screenshots/hu-09-clinical-record-view/05-edit-required-field.png), [historial actualizado](../../screenshots/hu-09-clinical-record-view/06-record-updated.png), [sin tratamiento activo](../../screenshots/hu-09-clinical-record-view/07-no-active-treatment.png), [sin historial](../../screenshots/hu-09-clinical-record-view/08-without-record.png), [móvil](../../screenshots/hu-09-clinical-record-view/09-mobile-record-view.png) |
| HU-18 | [`hu-18-audit`](../../screenshots/hu-18-audit/) | [registro de auditoría](../../screenshots/hu-18-audit/01-admin-audit-log.png), [filtro por usuario y entidad](../../screenshots/hu-18-audit/02-filter-user-and-entity.png), [opciones de usuario](../../screenshots/hu-18-audit/03-user-filter-options.png), [rango de fechas inválido](../../screenshots/hu-18-audit/04-invalid-date-range.png), [accesos denegados vistos por la dirección](../../screenshots/hu-18-audit/05-director-access-denied.png), [fisioterapeuta redirigido](../../screenshots/hu-18-audit/06-physio-redirected.png), [móvil](../../screenshots/hu-18-audit/07-mobile-audit-log.png) |

## Observaciones

1. La pantalla de auditoría se incorpora durante la implementación como medio de verificación de HU-18 y no forma parte del prototipo inicial.
2. HU-08 restringe las transiciones de estado del tratamiento a `ACTIVE → COMPLETED`, `ACTIVE → ABANDONED` y `ABANDONED → ACTIVE` (esta última solo si el historial no tiene otro tratamiento activo). `COMPLETED` es un estado final.
3. HU-09 incorpora la edición de hábitos y antecedentes (`PUT /api/patients/{patientId}/clinical-record`), que ya estaba en el contrato de la API.
4. Desde el 05/11/2026 cada historia tiene su issue (#25 a #42), y `develop` y `main` exigen un PR aprobado y los checks de CI en verde (ruleset "Protección develop y main": 1 aprobación y los checks Backend y Frontend). Los PR #17 a #24 y #43 se fusionaron antes, por eso no usan `Closes #N`.
5. Revisión de los PR: ⏳ completar quién revisó y aprobó cada PR; GitHub no registra revisiones en los PR #17 a #24 ni en #43, y este dato no está en el repositorio.
