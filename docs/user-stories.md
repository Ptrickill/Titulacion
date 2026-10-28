# Historias de usuario — Rehab Elite

Formato: Como <rol>, quiero <acción>, para <propósito>. Criterios en Dado/Cuando/Entonces. Puntos en Fibonacci. Anexo 18 del documento.

## PB-01 Autenticación y control de acceso (Sprint 3)
- HU-01 (3 pts) Como usuario registrado, quiero iniciar sesión con correo y contraseña, para acceder a las funciones de mi rol.
  - Dado credenciales válidas, cuando ingreso, entonces el sistema me dirige al panel de mi rol.
  - Dado credenciales inválidas, cuando ingreso, entonces el sistema muestra un mensaje genérico sin indicar qué dato falló.
  - Dado 5 intentos fallidos consecutivos, cuando intento de nuevo, entonces la cuenta se bloquea 15 minutos.
- HU-02 (5 pts) Como administrador, quiero crear usuarios y asignarles un rol, para controlar quién accede al sistema.
  - Dado un correo ya registrado, cuando creo el usuario, entonces el sistema rechaza el registro.
  - Dado un usuario creado, entonces su contraseña se almacena cifrada con BCrypt y su estado inicial es Activo.
  - Dado el rol Paciente, cuando creo la cuenta, entonces el sistema exige vincularla a un paciente registrado.
- HU-03 (2 pts) Como administrador, quiero activar o desactivar cuentas, para retirar el acceso del personal que deja la clínica.
  - Dado una cuenta inactiva, cuando el usuario inicia sesión, entonces el sistema rechaza el acceso.
  - Cada cambio de estado registra usuario, fecha y hora en la auditoría.
- HU-04 (3 pts) Como usuario autenticado, quiero que el sistema bloquee las funciones ajenas a mi rol, para proteger la información clínica.
  - Dado una petición a un recurso no autorizado, entonces la API responde 403 y registra el intento en la auditoría.
  - Dado una ruta no autorizada en el frontend, entonces el sistema redirige al panel del rol.

## PB-02 Gestión de pacientes (Sprint 3)
- HU-05 (5 pts) Como administrador, quiero registrar un paciente con sus datos de admisión y personales, para crear su registro único.
  - Dado una cédula con dígito verificador inválido, entonces el sistema rechaza el registro.
  - Dado una cédula existente, entonces el sistema muestra el aviso y el acceso al registro existente.
  - Dado un registro válido, entonces el sistema genera el número de historia clínica desde la cédula y calcula la edad desde la fecha de nacimiento.
  - Autoidentificación étnica y religión son opcionales.
- HU-06 (3 pts) Como administrador, quiero buscar pacientes por cédula o nombre y actualizar sus datos, para mantener la información vigente.
  - La búsqueda acepta coincidencias parciales y sin tildes.
  - Cada actualización registra usuario y fecha.
  - El administrador no visualiza datos clínicos.

## PB-03 Historial clínico (Sprint 4)
- HU-07 (8 pts) Como fisioterapeuta, quiero crear el historial clínico con hábitos, antecedentes, diagnóstico CIE-10, evaluación inicial del dolor y plan terapéutico, para documentar la valoración inicial.
  - Dado un paciente sin historial, cuando lo creo, entonces el historial queda asociado al paciente y el tratamiento queda con estado Activo.
  - El diagnóstico se selecciona desde el catálogo CIE-10.
  - La EVA inicial acepta valores de 0 a 10 y las sesiones planificadas son mayores a 0.
- HU-08 (3 pts) Como fisioterapeuta, quiero asignar el fisioterapeuta responsable y actualizar el estado del tratamiento (Activo, Finalizado, Abandonado), para mantener la trazabilidad.
  - Cada tratamiento mantiene un único responsable vigente y conserva el histórico de cambios.
  - Cada cambio de estado registra usuario, fecha y motivo.
- HU-09 (3 pts) Como fisioterapeuta, quiero consultar el historial antes de la sesión, para preparar la atención.
  - La consulta responde en menos de 2 segundos.
  - Solo los fisioterapeutas acceden; cada consulta queda en la auditoría.
- HU-18 (5 pts, técnica) Como dirección operativa, quiero que el sistema registre quién crea, modifica o consulta información clínica, para cumplir la LOPDP.
  - Cada operación sobre datos clínicos registra usuario, acción, entidad, fecha y hora.
  - Ningún rol modifica ni elimina registros de auditoría.

## PB-04 Registro de sesiones (Sprint 5)
- HU-10 (8 pts) Como fisioterapeuta, quiero registrar una sesión con fecha, tipo, actividades realizadas, observaciones clínicas, indicaciones para el paciente, EVA, estado de la sesión y evolución, para documentar el avance.
  - Dado un campo obligatorio vacío o una EVA fuera de 0–10, entonces el sistema rechaza el registro.
  - El sistema asigna el correlativo y toma como responsable al usuario autenticado, sin permitir editarlo.
  - Una sesión registrada no se elimina; su corrección conserva la versión anterior.

## PB-05 Línea de tiempo (Sprint 5)
- HU-11 (5 pts) Como fisioterapeuta, quiero ver las sesiones en orden cronológico con su EVA y responsable, para comprender la evolución cuando otro profesional atendió al paciente.
  - La vista muestra la curva de EVA por sesión y permite filtrar por rango de fechas.

## PB-06 Portal del paciente (Sprint 6)
- HU-12 (5 pts) Como paciente, quiero ver mis sesiones realizadas frente a las planificadas y la evolución de mi dolor, para conocer el avance de mi tratamiento.
  - Dado un intento de acceso a datos de otro paciente, entonces la API responde 403.
  - El portal no ofrece opciones de edición.
- HU-13 (3 pts) Como paciente, quiero leer las indicaciones del fisioterapeuta y el listado de mis sesiones, para seguir mi tratamiento.
  - El portal muestra las indicaciones para el paciente y excluye las observaciones clínicas internas.

## PB-07 Reportes (Sprint 6)
- HU-14 (5 pts) Como dirección operativa, quiero ver los tratamientos activos, finalizados y abandonados y las sesiones registradas, para controlar la operación.
  - Los indicadores se calculan desde el estado del tratamiento (HU-08); la vista es de solo lectura.
- HU-15 (5 pts) Como dirección operativa, quiero filtrar por fisioterapeuta, estado y rango de fechas y exportar el reporte a PDF, para revisar la carga de cada profesional.
  - Dado un filtro aplicado, entonces el reporte y la exportación reflejan el mismo resultado.

## PB-08 Resumen asistido por IA (Sprint 7)
- HU-16 (8 pts) Como fisioterapeuta, quiero generar un resumen de las sesiones registradas, para revisar la evolución antes de atender.
  - El sistema envía al servicio de IA solo información registrada y sin nombre ni cédula.
  - El resumen queda en estado Pendiente de validación.
  - Dado un fallo del servicio de IA, entonces el sistema muestra un aviso y el resto de funciones sigue operativo.
- HU-17 (3 pts) Como fisioterapeuta, quiero validar, editar o descartar el resumen, para conservar mi criterio profesional.
  - Solo el resumen validado se anexa al historial con usuario y fecha; el descartado queda únicamente en la auditoría.
