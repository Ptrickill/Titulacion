-- =====================================================================
-- Synthetic development data (TEC-06). Loaded ONLY with the "dev" profile:
-- application-dev.yml adds classpath:db/dev-seed to spring.flyway.locations.
-- Idempotent: every statement can run again without duplicating rows.
--
-- Every name, national ID, phone, email and address here is fictitious.
-- National IDs follow the pattern <province>5 0000 <n> + modulo 10 check
-- digit, so they are valid for NationalId but obviously synthetic
-- (DevSeedDataTests checks each one).
--
-- Passwords: accounts are created with a marker that is not a BCrypt hash,
-- so they cannot log in until DevSeedPasswordInitializer (@Profile("dev"))
-- stores the BCrypt hash of DEV_SEED_PASSWORD at startup. No password or
-- hash is ever written in this file.
--
-- No audit entries: this is bootstrap data, not an action of a user.
-- MEJ-04 adds the physiotherapists' schedules and a few synthetic clinical
-- records and treatments, so each physiotherapist has a different load.
-- =====================================================================

-- ---------------------------------------------------------------------
-- Accounts: every seed email ends with @dev-seed.rehabelite.test
-- ---------------------------------------------------------------------
-- MEJ-01: the seed accounts are already activated (verified email); their password comes from DEV_SEED_PASSWORD
INSERT INTO users (email, password_hash, full_name, role, email_verified_at)
VALUES ('fisio.andrade@dev-seed.rehabelite.test', 'dev-seed:password-set-at-startup', 'Valeria Andrade Mora', 'PHYSIOTHERAPIST', now()),
       ('fisio.cevallos@dev-seed.rehabelite.test', 'dev-seed:password-set-at-startup', 'Martín Cevallos Ruiz', 'PHYSIOTHERAPIST', now()),
       ('fisio.paredes@dev-seed.rehabelite.test', 'dev-seed:password-set-at-startup', 'Andrés Paredes Salinas', 'PHYSIOTHERAPIST', now()),
       ('direccion@dev-seed.rehabelite.test', 'dev-seed:password-set-at-startup', 'Paula Herrera Vásconez', 'OPERATIONS_DIRECTOR', now()),
       -- MEJ-05: the reception (role ADMINISTRATOR) registers the patients; the operations director manages the staff
       ('recepcion@dev-seed.rehabelite.test', 'dev-seed:password-set-at-startup', 'Gabriela Salazar Ponce', 'ADMINISTRATOR', now()),
       ('paciente.ortiz@dev-seed.rehabelite.test', 'dev-seed:password-set-at-startup', 'Rosa Elena Ortiz Vega', 'PATIENT', now()),
       ('paciente.quishpe@dev-seed.rehabelite.test', 'dev-seed:password-set-at-startup', 'Luis Alberto Quishpe Tipán', 'PATIENT', now()),
       ('paciente.zambrano@dev-seed.rehabelite.test', 'dev-seed:password-set-at-startup', 'Daniela Sofía Zambrano Loor', 'PATIENT', now())
ON CONFLICT (email) DO NOTHING;

-- ---------------------------------------------------------------------
-- Staff records (MEJ-02): complete personal data of each staff account of the seed. A database migrated to V7
-- before this script already has their records with data to complete: they are filled in, once
-- ---------------------------------------------------------------------
INSERT INTO staff_records (user_id, national_id, first_name, last_name, birth_date, sex, phone, email,
                           province_code, canton_code, parish_code, main_street, street_number, cross_street,
                           address_reference)
SELECT u.id, staff.national_id, staff.first_name, staff.last_name, staff.birth_date, staff.sex, staff.phone, u.email,
       staff.province_code, staff.canton_code, staff.parish_code, staff.main_street, staff.street_number,
       staff.cross_street, staff.address_reference
