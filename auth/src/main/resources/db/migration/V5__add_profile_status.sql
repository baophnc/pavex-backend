ALTER TABLE auth.user_account
ADD COLUMN profile_status VARCHAR(32) NOT NULL DEFAULT 'INCOMPLETE';

ALTER TABLE auth.user_account
ADD CONSTRAINT ck_user_account_profile_status
CHECK (
    profile_status IN (
        'INCOMPLETE',
        'COMPLETED',
        'PENDING_VERIFICATION',
        'REJECTED'
    )
);
