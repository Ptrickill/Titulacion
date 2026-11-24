-- MEJ-02 (Jira RHE-85): unified people registry. Staff members get a record with the same personal data as a
-- patient, linked to exactly one account; the email of an account can change and must then be confirmed again.
-- V1 to V6 stay as they are.

-- ---------------------------------------------------------------------
-- Staff records: personal data of physiotherapists, administrators and operations directors.
-- The role lives in users. National ID, birth date, sex, phone and address may be missing only in a record created
-- for an account that existed before MEJ-02 ("Datos por completar"); the application requires them when editing.
-- ---------------------------------------------------------------------
CREATE TABLE staff_records (
    id                 bigserial    NOT NULL,
    user_id            bigint       NOT NULL,
    national_id        char(10),
    first_name         varchar(100) NOT NULL,
    last_name          varchar(100) NOT NULL,
    birth_date         date,
    sex                varchar(10),
    phone              varchar(20),
    email              varchar(254) NOT NULL,
    province_code      char(2),
    canton_code        char(4),
    parish_code        char(6),
    main_street        varchar(150),
    street_number      varchar(20),
    cross_street       varchar(150),
    address_reference  varchar(255),
    created_by         bigint,
    created_at         timestamptz  NOT NULL DEFAULT now(),
    updated_by         bigint,
    updated_at         timestamptz,
    CONSTRAINT pk_staff_records PRIMARY KEY (id),
    CONSTRAINT uq_staff_records_user_id UNIQUE (user_id),
    CONSTRAINT uq_staff_records_national_id UNIQUE (national_id),
    CONSTRAINT uq_staff_records_email UNIQUE (email),
    CONSTRAINT ck_staff_records_national_id CHECK (national_id ~ '^[0-9]{10}$'),
    CONSTRAINT ck_staff_records_sex CHECK (sex IN ('MALE', 'FEMALE')),
    -- The place and the streets go together: all of them, or none (an address still to be completed)
    CONSTRAINT ck_staff_records_address_parts CHECK (
        (province_code IS NULL AND canton_code IS NULL AND parish_code IS NULL
            AND main_street IS NULL AND cross_street IS NULL)
        OR (province_code IS NOT NULL AND canton_code IS NOT NULL AND parish_code IS NOT NULL
            AND main_street IS NOT NULL AND cross_street IS NOT NULL)),
    -- The codes are nested: the canton starts with its province and the parish with its canton
    CONSTRAINT ck_staff_records_address_codes CHECK (
        province_code IS NULL
        OR (province_code ~ '^[0-9]{2}$' AND canton_code ~ '^[0-9]{4}$' AND parish_code ~ '^[0-9]{6}$'
            AND left(canton_code, 2) = province_code AND left(parish_code, 4) = canton_code)),
    CONSTRAINT fk_staff_records_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_staff_records_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_staff_records_updated_by FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE RESTRICT
);

-- Every staff account that already exists gets its record with the data the account has: the email and the names,
-- split from full_name (1 word: first name only; 2: one and one; 3: the first and the last two; 4 or more: the first
-- two and the rest). National ID, birth date, sex, phone and address stay empty, to be completed
INSERT INTO staff_records (user_id, first_name, last_name, email, created_at)
SELECT words.id,
       CASE WHEN cardinality(words.w) >= 4 THEN array_to_string(words.w[1:2], ' ') ELSE words.w[1] END,
       CASE WHEN cardinality(words.w) >= 4 THEN array_to_string(words.w[3:], ' ')
            WHEN cardinality(words.w) >= 2 THEN array_to_string(words.w[2:], ' ')
            ELSE '' END,
       words.email,
       words.created_at
FROM (SELECT u.id, u.email, u.created_at, regexp_split_to_array(btrim(u.full_name), '\s+') AS w
      FROM users u
      WHERE u.role <> 'PATIENT') AS words;

-- The account name is always derived from the record's names: without the extra spaces of the former free text
UPDATE users u
SET full_name = btrim(s.first_name || ' ' || s.last_name)
FROM staff_records s
WHERE s.user_id = u.id
  AND u.full_name <> btrim(s.first_name || ' ' || s.last_name);

-- And a portal account takes the names of its patient
UPDATE users u
SET full_name = p.first_name || ' ' || p.last_name
FROM patients p
WHERE p.portal_user_id = u.id
  AND u.full_name <> p.first_name || ' ' || p.last_name;

-- ---------------------------------------------------------------------
-- Change of email: an activated account keeps logging in with its email until the owner confirms the new one,
-- which waits here. No two accounts may share an email, current or pending.
-- ---------------------------------------------------------------------
ALTER TABLE users ADD COLUMN pending_email varchar(254);
CREATE UNIQUE INDEX uq_users_pending_email ON users (pending_email) WHERE pending_email IS NOT NULL;

ALTER TABLE account_tokens DROP CONSTRAINT ck_account_tokens_purpose;
ALTER TABLE account_tokens
    ADD CONSTRAINT ck_account_tokens_purpose CHECK (purpose IN ('ACTIVATION', 'PASSWORD_RESET', 'EMAIL_CHANGE'));

-- New audited actions; the table, its data and its append-only triggers stay as they are
ALTER TABLE audit_logs DROP CONSTRAINT ck_audit_logs_action;

ALTER TABLE audit_logs
    ADD CONSTRAINT ck_audit_logs_action
        CHECK (action IN ('CREATE', 'READ', 'UPDATE', 'STATUS_CHANGE', 'ACCESS_DENIED', 'LOGIN', 'EXPORT',
                          'INVITATION_SENT', 'INVITATION_RESENT', 'ACTIVATION', 'RESET_REQUEST',
                          'PASSWORD_RESET', 'EMAIL_CHANGE', 'EMAIL_CONFIRMATION'));