FROM (VALUES ('fisio.andrade@dev-seed.rehabelite.test', '1702000165', 'Valeria', 'Andrade Mora', DATE '1990-03-14',
              'FEMALE', '0990000201', '17', '1701', '170156', 'Calle de Prueba', 'N5-10', 'Av. Ficticia',
              'Edificio de prueba, departamento 2'),
             ('fisio.cevallos@dev-seed.rehabelite.test', '1702000249', 'Martín', 'Cevallos Ruiz', DATE '1987-08-02',
              'MALE', '0990000202', '17', '1701', '170112', 'Av. Imaginaria', '210', 'Calle Ejemplo',
              'Casa esquinera de prueba'),
             ('fisio.paredes@dev-seed.rehabelite.test', '1702000405', 'Andrés', 'Paredes Salinas', DATE '1993-01-19',
              'MALE', '0990000203', '17', '1701', '170157', 'Calle Ficticia', 'S4-12', 'Pasaje Ejemplo',
              'Junto a la farmacia de prueba'),
             ('direccion@dev-seed.rehabelite.test', '1702000322', 'Paula', 'Herrera Vásconez', DATE '1979-11-25',
              'FEMALE', '022345201', '17', '1701', '170108', 'Pasaje Ficticio', 'E3-45', 'Calle de Prueba',
              'Frente a la plaza de prueba'),
             ('recepcion@dev-seed.rehabelite.test', '1702000504', 'Gabriela', 'Salazar Ponce', DATE '1995-05-30',
              'FEMALE', '0990000204', '17', '1701', '170156', 'Calle Imaginaria', 'N8-20', 'Av. de Prueba',
              'Junto a la panadería de prueba'))
         AS staff (email, national_id, first_name, last_name, birth_date, sex, phone, province_code, canton_code,
                   parish_code, main_street, street_number, cross_street, address_reference)
         JOIN users u ON u.email = staff.email
ON CONFLICT (user_id) DO UPDATE
    SET national_id = EXCLUDED.national_id, first_name = EXCLUDED.first_name, last_name = EXCLUDED.last_name,
        birth_date = EXCLUDED.birth_date, sex = EXCLUDED.sex, phone = EXCLUDED.phone,
        province_code = EXCLUDED.province_code, canton_code = EXCLUDED.canton_code,
        parish_code = EXCLUDED.parish_code, main_street = EXCLUDED.main_street,
        street_number = EXCLUDED.street_number, cross_street = EXCLUDED.cross_street,
        address_reference = EXCLUDED.address_reference
    WHERE staff_records.national_id IS NULL;

