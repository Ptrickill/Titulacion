-- =====================================================================
-- Rehab Elite - V1: initial schema (PostgreSQL 16)
-- Source of truth for the data model. DO NOT edit once applied:
-- every change goes into a new migration (V3__..., V4__...).
-- Convention: columns not marked as optional are NOT NULL.
-- Naming: plural snake_case tables; pk_, fk_, uq_, ck_, ix_ prefixes.
-- =====================================================================

-- ---------------------------------------------------------------------
-- Extensions and functions
-- (if the application user cannot create them, create them beforehand
--  as postgres; IF NOT EXISTS avoids the permission error when present)
-- ---------------------------------------------------------------------
CREATE EXTENSION IF NOT EXISTS pg_trgm WITH SCHEMA public;
CREATE EXTENSION IF NOT EXISTS unaccent WITH SCHEMA public;

-- unaccent() is STABLE; with an explicit dictionary the result is
-- deterministic and can be declared IMMUTABLE for use in indexes.
CREATE OR REPLACE FUNCTION normalize_text(input text)
    RETURNS text
    LANGUAGE sql
    IMMUTABLE
    PARALLEL SAFE
    STRICT
AS $$
    SELECT lower(public.unaccent('public.unaccent'::regdictionary, input))
$$;

-- ---------------------------------------------------------------------
-- users
-- ---------------------------------------------------------------------
CREATE TABLE users (
    id               bigserial    NOT NULL,
    email            varchar(254) NOT NULL,
    password_hash    varchar(100) NOT NULL,
    full_name        varchar(150) NOT NULL,
    role             varchar(30)  NOT NULL,
    active           boolean      NOT NULL DEFAULT true,
    failed_attempts  integer      NOT NULL DEFAULT 0,
    locked_until     timestamptz,
    last_login_at    timestamptz,
    created_by       bigint,
    created_at       timestamptz  NOT NULL DEFAULT now(),
    updated_by       bigint,
    updated_at       timestamptz,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT ck_users_role CHECK (role IN ('ADMINISTRATOR', 'PHYSIOTHERAPIST', 'PATIENT', 'OPERATIONS_DIRECTOR')),
    CONSTRAINT ck_users_failed_attempts CHECK (failed_attempts >= 0),
    CONSTRAINT fk_users_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_users_updated_by FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE RESTRICT
);

