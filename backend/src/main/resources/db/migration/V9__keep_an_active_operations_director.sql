-- =====================================================================
-- V9 (MEJ-05): at least one operations director stays active
-- The operations director manages the staff and its accounts, so the
-- application never deactivates the last active director (enabled and
-- activated). UserManagementService checks it with the directors locked;
-- this trigger backs the rule for any other UPDATE.
--
-- It only acts when the updated row was an active director and stops
-- being one: a database without any active director (for example, one
-- created before MEJ-05) can still be updated. The advisory lock makes
-- two concurrent updates of directors run one after the other, so the
-- second one sees the first.
-- =====================================================================

CREATE FUNCTION keep_an_active_operations_director() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    IF OLD.role = 'OPERATIONS_DIRECTOR' AND OLD.active AND OLD.email_verified_at IS NOT NULL
       AND NOT (NEW.role = 'OPERATIONS_DIRECTOR' AND NEW.active AND NEW.email_verified_at IS NOT NULL) THEN
        PERFORM pg_advisory_xact_lock(hashtext('users_active_operations_director'));
        IF NOT EXISTS (
            SELECT 1 FROM users
            WHERE id <> OLD.id AND role = 'OPERATIONS_DIRECTOR' AND active AND email_verified_at IS NOT NULL
        ) THEN
            RAISE EXCEPTION 'At least one active operations director must remain'
                USING ERRCODE = 'check_violation', CONSTRAINT = 'trg_users_keep_active_director';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_users_keep_active_director
    BEFORE UPDATE OF role, active, email_verified_at ON users
    FOR EACH ROW EXECUTE FUNCTION keep_an_active_operations_director();
