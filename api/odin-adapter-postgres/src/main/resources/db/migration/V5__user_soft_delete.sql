ALTER TABLE app_user ADD COLUMN deleted_at timestamptz;

DROP INDEX app_user_email_key;
CREATE UNIQUE INDEX app_user_email_key ON app_user (lower(email)) WHERE email IS NOT NULL AND deleted_at IS NULL;
