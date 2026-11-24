# Base de datos de producción (Render)

Requisitos de la base PostgreSQL 16 donde se desplegará Rehab Elite (TEC-07, sprint 6).

## Extensiones

Las migraciones de Flyway crean estas extensiones con `CREATE EXTENSION IF NOT EXISTS`. El usuario de la aplicación debe poder crearlas, o deben existir antes de la primera migración.

| Extensión | Migración | Para qué |
|---|---|---|
| `pg_trgm` | V1 | Índice trigram de la búsqueda de pacientes por nombre (`ix_patients_full_name_trgm`). |
| `unaccent` | V1 | `normalize_text(text)`: búsqueda sin tildes. |
| `btree_gist` | V8 (MEJ-04) | Restricción `ex_staff_schedule_slots_overlap`: dos franjas del mismo día de un fisioterapeuta no se superponen. Combina la igualdad de columnas simples con la superposición de rangos de horas en un índice GiST. |

**Pendiente de verificar en el sprint 6:** que la base de Render permita `btree_gist`. Es una extensión de confianza (*trusted*) desde PostgreSQL 13, así que el dueño de la base puede crearla sin ser superusuario, y Render la incluye entre sus extensiones disponibles. Si no se pudiera crear, V8 falla y la aplicación no arranca: habría que crearla desde el panel o la consola de Render antes del despliegue.

Comprobación rápida, conectado a la base de Render:

```sql
SELECT name, default_version, installed_version
FROM pg_available_extensions
WHERE name IN ('pg_trgm', 'unaccent', 'btree_gist');
```

Las tres deben aparecer; después de la primera migración, `installed_version` no debe ser nulo.

## Jornada de la clínica

V8 fija la jornada más amplia de los horarios en 07:00 a 20:00 (`ck_staff_schedule_slots_clinic_hours`). Las propiedades `app.clinic.opening-time` y `app.clinic.closing-time` (`application.yml`) pueden acotarla, nunca ampliarla: con valores fuera de ese marco la aplicación no arranca. Ampliarla requiere una migración nueva.
