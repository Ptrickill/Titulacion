# Evidencias — Sprint 4

Registro de las evidencias del Sprint 4. Guardar los archivos en esta carpeta con los nombres indicados y actualizar la columna **Estado** (✅ entregada, ⏳ pendiente).

La evidencia de HU-07, HU-08, HU-09, HU-18 y TEC-06, que antes estaba en este documento, está en [la evidencia del sprint 3](../sprint-3/README.md#incremento-hu-07-hu-08-hu-09-hu-18-y-tec-06).

| Fecha de inicio | Fecha de cierre | Objetivo del sprint | Versión entregada |
|---|---|---|---|
| 09/11/2026 | 22/11/2026 | Entregar el portal del paciente (PB-06: HU-12 y HU-13) y los reportes para la dirección operativa (PB-07: HU-14 y HU-15), según `docs/user-stories.md`: 18 puntos en 4 historias | ⏳ |

Historias del sprint (puntos según [`docs/user-stories.md`](../../user-stories.md)):

| Historia | Puntos | Issue | PR |
|---|---|---|---|
| HU-12 Ver el avance del tratamiento en el portal | 5 | [#36](https://github.com/Ptrickill/Titulacion/issues/36) | [#57](https://github.com/Ptrickill/Titulacion/pull/57) |
| HU-13 Leer indicaciones y sesiones en el portal | 3 | [#37](https://github.com/Ptrickill/Titulacion/issues/37) | [#58](https://github.com/Ptrickill/Titulacion/pull/58) |
| HU-14 Indicadores de tratamientos y sesiones | 5 | [#38](https://github.com/Ptrickill/Titulacion/issues/38) | ⏳ |
| HU-15 Filtrar y exportar el reporte a PDF | 5 | [#39](https://github.com/Ptrickill/Titulacion/issues/39) | ⏳ |

## Evidencias

| # | Evidencia | Descripción | Archivo / enlace | Estado |
|---|---|---|---|---|
| 1 | Tablero al inicio | Captura del tablero con el backlog comprometido | `board-start.png` | ⏳ |
| 2 | Tablero al cierre | Captura del tablero con las historias terminadas y las que pasan al siguiente sprint | `board-end.png` | ⏳ |
| 3 | Burndown | Gráfico de puntos restantes por día del sprint | `burndown.png` | ⏳ |
| 4 | Pull requests | PR fusionados hacia `develop` con su historia, la CI en verde y la lista de verificación completada por el autor | Tabla [Pull requests](#pull-requests) | ⏳ |
| 5 | Integración continua | Ejecución en verde del workflow **CI** (backend y frontend) del último PR del sprint | `ci-run.png` | ⏳ |
| 6 | Pruebas | Resultado de `mvnw clean verify` (artefacto `surefire-reports`) y de `ng test` | `tests-backend.png`, `tests-frontend.png` · totales en [Resultados de pruebas](#resultados-de-pruebas) | ⏳ |
| 7 | Pantallas | Capturas de las funcionalidades entregadas | [`docs/screenshots/`](../../screenshots/) (detalle en [Pantallas](#pantallas)) | ⏳ |
| 8 | Acta | Acta de la revisión y retrospectiva (asistentes, acuerdos, acciones de mejora) | `sprint-review-minutes.pdf` | ⏳ |

## Pull requests

Fechas de fusión tomadas de GitHub (fusión en `develop`, hora de Ecuador).

| PR | Historia | Descripción | Estado CI | Lista de verificación | Fusionado |
|---|---|---|---|---|---|
| [#57](https://github.com/Ptrickill/Titulacion/pull/57) | HU-12 (SCRUM-28) | Portal del paciente: tratamientos, avance de sesiones y evolución del dolor, solo lectura | ✅ [ejecución](https://github.com/Ptrickill/Titulacion/actions/runs/43562090680) | Completa | 14/11/2026 00:38 |
| [#58](https://github.com/Ptrickill/Titulacion/pull/58) | HU-13 (SCRUM-29) | Portal del paciente: listado de sesiones e indicación del terapeuta, sin observaciones clínicas ni evolución | ⏳ | ⏳ | ⏳ |

## Resultados de pruebas

Los totales del cierre del sprint siguen pendientes. Totales parciales, por historia:

| Historia | Fecha | `.\mvnw.cmd clean verify` (en `backend/`) | `npx ng test --watch=false` (en `frontend/`) |
|---|---|---|---|
| HU-12 | 14/11/2026 | 571 pruebas, 0 fallos, 0 errores, 0 omitidas (BUILD SUCCESS) | 277 pruebas en 51 archivos |
| HU-13 | 14/11/2026 | 621 pruebas, 0 fallos, 0 errores, 0 omitidas (BUILD SUCCESS) | 303 pruebas en 53 archivos |

| Comando | Total | Fallos | Errores | Omitidas | Resultado |
|---|---|---|---|---|---|
| `.\mvnw.cmd clean verify` (en `backend/`) | ⏳ | ⏳ | ⏳ | ⏳ | ⏳ |
| `npx ng test --watch=false` (en `frontend/`) | ⏳ | ⏳ | ⏳ | ⏳ | ⏳ |

## Pantallas

| Historia | Carpeta | Capturas |
|---|---|---|
| HU-12 | [`hu-12-portal-progress`](../../screenshots/hu-12-portal-progress/) | 10: portal con el tratamiento activo (`01`, `02`), selector de tratamiento (`03`), tratamiento finalizado (`04`), 1366 px (`05`), un solo tratamiento con dolor que sube (`06`), paciente sin tratamientos (`07`), 390 px (`08`, `09`) y auditoría de las consultas y los accesos denegados (`10`) |
| HU-13 | [`hu-13-portal-sessions`](../../screenshots/hu-13-portal-sessions/) | 11: portal con las tres tarjetas (`01`, `02`), listado de sesiones (`03`), la tabla a 1366 y a 1920 px (`04`), tratamiento finalizado con el paginador (`05`), tratamiento sin indicaciones (`06`), paciente sin tratamientos (`07`), 1024 px (`08`) y 390 px (`09`, `10`) |
| HU-14 | ⏳ | ⏳ |
| HU-15 | ⏳ | ⏳ |

## Observaciones

### HU-12 (portal del paciente)

Decisiones tomadas al implementar la historia:

- **Identidad:** el paciente se obtiene siempre de la cuenta autenticada (`patients.portal_user_id`). Ningún endpoint del portal recibe el id del paciente.
- **Tratamiento ajeno o inexistente:** ambos responden el mismo 403 "No tiene acceso a ese tratamiento." y quedan como `ACCESS_DENIED`, para que la API no revele qué ids existen. El resto de la API conserva el 404.
- **Entidad del acceso denegado:** una ruta `/api/portal/treatments/{id}/...` se registra como `portal/treatments` con su id. Las rutas anteriores registran lo mismo que antes.
- **Auditoría de la lista:** un `READ Treatment` por cada tratamiento listado, insertados con un solo `INSERT`. La lista cuesta 6 lecturas y 1 `INSERT` con 1 o con 5 tratamientos; el avance, 5 sentencias con 1 o con 100 sesiones (`PortalPerformanceIntegrationTests`).
- **Curva de EVA:** mismas reglas que HU-11. Parte de la evaluación inicial y solo cuenta las sesiones realizadas; las reprogramadas y las inasistencias no aparecen.
- **Porcentaje:** sesiones realizadas sobre planificadas, redondeado hacia abajo y con tope de 100 %.
- **Texto del banner:** el prototipo dice que el tratamiento "avanza satisfactoriamente". El sistema no puede afirmarlo, así que muestra el diagnóstico y el fisioterapeuta responsable o, si el tratamiento no está activo, su estado.
- **Dolor que no baja:** la tarjeta dice "El dolor aumentó N puntos respecto a la evaluación inicial." o "Sin cambios respecto a la evaluación inicial.", en lugar de "Reducción de dolor lograda".
- **Fuera de HU-12:** la tarjeta de indicaciones y el listado de sesiones del prototipo son de HU-13. Las notificaciones y la próxima cita están fuera del alcance del proyecto.
- **Datos de las capturas:** el seed solo tiene cuentas y pacientes. Los historiales, tratamientos y sesiones se crearon por la API en la base desechable de Testcontainers.

### HU-13 (sesiones e indicaciones en el portal)

Decisiones tomadas al implementar la historia:

- **Tipo propio para el paciente:** el módulo `sessions` entrega las sesiones del portal en `PatientSessionView`, que no tiene las observaciones clínicas ni la evolución. `SessionSummary`, de HU-12, no cambió. Una prueba recorre los tipos públicos del módulo y falla si alguno lleva esos datos.
- **Campos excluidos:** una prueba de integración comprueba que el JSON de las sesiones no contiene `clinicalObservations` ni `progressStatus`, ni sus valores, y que cada sesión tiene exactamente sus nueve propiedades.
- **Última indicación:** es la de la sesión más reciente que tiene una, sea cual sea su estado, porque el fisioterapeuta la escribió para el paciente. Sin indicaciones, la API responde 200 sin cuerpo.
- **Indicaciones anteriores:** el listado muestra la indicación de cada sesión debajo de sus actividades, con el prefijo "Indicación:".
- **Reglas de HU-12:** paciente desde la cuenta autenticada, el mismo 403 auditado para un tratamiento ajeno o inexistente y `READ Treatment` en cada consulta. Cada endpoint nuevo cuesta 6 sentencias con 1 o con 100 sesiones.
- **Tres tarjetas en una fila,** como el prototipo; se apilan en pantallas angostas.
- **Desborde de la tabla:** Vitest corre en jsdom, que no calcula el diseño, así que su prueba protege la estructura. Los anchos se midieron en Chrome al tomar las capturas:

  | Ancho de ventana | Contenedor de la tabla | Tabla | Desborde |
  |---|---|---|---|
  | 1366 px | 850 px | 850 px | 0 px |
  | 1920 px | 850 px | 850 px | 0 px |
  | 1024 px | 726 px | 726 px | 0 px |
  | 390 px | 356 px | 723 px | 367 px, con desplazamiento dentro de la tarjeta; la página no se desborda |
