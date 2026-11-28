-- =====================================================================
-- V10 - MEJ-06 (Jira RHE-90): LOPDP compliance
-- * patient_consents: the consents of each patient (processing of personal and health data, and email
--   communications), with the version of the text, how it was given and its revocation. At most one current
--   consent per patient and type.
-- * data_subject_requests: the requests of the data subject (access, rectification, update, objection, erasure,
--   portability), with their status and response.
-- * patients: a patient can be blocked by an accepted erasure request, which also clears the email, the phone and
--   the address: both columns become optional. Existing rows do not change.
-- * audit_logs: the ANONYMIZATION action.
-- V11 (a Java migration, it needs CLINICAL_DATA_KEY) encrypts the existing clinical text.
-- =====================================================================

CREATE TABLE patient_consents (
    id                  bigserial   NOT NULL,
    patient_id          bigint      NOT NULL,
    consent_type        varchar(30) NOT NULL,
    text_version        varchar(30) NOT NULL,
    channel             varchar(20) NOT NULL,
    granted_by          bigint,
    granted_at          timestamptz NOT NULL DEFAULT now(),
    -- The patient accepted a newer version of the same text: this one is no longer the current one
    replaced_at         timestamptz,
    revoked_at          timestamptz,
    revoked_by          bigint,
    revocation_channel  varchar(20),
    CONSTRAINT pk_patient_consents PRIMARY KEY (id),
    CONSTRAINT ck_patient_consents_type CHECK (consent_type IN ('DATA_PROCESSING', 'EMAIL_COMMUNICATIONS')),
    CONSTRAINT ck_patient_consents_channel CHECK (channel IN ('PHYSICAL_SIGNATURE', 'PORTAL')),
    CONSTRAINT ck_patient_consents_revocation_channel
        CHECK (revocation_channel IN ('PHYSICAL_SIGNATURE', 'PORTAL', 'ERASURE')),
    CONSTRAINT ck_patient_consents_revocation CHECK ((revoked_at IS NULL) = (revocation_channel IS NULL)),
    CONSTRAINT ck_patient_consents_closed_once CHECK (revoked_at IS NULL OR replaced_at IS NULL),
    CONSTRAINT fk_patient_consents_patient FOREIGN KEY (patient_id) REFERENCES patients (id) ON DELETE RESTRICT,
    CONSTRAINT fk_patient_consents_granted_by FOREIGN KEY (granted_by) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_patient_consents_revoked_by FOREIGN KEY (revoked_by) REFERENCES users (id) ON DELETE RESTRICT
);

CREATE UNIQUE INDEX uq_patient_consents_current ON patient_consents (patient_id, consent_type)
    WHERE revoked_at IS NULL AND replaced_at IS NULL;
CREATE INDEX ix_patient_consents_patient_id ON patient_consents (patient_id);

CREATE TABLE data_subject_requests (
    id            bigserial     NOT NULL,
    patient_id    bigint        NOT NULL,
    request_type  varchar(20)   NOT NULL,
    received_on   date          NOT NULL,
    status        varchar(15)   NOT NULL DEFAULT 'RECEIVED',
    response      varchar(2000),
    resolved_by   bigint,
    resolved_at   timestamptz,
    created_by    bigint,
    created_at    timestamptz   NOT NULL DEFAULT now(),
    updated_by    bigint,
    updated_at    timestamptz,
    CONSTRAINT pk_data_subject_requests PRIMARY KEY (id),
    CONSTRAINT ck_data_subject_requests_type
        CHECK (request_type IN ('ACCESS', 'RECTIFICATION', 'UPDATE', 'OBJECTION', 'ERASURE', 'PORTABILITY')),
    CONSTRAINT ck_data_subject_requests_status CHECK (status IN ('RECEIVED', 'IN_PROGRESS', 'RESOLVED', 'REJECTED')),
    -- A closed request (resolved or rejected) has its response and the date it was closed; an open one has neither
    CONSTRAINT ck_data_subject_requests_closing CHECK (
        (status IN ('RESOLVED', 'REJECTED')) = (resolved_at IS NOT NULL AND response IS NOT NULL)
    ),
    CONSTRAINT fk_data_subject_requests_patient FOREIGN KEY (patient_id) REFERENCES patients (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_data_subject_requests_resolved_by FOREIGN KEY (resolved_by) REFERENCES users (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_data_subject_requests_created_by FOREIGN KEY (created_by) REFERENCES users (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_data_subject_requests_updated_by FOREIGN KEY (updated_by) REFERENCES users (id)
        ON DELETE RESTRICT
);

CREATE INDEX ix_data_subject_requests_patient_id ON data_subject_requests (patient_id);
CREATE INDEX ix_data_subject_requests_open ON data_subject_requests (request_type)
    WHERE status IN ('RECEIVED', 'IN_PROGRESS');

-- An accepted erasure request blocks the patient and clears its contact data (the clinical record is kept)
ALTER TABLE patients
    ALTER COLUMN email DROP NOT NULL,
    ALTER COLUMN phone DROP NOT NULL,
    ADD COLUMN blocked_at timestamptz,
    ADD COLUMN blocked_by bigint,
    ADD CONSTRAINT fk_patients_blocked_by FOREIGN KEY (blocked_by) REFERENCES users (id) ON DELETE RESTRICT;

ALTER TABLE audit_logs DROP CONSTRAINT ck_audit_logs_action;

ALTER TABLE audit_logs
    ADD CONSTRAINT ck_audit_logs_action
        CHECK (action IN ('CREATE', 'READ', 'UPDATE', 'STATUS_CHANGE', 'ACCESS_DENIED', 'LOGIN', 'EXPORT',
                          'INVITATION_SENT', 'INVITATION_RESENT', 'ACTIVATION', 'RESET_REQUEST',
                          'PASSWORD_RESET', 'EMAIL_CHANGE', 'EMAIL_CONFIRMATION', 'ANONYMIZATION'));