-- ---------------------------------------------------------------------
-- patients
-- first_name / last_name hold all given names / both surnames.
-- ---------------------------------------------------------------------
CREATE TABLE patients (
    id                          bigserial    NOT NULL,
    national_id                 char(10)     NOT NULL,
    medical_record_number       varchar(20)  NOT NULL,
    first_name                  varchar(100) NOT NULL,
    last_name                   varchar(100) NOT NULL,
    birth_date                  date         NOT NULL,
    sex                         varchar(10)  NOT NULL,
    phone                       varchar(20)  NOT NULL,
    email                       varchar(254) NOT NULL,
    address                     varchar(255) NOT NULL,
    occupation                  varchar(100) NOT NULL,
    education_level             varchar(50)  NOT NULL,
    health_insurance            varchar(100) NOT NULL,
    ethnic_self_identification  varchar(50),
    religion                    varchar(50),
    admission_date              date         NOT NULL DEFAULT CURRENT_DATE,
    portal_user_id              bigint,
    created_by                  bigint,
    created_at                  timestamptz  NOT NULL DEFAULT now(),
    updated_by                  bigint,
    updated_at                  timestamptz,
    CONSTRAINT pk_patients PRIMARY KEY (id),
    CONSTRAINT uq_patients_national_id UNIQUE (national_id),
    CONSTRAINT uq_patients_medical_record_number UNIQUE (medical_record_number),
    CONSTRAINT uq_patients_portal_user_id UNIQUE (portal_user_id),
    CONSTRAINT ck_patients_national_id CHECK (national_id ~ '^[0-9]{10}$'),
    CONSTRAINT ck_patients_sex CHECK (sex IN ('MALE', 'FEMALE')),
    CONSTRAINT ck_patients_birth_date CHECK (birth_date <= admission_date),
    CONSTRAINT fk_patients_portal_user FOREIGN KEY (portal_user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_patients_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_patients_updated_by FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE RESTRICT
);

-- Accent- and case-insensitive name search (ILIKE / similarity)
CREATE INDEX ix_patients_full_name_trgm
    ON patients USING gin (normalize_text(last_name || ' ' || first_name) gin_trgm_ops);

-- ---------------------------------------------------------------------
-- clinical_records (one per patient)
-- ---------------------------------------------------------------------
CREATE TABLE clinical_records (
    id                  bigserial   NOT NULL,
    patient_id          bigint      NOT NULL,
    habits              text        NOT NULL,
    physical_activity   text        NOT NULL,
    allergies           text        NOT NULL,
    current_medication  text        NOT NULL,
    personal_history    text        NOT NULL,
    surgical_history    text        NOT NULL,
    family_history      text        NOT NULL,
    created_by          bigint,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_by          bigint,
    updated_at          timestamptz,
    CONSTRAINT pk_clinical_records PRIMARY KEY (id),
    CONSTRAINT uq_clinical_records_patient_id UNIQUE (patient_id),
    CONSTRAINT fk_clinical_records_patient FOREIGN KEY (patient_id) REFERENCES patients (id) ON DELETE RESTRICT,
    CONSTRAINT fk_clinical_records_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_clinical_records_updated_by FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE RESTRICT
);

-- ---------------------------------------------------------------------
-- icd10_diagnoses (catalog)
-- ---------------------------------------------------------------------
CREATE TABLE icd10_diagnoses (
    code         varchar(7)   NOT NULL,
    description  varchar(255) NOT NULL,
    CONSTRAINT pk_icd10_diagnoses PRIMARY KEY (code)
);

-- ---------------------------------------------------------------------
-- treatments
-- ---------------------------------------------------------------------
CREATE TABLE treatments (
    id                     bigserial    NOT NULL,
    clinical_record_id     bigint       NOT NULL,
    diagnosis_code         varchar(7)   NOT NULL,
    chief_complaint        text         NOT NULL,
    current_illness        text         NOT NULL,
    referring_physician    varchar(150) NOT NULL,
    complementary_tests    text         NOT NULL,
    severity               varchar(10)  NOT NULL,
    pain_type              varchar(50)  NOT NULL,
    pain_location          varchar(100) NOT NULL,
    initial_pain_score     smallint     NOT NULL,
    pain_frequency         varchar(50)  NOT NULL,
    session_frequency      varchar(50)  NOT NULL,
    goal                   text         NOT NULL,
    planned_sessions       integer      NOT NULL,
    status                 varchar(15)  NOT NULL DEFAULT 'ACTIVE',
    start_date             date         NOT NULL DEFAULT CURRENT_DATE,
    created_by             bigint,
    created_at             timestamptz  NOT NULL DEFAULT now(),
    updated_by             bigint,
    updated_at             timestamptz,
    CONSTRAINT pk_treatments PRIMARY KEY (id),
    CONSTRAINT ck_treatments_severity CHECK (severity IN ('MILD', 'MODERATE', 'SEVERE')),
    CONSTRAINT ck_treatments_initial_pain_score CHECK (initial_pain_score BETWEEN 0 AND 10),
    CONSTRAINT ck_treatments_planned_sessions CHECK (planned_sessions > 0),
    CONSTRAINT ck_treatments_status CHECK (status IN ('ACTIVE', 'COMPLETED', 'ABANDONED')),
    CONSTRAINT fk_treatments_clinical_record FOREIGN KEY (clinical_record_id) REFERENCES clinical_records (id) ON DELETE RESTRICT,
    CONSTRAINT fk_treatments_diagnosis FOREIGN KEY (diagnosis_code) REFERENCES icd10_diagnoses (code) ON DELETE RESTRICT,
    CONSTRAINT fk_treatments_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_treatments_updated_by FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE RESTRICT
);

CREATE INDEX ix_treatments_clinical_record_id ON treatments (clinical_record_id);
CREATE INDEX ix_treatments_status ON treatments (status);

-- ---------------------------------------------------------------------
-- responsible_assignments (history of responsible physiotherapists)
-- ---------------------------------------------------------------------
CREATE TABLE responsible_assignments (
    id                 bigserial   NOT NULL,
    treatment_id       bigint      NOT NULL,
    physiotherapist_id bigint      NOT NULL,
    valid_from         timestamptz NOT NULL DEFAULT now(),
    valid_to           timestamptz,
    CONSTRAINT pk_responsible_assignments PRIMARY KEY (id),
    CONSTRAINT ck_responsible_assignments_period CHECK (valid_to IS NULL OR valid_to >= valid_from),
    CONSTRAINT fk_responsible_assignments_treatment FOREIGN KEY (treatment_id) REFERENCES treatments (id) ON DELETE RESTRICT,
    CONSTRAINT fk_responsible_assignments_physiotherapist FOREIGN KEY (physiotherapist_id) REFERENCES users (id) ON DELETE RESTRICT
);

-- Only one current responsible physiotherapist per treatment
CREATE UNIQUE INDEX uq_responsible_assignments_current
    ON responsible_assignments (treatment_id)
    WHERE valid_to IS NULL;
CREATE INDEX ix_responsible_assignments_physiotherapist_id ON responsible_assignments (physiotherapist_id);

-- ---------------------------------------------------------------------
-- treatment_status_changes
-- ---------------------------------------------------------------------
CREATE TABLE treatment_status_changes (
    id               bigserial   NOT NULL,
    treatment_id     bigint      NOT NULL,
    user_id          bigint      NOT NULL,
    previous_status  varchar(15) NOT NULL,
    new_status       varchar(15) NOT NULL,
    reason           text        NOT NULL,
    changed_at       timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_treatment_status_changes PRIMARY KEY (id),
    CONSTRAINT ck_treatment_status_changes_previous_status CHECK (previous_status IN ('ACTIVE', 'COMPLETED', 'ABANDONED')),
    CONSTRAINT ck_treatment_status_changes_new_status CHECK (new_status IN ('ACTIVE', 'COMPLETED', 'ABANDONED')),
    CONSTRAINT ck_treatment_status_changes_different CHECK (previous_status <> new_status),
    CONSTRAINT fk_treatment_status_changes_treatment FOREIGN KEY (treatment_id) REFERENCES treatments (id) ON DELETE RESTRICT,
    CONSTRAINT fk_treatment_status_changes_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT
);

CREATE INDEX ix_treatment_status_changes_treatment_id ON treatment_status_changes (treatment_id);

-- ---------------------------------------------------------------------
-- therapy_sessions
-- ---------------------------------------------------------------------
CREATE TABLE therapy_sessions (
    id                     bigserial   NOT NULL,
    treatment_id           bigint      NOT NULL,
    responsible_id         bigint      NOT NULL,
    sequence_number        integer     NOT NULL,
    session_date           date        NOT NULL,
    session_time           time        NOT NULL,
    session_type           varchar(50) NOT NULL,
    activities_performed   text        NOT NULL,
    clinical_observations  text        NOT NULL,
    patient_instructions   text,
    pain_score             smallint    NOT NULL,
    progress_status        varchar(20) NOT NULL,
    status                 varchar(15) NOT NULL,
    created_by             bigint,
    created_at             timestamptz NOT NULL DEFAULT now(),
    updated_by             bigint,
    updated_at             timestamptz,
    CONSTRAINT pk_therapy_sessions PRIMARY KEY (id),
    CONSTRAINT uq_therapy_sessions_treatment_sequence UNIQUE (treatment_id, sequence_number),
    CONSTRAINT ck_therapy_sessions_sequence_number CHECK (sequence_number > 0),
    CONSTRAINT ck_therapy_sessions_pain_score CHECK (pain_score BETWEEN 0 AND 10),
    CONSTRAINT ck_therapy_sessions_progress_status CHECK (progress_status IN ('STABLE', 'IMPROVING', 'NEEDS_ATTENTION')),
    CONSTRAINT ck_therapy_sessions_status CHECK (status IN ('COMPLETED', 'RESCHEDULED', 'NO_SHOW')),
    CONSTRAINT fk_therapy_sessions_treatment FOREIGN KEY (treatment_id) REFERENCES treatments (id) ON DELETE RESTRICT,
    CONSTRAINT fk_therapy_sessions_responsible FOREIGN KEY (responsible_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_therapy_sessions_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_therapy_sessions_updated_by FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE RESTRICT
);

CREATE INDEX ix_therapy_sessions_treatment_id_session_date ON therapy_sessions (treatment_id, session_date);
CREATE INDEX ix_therapy_sessions_responsible_id ON therapy_sessions (responsible_id);

-- ---------------------------------------------------------------------
-- ai_summaries
-- ---------------------------------------------------------------------
CREATE TABLE ai_summaries (
    id                 bigserial   NOT NULL,
    treatment_id       bigint      NOT NULL,
    generated_by_id    bigint      NOT NULL,
    reviewed_by_id     bigint,
    generated_content  text        NOT NULL,
    final_content      text,
    status             varchar(20) NOT NULL DEFAULT 'PENDING_REVIEW',
    generated_at       timestamptz NOT NULL DEFAULT now(),
    reviewed_at        timestamptz,
    CONSTRAINT pk_ai_summaries PRIMARY KEY (id),
    CONSTRAINT ck_ai_summaries_status CHECK (status IN ('PENDING_REVIEW', 'APPROVED', 'DISCARDED')),
    CONSTRAINT ck_ai_summaries_review CHECK (
        status = 'PENDING_REVIEW'
        OR (reviewed_by_id IS NOT NULL AND reviewed_at IS NOT NULL)
    ),
    CONSTRAINT fk_ai_summaries_treatment FOREIGN KEY (treatment_id) REFERENCES treatments (id) ON DELETE RESTRICT,
    CONSTRAINT fk_ai_summaries_generated_by FOREIGN KEY (generated_by_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_ai_summaries_reviewed_by FOREIGN KEY (reviewed_by_id) REFERENCES users (id) ON DELETE RESTRICT
);

CREATE INDEX ix_ai_summaries_treatment_id ON ai_summaries (treatment_id);

-- ---------------------------------------------------------------------
-- audit_logs (append-only)
-- user_id is optional so that login attempts with an unknown email
-- can also be recorded.
-- ---------------------------------------------------------------------
CREATE TABLE audit_logs (
    id           bigserial   NOT NULL,
    user_id      bigint,
    action       varchar(20) NOT NULL,
    entity_type  varchar(60) NOT NULL,
    entity_id    bigint,
    occurred_at  timestamptz NOT NULL DEFAULT now(),
    ip_address   varchar(45),
    CONSTRAINT pk_audit_logs PRIMARY KEY (id),
    CONSTRAINT ck_audit_logs_action CHECK (action IN ('CREATE', 'READ', 'UPDATE', 'STATUS_CHANGE', 'ACCESS_DENIED', 'LOGIN')),
    CONSTRAINT fk_audit_logs_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT
);

CREATE INDEX ix_audit_logs_user_id_occurred_at ON audit_logs (user_id, occurred_at);
CREATE INDEX ix_audit_logs_entity_type_entity_id ON audit_logs (entity_type, entity_id);

CREATE OR REPLACE FUNCTION prevent_audit_log_changes()
    RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'audit_logs is append-only: % is not allowed', TG_OP;
END;
$$;

CREATE TRIGGER trg_audit_logs_no_update_delete
    BEFORE UPDATE OR DELETE ON audit_logs
    FOR EACH ROW EXECUTE FUNCTION prevent_audit_log_changes();

CREATE TRIGGER trg_audit_logs_no_truncate
    BEFORE TRUNCATE ON audit_logs
    FOR EACH STATEMENT EXECUTE FUNCTION prevent_audit_log_changes();
