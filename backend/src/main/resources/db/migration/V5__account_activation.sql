-- MEJ-01: account activation and email verification, and password reset.
-- The administrator no longer sets the password: a new account has none until its owner activates it through
-- the emailed link. V1 to V4 stay as they are.

-- A pending account has no password yet
ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;

-- When the owner proved they control the email (activation); null while the account is pending
ALTER TABLE users ADD COLUMN email_verified_at timestamptz;

-- Incremented when the password is reset: access tokens carry it (claim "sv") and older ones stop working
ALTER TABLE users ADD COLUMN session_version integer NOT NULL DEFAULT 0;

-- Every account created before this migration already had a password set by the administrator
UPDATE users SET email_verified_at = created_at WHERE email_verified_at IS NULL;

-- One-time links: only the SHA-256 of the token (hex) is stored, never the token itself
CREATE TABLE account_tokens (
    id          bigserial   NOT NULL,
    user_id     bigint      NOT NULL,
    purpose     varchar(20) NOT NULL,
    token_hash  char(64)    NOT NULL,
    expires_at  timestamptz NOT NULL,
    used_at     timestamptz,
    created_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_account_tokens PRIMARY KEY (id),
    CONSTRAINT uq_account_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT ck_account_tokens_purpose CHECK (purpose IN ('ACTIVATION', 'PASSWORD_RESET')),
    CONSTRAINT fk_account_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT
);

-- Previous tokens of a purpose (invalidation) and the reset requests of the last hour (rate limit)
CREATE INDEX ix_account_tokens_user_id_purpose_created_at ON account_tokens (user_id, purpose, created_at);

-- New audited actions; the table, its data and its append-only triggers stay as they are
ALTER TABLE audit_logs DROP CONSTRAINT ck_audit_logs_action;

ALTER TABLE audit_logs
    ADD CONSTRAINT ck_audit_logs_action
        CHECK (action IN ('CREATE', 'READ', 'UPDATE', 'STATUS_CHANGE', 'ACCESS_DENIED', 'LOGIN', 'EXPORT',
                          'INVITATION_SENT', 'INVITATION_RESENT', 'ACTIVATION', 'RESET_REQUEST',
                          'PASSWORD_RESET'));
