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
-- No clinical data and no audit entries: this is bootstrap data, not an
-- action of a user.
-- =====================================================================

-- ---------------------------------------------------------------------
-- Accounts: every seed email ends with @dev-seed.rehabelite.test
-- ---------------------------------------------------------------------
-- MEJ-01: the seed accounts are already activated (verified email); their password comes from DEV_SEED_PASSWORD
INSERT INTO users (email, password_hash, full_name, role, email_verified_at)
VALUES ('fisio.andrade@dev-seed.rehabelite.test', 'dev-seed:password-set-at-startup', 'Valeria Andrade Mora', 'PHYSIOTHERAPIST', now()),
       ('fisio.cevallos@dev-seed.rehabelite.test', 'dev-seed:password-set-at-startup', 'Martín Cevallos Ruiz', 'PHYSIOTHERAPIST', now()),
       ('direccion@dev-seed.rehabelite.test', 'dev-seed:password-set-at-startup', 'Paula Herrera Vásconez', 'OPERATIONS_DIRECTOR', now()),
       ('paciente.ortiz@dev-seed.rehabelite.test', 'dev-seed:password-set-at-startup', 'Rosa Elena Ortiz Vega', 'PATIENT', now()),
       ('paciente.quishpe@dev-seed.rehabelite.test', 'dev-seed:password-set-at-startup', 'Luis Alberto Quishpe Tipán', 'PATIENT', now()),
       ('paciente.zambrano@dev-seed.rehabelite.test', 'dev-seed:password-set-at-startup', 'Daniela Sofía Zambrano Loor', 'PATIENT', now())
ON CONFLICT (email) DO NOTHING;

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