-- ---------------------------------------------------------------------
-- Patients (personal data only); medical record number = national ID
-- ---------------------------------------------------------------------
-- MEJ-03: structured address (parish, canton and province codes of INEC's catalog, and the streets), phones that
-- follow the mobile or landline rule, and "¿Tiene seguro de salud?" as yes/no. All the data is fictitious
INSERT INTO patients (national_id, medical_record_number, first_name, last_name, birth_date, sex, phone, email,
                      province_code, canton_code, parish_code, main_street, street_number, cross_street,
                      address_reference, occupation, education_level, has_health_insurance,
                      ethnic_self_identification, religion, admission_date)
VALUES ('1750000018', '1750000018', 'Rosa Elena', 'Ortiz Vega', DATE '1968-04-12', 'FEMALE', '0990000101',
        'rosa.ortiz@example.com', '17', '1701', '170112', 'Calle Ficticia', 'N10-01', 'Av. Ejemplo',
        'Edificio de prueba, piso 3', 'Contadora', 'Superior', true, 'Mestizo', 'Católica', DATE '2026-09-01'),
       ('1750000026', '1750000026', 'Luis Alberto', 'Quishpe Tipán', DATE '1985-11-03', 'MALE', '0990000102',
        'luis.quishpe@example.com', '17', '1701', '170108', 'Pasaje Ejemplo', 'S2-15', 'Calle Ficticia',
        'Junto a la tienda de prueba', 'Chofer profesional', 'Secundaria', true, 'Indígena', NULL, DATE '2026-09-08'),
       ('1750000034', '1750000034', 'Daniela Sofía', 'Zambrano Loor', DATE '2001-02-27', 'FEMALE', '0990000103',
        'daniela.zambrano@example.com', '17', '1701', '170157', 'Av. Imaginaria', '345', 'Calle de Prueba',
        'Casa blanca de dos pisos', 'Estudiante universitaria', 'Superior', true, 'Montubio', NULL, DATE '2026-09-15'),
       ('1750000042', '1750000042', 'Jorge Enrique', 'Salazar Paredes', DATE '1957-07-19', 'MALE', '022345104',
        'jorge.salazar@example.com', '17', '1701', '170115', 'Calle de Prueba', 'Oe4-22', 'Av. Ficticia',
        'Frente al parque de prueba', 'Jubilado', 'Secundaria', true, NULL, 'Católica', DATE '2026-09-22'),
       ('1750000059', '1750000059', 'Gabriela Alejandra', 'Benítez Cruz', DATE '1993-05-08', 'FEMALE', '0990000105',
        'gabriela.benitez@example.com', '17', '1701', '170156', 'Conjunto Modelo', 'Casa 7', 'Calle Ejemplo',
        'Conjunto de prueba, garita azul', 'Diseñadora gráfica', 'Superior', false, 'Mestizo', NULL, DATE '2026-10-01'),
       ('0950000067', '0950000067', 'Carlos Andrés', 'Villacís Franco', DATE '1979-09-30', 'MALE', '042345106',
        'carlos.villacis@example.com', '09', '0901', '090114', 'Ciudadela Ejemplo', 'Mz 3 V 12', 'Calle Ficticia',
        'Cerca del mercado de prueba', 'Comerciante', 'Secundaria', true, 'Mestizo', 'Evangélica', DATE '2026-10-02'),
       ('0950000075', '0950000075', 'María José', 'Aguirre Bajaña', DATE '2010-12-14', 'FEMALE', '0990000107',
        'familia.aguirre@example.com', '09', '0901', '090112', 'Av. Ficticia', NULL, 'Calle 10',
        'Esquina de prueba', 'Estudiante', 'Primaria', true, NULL, NULL, DATE '2026-10-05'),
       ('0150000081', '0150000081', 'Pedro Pablo', 'Cordero Ochoa', DATE '1972-03-21', 'MALE', '072345108',
        'pedro.cordero@example.com', '01', '0101', '010103', 'Calle Larga Ficticia', '8-40', 'Calle Ejemplo',
        'Casa con portón verde', 'Ingeniero civil', 'Posgrado', true, 'Mestizo', 'Católica', DATE '2026-10-06'),
       ('1350000095', '1350000095', 'Ana Lucía', 'Mendoza Cedeño', DATE '1988-08-02', 'FEMALE', '0990000109',
        'ana.mendoza@example.com', '13', '1301', '130101', 'Calle 5', NULL, 'Av. de Prueba',
        'Barrio de prueba', 'Enfermera', 'Superior', true, 'Montubio', 'Católica', DATE '2026-10-09'),
       ('1850000108', '1850000108', 'Santiago Javier', 'Lozada Núñez', DATE '1999-06-11', 'MALE', '0990000110',
        'santiago.lozada@example.com', '18', '1801', '180102', 'Av. de Prueba', '12-34', 'Calle Ficticia',
        'Junto al coliseo de prueba', 'Deportista', 'Secundaria', false, 'Mestizo', NULL, DATE '2026-10-13'),
       ('1050000114', '1050000114', 'Inés Maribel', 'Farinango Cachimuel', DATE '1964-01-25', 'FEMALE', '0990000111',
        'ines.farinango@example.com', '10', '1004', '100401', 'Comunidad Ficticia', NULL, 'Camino de prueba',
        'Casa junto a la cancha comunal', 'Artesana', 'Primaria', true, 'Indígena', 'Católica', DATE '2026-10-16'),
       ('0650000128', '0650000128', 'Fernando Xavier', 'Guamán Yépez', DATE '1981-10-17', 'MALE', '032345112',
        'fernando.guaman@example.com', '06', '0601', '060101', 'Calle Modelo', NULL, 'Av. Ejemplo',
        'Cerca de la escuela de prueba', 'Docente', 'Superior', true, 'Mestizo', NULL, DATE '2026-10-20'),
       ('1150000139', '1150000139', 'Patricia Elizabeth', 'Jaramillo Ruiz', DATE '1990-04-04', 'FEMALE', '0990000113',
        'patricia.jaramillo@example.com', '11', '1101', '110101', 'Calle Ficticia', '15-08', 'Calle Ejemplo',
        'Edificio de prueba', 'Abogada', 'Posgrado', true, NULL, 'Católica', DATE '2026-10-23'),
       ('2350000143', '2350000143', 'Kevin Alexander', 'Chalá Quiñónez', DATE '2004-07-29', 'MALE', '0990000114',
        'kevin.chala@example.com', '23', '2301', '230103', 'Cooperativa Ejemplo', NULL, 'Calle de Prueba',
        'Lote de prueba 5', 'Mecánico', 'Secundaria', false, 'Afroecuatoriano', NULL, DATE '2026-10-26'),
       ('3050000151', '3050000151', 'Elena Beatriz', 'Proaño Sotomayor', DATE '1976-12-01', 'FEMALE', '0990000115',
        'elena.proano@example.com', '17', '1701', '170123', 'Av. Imaginaria', '900', 'Calle Ficticia',
        'Edificio de prueba, oficina 2', 'Traductora', 'Superior', true, 'Blanco', NULL, DATE '2026-10-28')
ON CONFLICT (national_id) DO NOTHING;

-- ---------------------------------------------------------------------
-- Portal accounts: link each PATIENT account to its patient, once
-- ---------------------------------------------------------------------
UPDATE patients p
SET portal_user_id = u.id
FROM (VALUES ('paciente.ortiz@dev-seed.rehabelite.test', '1750000018'),
             ('paciente.quishpe@dev-seed.rehabelite.test', '1750000026'),
             ('paciente.zambrano@dev-seed.rehabelite.test', '1750000034')) AS link (email, national_id)
         JOIN users u ON u.email = link.email
WHERE p.national_id = link.national_id
  AND p.portal_user_id IS NULL
  AND NOT EXISTS (SELECT 1 FROM patients other WHERE other.portal_user_id = u.id);

-- ---------------------------------------------------------------------
-- Weekly schedules (MEJ-04), Monday (1) to Saturday (6): Andrade works every weekday morning and afternoon, Cevallos
-- three long days and Saturday morning, and Paredes only two afternoons (a partial schedule, so the availability
-- filter shows a difference). Only for a physiotherapist that has no slots yet
-- ---------------------------------------------------------------------
INSERT INTO staff_schedule_slots (staff_record_id, day_of_week, start_time, end_time)
SELECT s.id, slot.day_of_week, slot.start_time, slot.end_time
FROM (VALUES ('fisio.andrade@dev-seed.rehabelite.test', 1, TIME '08:00', TIME '13:00'),
             ('fisio.andrade@dev-seed.rehabelite.test', 1, TIME '14:00', TIME '17:00'),
             ('fisio.andrade@dev-seed.rehabelite.test', 2, TIME '08:00', TIME '13:00'),
             ('fisio.andrade@dev-seed.rehabelite.test', 2, TIME '14:00', TIME '17:00'),
             ('fisio.andrade@dev-seed.rehabelite.test', 3, TIME '08:00', TIME '13:00'),
             ('fisio.andrade@dev-seed.rehabelite.test', 3, TIME '14:00', TIME '17:00'),
             ('fisio.andrade@dev-seed.rehabelite.test', 4, TIME '08:00', TIME '13:00'),
             ('fisio.andrade@dev-seed.rehabelite.test', 4, TIME '14:00', TIME '17:00'),
             ('fisio.andrade@dev-seed.rehabelite.test', 5, TIME '08:00', TIME '13:00'),
             ('fisio.andrade@dev-seed.rehabelite.test', 5, TIME '14:00', TIME '17:00'),
             ('fisio.cevallos@dev-seed.rehabelite.test', 1, TIME '07:00', TIME '15:00'),
             ('fisio.cevallos@dev-seed.rehabelite.test', 3, TIME '07:00', TIME '15:00'),
             ('fisio.cevallos@dev-seed.rehabelite.test', 5, TIME '07:00', TIME '15:00'),
             ('fisio.cevallos@dev-seed.rehabelite.test', 6, TIME '08:00', TIME '12:00'),
             ('fisio.paredes@dev-seed.rehabelite.test', 2, TIME '13:00', TIME '19:00'),
             ('fisio.paredes@dev-seed.rehabelite.test', 4, TIME '13:00', TIME '19:00'))
         AS slot (email, day_of_week, start_time, end_time)
         JOIN users u ON u.email = slot.email
         JOIN staff_records s ON s.user_id = u.id
WHERE NOT EXISTS (SELECT 1 FROM staff_schedule_slots existing WHERE existing.staff_record_id = s.id);

-- ---------------------------------------------------------------------
-- Clinical records and treatments (MEJ-04): eight patients, so the loads differ. Andrade is the responsible of four
-- active treatments, Cevallos of two active ones and a completed one, Paredes of one. All the clinical text is
-- generic and synthetic. A record is created only for a patient without one, a treatment only for a record without
-- treatments, and a responsible only for a treatment without one, so the script never adds a second active treatment
-- ---------------------------------------------------------------------
INSERT INTO clinical_records (patient_id, habits, physical_activity, allergies, current_medication, personal_history,
                              surgical_history, family_history, created_by)
SELECT p.id, 'No fuma. Consumo ocasional de café.', 'Camina 30 minutos tres veces por semana.', 'Ninguna conocida.',
       'Ninguna.', 'Sin antecedentes relevantes (dato sintético).', 'Ninguno.', 'Ninguno.', u.id
FROM (VALUES ('1750000018', 'fisio.andrade@dev-seed.rehabelite.test'),
             ('1750000042', 'fisio.andrade@dev-seed.rehabelite.test'),
             ('0950000067', 'fisio.andrade@dev-seed.rehabelite.test'),
             ('1350000095', 'fisio.andrade@dev-seed.rehabelite.test'),
             ('1750000026', 'fisio.cevallos@dev-seed.rehabelite.test'),
             ('1750000059', 'fisio.cevallos@dev-seed.rehabelite.test'),
             ('0150000081', 'fisio.cevallos@dev-seed.rehabelite.test'),
             ('1750000034', 'fisio.paredes@dev-seed.rehabelite.test'))
         AS rec (national_id, email)
         JOIN patients p ON p.national_id = rec.national_id
         JOIN users u ON u.email = rec.email
ON CONFLICT (patient_id) DO NOTHING;

INSERT INTO treatments (clinical_record_id, diagnosis_code, chief_complaint, current_illness, referring_physician,
                        complementary_tests, severity, pain_type, pain_location, initial_pain_score, pain_frequency,
                        session_frequency, goal, planned_sessions, status, start_date, created_by)
SELECT r.id, t.diagnosis_code, t.chief_complaint, 'Cuadro sintético de varias semanas de evolución.',
       'Médico de prueba', 'Sin exámenes (dato sintético).', t.severity, t.pain_type, t.pain_location, t.pain_score,
       'Intermitente', '3 por semana', 'Reducir el dolor y recuperar la funcionalidad.', t.planned_sessions, t.status,
       t.start_date, u.id
FROM (VALUES ('1750000018', 'fisio.andrade@dev-seed.rehabelite.test', 'M54.5', 'Dolor lumbar', 'MODERATE', 'Punzante',
              'Zona lumbar', 7, 12, 'ACTIVE', DATE '2026-09-08'),
             ('1750000042', 'fisio.andrade@dev-seed.rehabelite.test', 'M17.9', 'Dolor de rodilla', 'MODERATE',
              'Sordo', 'Rodilla derecha', 6, 10, 'ACTIVE', DATE '2026-09-29'),
             ('0950000067', 'fisio.andrade@dev-seed.rehabelite.test', 'M75.1', 'Dolor de hombro', 'SEVERE',
              'Punzante', 'Hombro izquierdo', 8, 15, 'ACTIVE', DATE '2026-10-12'),
             ('1350000095', 'fisio.andrade@dev-seed.rehabelite.test', 'M54.2', 'Dolor cervical', 'MILD', 'Tensional',
              'Cuello', 5, 8, 'ACTIVE', DATE '2026-10-20'),
             ('1750000026', 'fisio.cevallos@dev-seed.rehabelite.test', 'M51.1', 'Dolor lumbar irradiado', 'SEVERE',
              'Quemante', 'Zona lumbar', 8, 15, 'ACTIVE', DATE '2026-09-15'),
             ('1750000059', 'fisio.cevallos@dev-seed.rehabelite.test', 'M77.1', 'Dolor de codo', 'MILD', 'Sordo',
              'Codo derecho', 4, 8, 'ACTIVE', DATE '2026-10-06'),
             ('0150000081', 'fisio.cevallos@dev-seed.rehabelite.test', 'M75.0', 'Rigidez de hombro', 'MODERATE',
              'Sordo', 'Hombro derecho', 6, 10, 'COMPLETED', DATE '2026-10-08'),
             ('1750000034', 'fisio.paredes@dev-seed.rehabelite.test', 'M22.2', 'Dolor de rodilla al subir gradas',
              'MILD', 'Punzante', 'Rodilla izquierda', 5, 10, 'ACTIVE', DATE '2026-10-22'))
         AS t (national_id, email, diagnosis_code, chief_complaint, severity, pain_type, pain_location, pain_score,
               planned_sessions, status, start_date)
         JOIN patients p ON p.national_id = t.national_id
         JOIN clinical_records r ON r.patient_id = p.id
         JOIN users u ON u.email = t.email
WHERE NOT EXISTS (SELECT 1 FROM treatments other WHERE other.clinical_record_id = r.id);

-- The physiotherapist who created each record of the seed is the current responsible of its treatment. A treatment
-- created through the API always has its responsible, so only the seed's treatments lack one
INSERT INTO responsible_assignments (treatment_id, physiotherapist_id, valid_from)
SELECT t.id, r.created_by, t.created_at
FROM treatments t
         JOIN clinical_records r ON r.id = t.clinical_record_id
         JOIN users u ON u.id = r.created_by
WHERE u.email LIKE '%@dev-seed.rehabelite.test'
  AND NOT EXISTS (SELECT 1 FROM responsible_assignments other WHERE other.treatment_id = t.id);

-- The completed treatment keeps its closure, as a status change made through the API would
INSERT INTO treatment_status_changes (treatment_id, user_id, previous_status, new_status, reason, changed_at)
SELECT t.id, r.created_by, 'ACTIVE', 'COMPLETED', 'Alta por objetivos cumplidos (dato sintético).',
       TIMESTAMPTZ '2026-10-30 17:00:00-05'
FROM treatments t
         JOIN clinical_records r ON r.id = t.clinical_record_id
         JOIN patients p ON p.id = r.patient_id
WHERE p.national_id = '0150000081'
  AND t.status = 'COMPLETED'
  AND NOT EXISTS (SELECT 1 FROM treatment_status_changes other WHERE other.treatment_id = t.id);
