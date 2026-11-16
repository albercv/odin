-- Las sesiones que ya existían siguen valiendo: id generado y reauthenticated_at = created_at.
ALTER TABLE admin_session ADD COLUMN id uuid NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE admin_session ADD CONSTRAINT admin_session_id_key UNIQUE (id);
ALTER TABLE admin_session ADD COLUMN user_agent varchar(300);
ALTER TABLE admin_session ADD COLUMN ip text;
ALTER TABLE admin_session ADD COLUMN reauthenticated_at timestamptz;
UPDATE admin_session SET reauthenticated_at = created_at;
ALTER TABLE admin_session ALTER COLUMN reauthenticated_at SET NOT NULL;
