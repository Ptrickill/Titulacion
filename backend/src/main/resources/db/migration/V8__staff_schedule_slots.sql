-- MEJ-04 (Jira RHE-87): weekly schedule of the physiotherapists, used by the operations director to reassign
-- treatments according to availability. V1 to V7 stay as they are.

-- btree_gist lets one EXCLUDE constraint combine equality on plain columns (staff record and day) with the overlap
-- of time ranges. It is a trusted extension (PostgreSQL 13+): the owner of the database can create it, as on Render.
CREATE EXTENSION IF NOT EXISTS btree_gist WITH SCHEMA public;

-- PostgreSQL has no built-in range of times of day; this one compares slots of the same day
CREATE FUNCTION time_subtype_diff(x time, y time) RETURNS double precision
    LANGUAGE sql IMMUTABLE STRICT
    AS 'SELECT EXTRACT(EPOCH FROM (x - y))::double precision';

CREATE TYPE time_range AS RANGE (subtype = time, subtype_diff = time_subtype_diff);

-- ---------------------------------------------------------------------
-- Schedule slots: when a physiotherapist works, Monday (1) to Saturday (6), the ISO day numbers.
-- The application keeps only physiotherapists' records here and replaces the whole week at once, so a slot is never
-- updated (no updated_by / updated_at): a change deletes the previous slots and inserts the new ones.
-- ---------------------------------------------------------------------
CREATE TABLE staff_schedule_slots (
    id               bigserial   NOT NULL,
    staff_record_id  bigint      NOT NULL,
    day_of_week      smallint    NOT NULL,
    start_time       time        NOT NULL,
    end_time         time        NOT NULL,
    created_by       bigint,
    created_at       timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_staff_schedule_slots PRIMARY KEY (id),
    CONSTRAINT ck_staff_schedule_slots_day CHECK (day_of_week BETWEEN 1 AND 6),
    CONSTRAINT ck_staff_schedule_slots_order CHECK (start_time < end_time),
    -- The widest clinic hours; app.clinic.opening-time / closing-time may only narrow them (checked at startup)
    CONSTRAINT ck_staff_schedule_slots_clinic_hours CHECK (start_time >= TIME '07:00' AND end_time <= TIME '20:00'),
    -- Two slots of the same day never overlap. Half-open ranges [start, end): contiguous slots (08-12 and 12-14)
    -- do not overlap, although the application merges them before saving
    CONSTRAINT ex_staff_schedule_slots_overlap EXCLUDE USING gist (
        staff_record_id WITH =,
        day_of_week WITH =,
        time_range(start_time, end_time, '[)') WITH &&),
    CONSTRAINT fk_staff_schedule_slots_staff_record FOREIGN KEY (staff_record_id) REFERENCES staff_records (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_staff_schedule_slots_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE RESTRICT
);
