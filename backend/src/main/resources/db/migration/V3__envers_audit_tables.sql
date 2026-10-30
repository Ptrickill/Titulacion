-- =====================================================================
-- Rehab Elite - V3: version history with Hibernate Envers (HU-18)
-- revinfo holds one row per revision (when and who); each *_aud table
-- keeps every version of an audited table. V1 and V2 are not edited.
-- Rule: a future change to treatments or therapy_sessions must change
-- its _aud table in the same migration (Hibernate validates both).
-- =====================================================================

-- ---------------------------------------------------------------------
-- revinfo: a revision groups the changes of one transaction.
-- user_id is optional: changes without an authenticated user
-- (initializers, dev seed) have no author.
-- ---------------------------------------------------------------------
CREATE TABLE revinfo (
    rev                 bigserial   NOT NULL,
    revision_timestamp  timestamptz NOT NULL,
    user_id             bigint,
    CONSTRAINT pk_revinfo PRIMARY KEY (rev),
    CONSTRAINT fk_revinfo_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT
);

CREATE INDEX ix_revinfo_user_id ON revinfo (user_id);

-- ---------------------------------------------------------------------
-- treatments_aud: same columns as treatments, all optional (Envers
-- stores nulls for a deletion), plus the revision and its type
-- (0 = insert, 1 = update, 2 = delete).
-- ---------------------------------------------------------------------
CREATE TABLE treatments_aud (
    id                     bigint       NOT NULL,
    rev                    bigint       NOT NULL,
    revtype                smallint,
    clinical_record_id     bigint,
    diagnosis_code         varchar(7),
    chief_complaint        text,
    current_illness        text,
    referring_physician    varchar(150),
    complementary_tests    text,
    severity               varchar(10),
    pain_type              varchar(50),
    pain_location          varchar(100),
    initial_pain_score     smallint,
    pain_frequency         varchar(50),
    session_frequency      varchar(50),
    goal                   text,
    planned_sessions       integer,
    status                 varchar(15),
    start_date             date,
    created_by             bigint,
    created_at             timestamptz,
    updated_by             bigint,
    updated_at             timestamptz,
    CONSTRAINT pk_treatments_aud PRIMARY KEY (id, rev),
    CONSTRAINT fk_treatments_aud_revinfo FOREIGN KEY (rev) REFERENCES revinfo (rev) ON DELETE RESTRICT
);

CREATE INDEX ix_treatments_aud_rev ON treatments_aud (rev);

-- ---------------------------------------------------------------------
-- therapy_sessions_aud: ready for HU-10, which maps TherapySession with
-- @Audited and must check that these columns still match
-- therapy_sessions.
-- ---------------------------------------------------------------------
CREATE TABLE therapy_sessions_aud (
    id                     bigint       NOT NULL,
    rev                    bigint       NOT NULL,
    revtype                smallint,
    treatment_id           bigint,
    responsible_id         bigint,
    sequence_number        integer,
    session_date           date,
    session_time           time,
    session_type           varchar(50),
    activities_performed   text,
    clinical_observations  text,
    patient_instructions   text,
    pain_score             smallint,
    progress_status        varchar(20),
    status                 varchar(15),
    created_by             bigint,
    created_at             timestamptz,
    updated_by             bigint,
    updated_at             timestamptz,
    CONSTRAINT pk_therapy_sessions_aud PRIMARY KEY (id, rev),
    CONSTRAINT fk_therapy_sessions_aud_revinfo FOREIGN KEY (rev) REFERENCES revinfo (rev) ON DELETE RESTRICT
);

CREATE INDEX ix_therapy_sessions_aud_rev ON therapy_sessions_aud (rev);

-- ---------------------------------------------------------------------
-- GET /api/audit-logs lists the log by date (most recent first) and
-- filters by date range without a user or entity.
-- ---------------------------------------------------------------------
CREATE INDEX ix_audit_logs_occurred_at ON audit_logs (occurred_at);
