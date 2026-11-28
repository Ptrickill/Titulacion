# Cifrado de los datos clínicos

Cómo se cifran los textos clínicos en la base (MEJ-06, Jira RHE-90), cómo se configura la clave y cómo se rota.

## Qué se cifra

Los campos de texto libre con información de salud se guardan cifrados con **AES-256-GCM**. Cada valor lleva un vector de inicialización aleatorio de 12 bytes: el mismo texto nunca produce dos veces el mismo valor en la base.

| Tabla | Columnas |
|---|---|
| `clinical_records` | `habits`, `physical_activity`, `allergies`, `current_medication`, `personal_history`, `surgical_history`, `family_history` |
| `treatments` y `treatments_aud` | `chief_complaint`, `current_illness`, `complementary_tests`, `goal` |
| `treatment_status_changes` | `reason` |
| `therapy_sessions` y `therapy_sessions_aud` | `activities_performed`, `clinical_observations`, `patient_instructions` |
| `ai_summaries` | `generated_content`, `final_content` |

Las tablas `_aud` de Hibernate Envers guardan las versiones anteriores, y también están cifradas.

**No se cifran:**

- el código CIE-10 del diagnóstico, porque lo usan los reportes y su descripción es un dato del catálogo;
- el médico remitente, el tipo, la zona y la frecuencia del dolor, la EVA y la gravedad;
- los estados y las fechas.

En ninguna tabla existe una descripción libre del diagnóstico.

Cada valor cifrado se guarda así:

```
enc:v1:<id de la clave>:<Base64 del IV, el texto cifrado y la etiqueta>
```

El id de la clave son los primeros 8 dígitos hexadecimales del SHA-256 de la clave. Con él, la aplicación sabe qué clave descifra cada valor.

Ninguna búsqueda, filtro ni orden usa estas columnas. Los reportes, la línea de tiempo, el portal y la búsqueda de pacientes funcionan igual que antes. La única condición sobre ellas es `patient_instructions IS NOT NULL`, y un valor nulo sigue siendo nulo.

## La clave: `CLINICAL_DATA_KEY`

- La clave son **32 bytes aleatorios en Base64**.
- **Sin ella, o con una clave inválida, la aplicación no arranca.**
- Se define solo como variable de entorno: en `.env` en desarrollo y en **Environment** del servicio web en Render. Nunca se escribe en el repositorio ni en la imagen Docker.
- Cada entorno usa su propia clave: desarrollo, pruebas y producción.

Para generarla con PowerShell:

```powershell
$b = New-Object byte[] 32; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); [Convert]::ToBase64String($b)
```

Las pruebas automáticas usan una clave fija de prueba, en `backend/src/test/resources/application-test.yml`. Esa clave no sirve para ningún otro entorno.

## Si se pierde la clave, los datos son ilegibles

Sin la clave **no hay forma de recuperar** los textos clínicos cifrados. La aplicación tampoco podría leer los historiales, los tratamientos ni las sesiones que los contienen. Por eso:

- **Guarde una copia de la clave fuera del servidor**: un gestor de contraseñas de la clínica o una bóveda de secretos, con acceso solo para los responsables. No la guarde en el mismo lugar que los respaldos de la base: quien tenga ambos podría leer los datos.
- Cuando se respalda la base, se debe poder recuperar también la clave vigente en esa fecha, y las anteriores si existían.
- Registre en el inventario de la clínica quién tiene acceso a la clave.

## Rotación de la clave

La aplicación cifra siempre con `CLINICAL_DATA_KEY` y descifra con cualquiera de las claves configuradas. Las anteriores van en `CLINICAL_DATA_PREVIOUS_KEYS`, separadas por comas. Así, la clave se puede cambiar sin detener el servicio por más de un reinicio.

1. Genere una clave nueva con el comando anterior y guarde su copia fuera del servidor.
2. En las variables del servicio:
   - `CLINICAL_DATA_KEY` = la clave nueva;
   - `CLINICAL_DATA_PREVIOUS_KEYS` = la clave anterior (y las previas, si las hubiera).
3. Reinicie el servicio:
   - los valores nuevos y los que se editen desde entonces se cifran con la clave nueva;
   - los antiguos se siguen leyendo con la anterior.
4. **No retire una clave anterior mientras queden valores cifrados con ella.** Para saber cuántos quedan, calcule su id y cuente los valores con ese prefijo en cada tabla de la lista. Por ejemplo:

   ```powershell
   $k = [Convert]::FromBase64String('<clave anterior>')
   -join ([Security.Cryptography.SHA256]::Create().ComputeHash($k)[0..3] | ForEach-Object { $_.ToString('x2') })
   ```

   ```sql
   SELECT count(*) FROM clinical_records WHERE habits LIKE 'enc:v1:<id>:%';
   ```

**Limitación conocida:** todavía no existe un proceso que vuelva a cifrar con la clave nueva los valores que ya estaban cifrados. Un valor cambia de clave solo cuando se edita. Mientras tanto, la clave anterior debe quedarse en `CLINICAL_DATA_PREVIOUS_KEYS`.

## Migración de los datos existentes (V11)

La migración `V11` es una migración Java de Flyway (`EncryptClinicalTextMigration`), porque necesita la clave. Al desplegar MEJ-06 en una base con datos, cifra por lotes de 500 filas todos los valores en texto plano de las columnas de la lista, también en las tablas `_aud`. Se puede repetir sin riesgo: salta los valores que ya están cifrados.

Antes de desplegar MEJ-06 en una base con datos reales:

- respalde la base;
- defina `CLINICAL_DATA_KEY`;
- guarde su copia fuera del servidor.

En desarrollo, el seed del perfil `dev` inserta textos sintéticos en texto plano. `DevSeedClinicalTextInitializer` los cifra al arrancar, antes de que nada los lea.

## Lectura estricta

Leer un valor que no está cifrado es un error: la aplicación no muestra texto plano por accidente. Si algo inserta texto clínico directamente por SQL, debe insertarlo ya cifrado. Así lo hacen las ayudas de prueba `TestTreatments` y `TestSessions`. Si no lo hace, la lectura de ese registro falla. En desarrollo, reiniciar con el perfil `dev` cifra los valores en texto plano; en producción, nunca se inserta texto clínico por SQL.
