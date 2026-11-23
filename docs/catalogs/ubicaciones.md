# Catálogo de ubicaciones (provincia, cantón y parroquia)

Catálogo de la dirección estructurada del paciente (MEJ-03, Jira RHE-86). Lo usa el formulario de registro y edición de pacientes y lo sirve `GET /api/catalogs/locations`.

## Fuente

| Dato | Valor |
|---|---|
| Publicador | Instituto Nacional de Estadística y Censos (INEC), Dirección de Cartografía Estadística y Operaciones de Campo (DICA) |
| Documento | Clasificador Geográfico Estadístico 2025, "Esquema de codificación de la división político administrativa del país" |
| Versión | CGE 2025 |
| Fecha de corte | 31 de diciembre de 2024 ("Actualizado al 31 de diciembre de 2024") |
| Archivo usado | [cge2025.xls](https://aplicaciones2.ecuadorencifras.gob.ec/SIN/descargas/cge2025.xls) (también publicado en [PDF](https://aplicaciones2.ecuadorencifras.gob.ec/SIN/descargas/cge2025.pdf)) |
| SHA-256 del `.xls` | `205b33e777a903095fd838c614659a3a3ff737beac03be8a36c3531cd272cc7b` |
| Descargado | 22/11/2026 |

El archivo del INEC no se guarda en el repositorio. El catálogo de la aplicación es `backend/src/main/resources/catalogs/ec-locations-cge2025.json`, generado con:

```powershell
# Python 3.10+ con xlrd 2.0.1
python tools/catalogs/inec_cge_to_json.py cge2025.xls backend/src/main/resources/catalogs/ec-locations-cge2025.json
```

## Contenido

Se guardan los códigos y los nombres oficiales tal como los publica el INEC (en mayúsculas y con tildes). La interfaz y la dirección compuesta los muestran con mayúscula inicial, con la misma regla de los nombres de los pacientes ("SAN MIGUEL DE LOS BANCOS" → "San Miguel de los Bancos").

| Nivel | Código | Cantidad |
|---|---|---|
| Provincia | 2 dígitos (`17`) | 24 |
| Cantón | 4 dígitos: provincia + cantón (`1701`) | 222 |
| Parroquia | 6 dígitos: cantón + parroquia (`170156`) | 1255: 268 urbanas, 824 rurales y 163 cabeceras cantonales |

Los 222 cantones incluyen Sevilla Don Bosco (Morona Santiago, `1413`), creado después del conteo de 221 cantones que suele citarse.

## Reglas de conversión

1. **Jurisdicciones con asterisco:** el INEC marca con un asterisco (`*`) las jurisdicciones que cambiaron: cantones que pasaron a otra provincia, parroquias rurales que pasaron a ser urbanas o a otro cantón, y parroquias suprimidas. No se incluyen: son 133 filas de parroquia y 9 de cantón. Por ejemplo, Chordeleg aparece como parroquia de Gualaceo (`010351`), pero hoy es el cantón `0111`. El doble asterisco (`**`) es solo una llamada a una nota y se quita del nombre.
2. **Cabecera cantonal (código 50):** se incluye solo en los cantones sin parroquias urbanas (163), con el nombre "… (CABECERA CANTONAL)". En los 59 cantones con parroquias urbanas (Quito, Guayaquil, Cuenca…), el código 50 agrupa esas parroquias y elegirlo sería ambiguo: se elige la parroquia urbana.
3. **Zonas en estudio (código 90):** son ocho áreas sin provincia asignada (Las Golondrinas, Manga del Cura, El Piedrero…). No son provincia ni cantón y no se incluyen.
4. **Filas repetidas:** el Distrito Metropolitano de Quito lista sus parroquias dos veces (por parroquia y por zona metropolitana). Se conserva la primera aparición de cada código.
5. **Nombres partidos:** los nombres que el libro parte en dos filas se unen ("SAN GERARDO (CAB. EN SAN GERARDO DE HUAHUALPATA)").

## Uso

- El backend valida que la parroquia pertenezca al cantón y el cantón a la provincia (`LocationCatalog.isConsistent`). La base de datos comprueba además que los códigos estén anidados (`ck_patients_address_codes`).
- El frontend pide el catálogo una vez por sesión (caché con `shareReplay`); la respuesta permite guardarlo un día (`Cache-Control: max-age=86400, private`).
- La dirección nunca se envía a un servicio externo.

## Actualización

Con una edición nueva del clasificador: descargar el `.xls`, regenerar el JSON con un nombre que indique la edición, actualizar `ResourceLocationCatalog.RESOURCE`, los conteos de `ResourceLocationCatalogTests` y esta página. Si una parroquia de un paciente desaparece del catálogo, su dirección deja de validarse al editarlo y hay que corregirla.
