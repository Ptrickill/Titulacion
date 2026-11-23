-- MEJ-03 (Jira RHE-86): structured address of the patient (province, canton and parish of INEC's Clasificador
-- Geográfico Estadístico, plus the streets) and "¿Tiene seguro de salud?" as yes/no. V1 to V5 stay as they are.

-- Health insurance: the free text becomes yes/no. 'Ninguno' (in any case, with or without spaces) means no
ALTER TABLE patients ADD COLUMN has_health_insurance boolean;
UPDATE patients SET has_health_insurance = lower(btrim(health_insurance)) <> 'ninguno';
ALTER TABLE patients ALTER COLUMN has_health_insurance SET NOT NULL;
ALTER TABLE patients DROP COLUMN health_insurance;

-- Address: official codes of the parish, its canton and its province (2, 4 and 6 digits), and the streets
ALTER TABLE patients
    ADD COLUMN province_code     char(2),
    ADD COLUMN canton_code       char(4),
    ADD COLUMN parish_code       char(6),
    ADD COLUMN main_street       varchar(150),
    ADD COLUMN street_number     varchar(20),
    ADD COLUMN cross_street      varchar(150),
    ADD COLUMN address_reference varchar(255);

-- Existing patients keep their free-text address as the reference; their address stays incomplete until it is
-- completed when they are edited
UPDATE patients SET address_reference = address;
ALTER TABLE patients DROP COLUMN address;

-- The place and the streets go together: all of them, or none (an address still to be completed)
ALTER TABLE patients
    ADD CONSTRAINT ck_patients_address_parts CHECK (
        (province_code IS NULL AND canton_code IS NULL AND parish_code IS NULL
            AND main_street IS NULL AND cross_street IS NULL)
        OR (province_code IS NOT NULL AND canton_code IS NOT NULL AND parish_code IS NOT NULL
            AND main_street IS NOT NULL AND cross_street IS NOT NULL));

-- The codes are nested: the canton starts with its province and the parish with its canton
ALTER TABLE patients
    ADD CONSTRAINT ck_patients_address_codes CHECK (
        province_code IS NULL
        OR (province_code ~ '^[0-9]{2}$' AND canton_code ~ '^[0-9]{4}$' AND parish_code ~ '^[0-9]{6}$'
            AND left(canton_code, 2) = province_code AND left(parish_code, 4) = canton_code));
