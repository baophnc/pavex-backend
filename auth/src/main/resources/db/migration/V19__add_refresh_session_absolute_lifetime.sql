-- Refresh token rotation used to restart the 30-day window on every refresh,
-- so an active client could stay signed in forever. session_started_at is the
-- original login time and is carried across rotations; the session can never
-- outlive session_started_at + security.jwt.refresh-session-max-lifetime.
ALTER TABLE auth.refresh_session
    ADD COLUMN session_started_at TIMESTAMPTZ;

-- The real login time of existing chains is unknown; the current link's
-- creation time is the closest safe approximation.
UPDATE auth.refresh_session
SET session_started_at = created_at
WHERE session_started_at IS NULL;

ALTER TABLE auth.refresh_session
    ALTER COLUMN session_started_at SET NOT NULL;
