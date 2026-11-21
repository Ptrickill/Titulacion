-- HU-15: exporting the operations report to PDF is audited as EXPORT.
-- Only the allowed actions change; the table, its data and its append-only triggers stay as they are.
ALTER TABLE audit_logs DROP CONSTRAINT ck_audit_logs_action;

ALTER TABLE audit_logs
    ADD CONSTRAINT ck_audit_logs_action
        CHECK (action IN ('CREATE', 'READ', 'UPDATE', 'STATUS_CHANGE', 'ACCESS_DENIED', 'LOGIN', 'EXPORT'));
