-- Fase 2a: la persona se separa de su pertenencia a cada empresa (D-038).
CREATE TABLE membership (
    seq              bigint GENERATED ALWAYS AS IDENTITY,
    user_id          uuid NOT NULL REFERENCES app_user (id),
    company_id       uuid NOT NULL REFERENCES company (id),
    is_company_admin boolean NOT NULL,
    is_worker        boolean NOT NULL,
    active           boolean NOT NULL,
    deleted_at       timestamptz,
    PRIMARY KEY (user_id, company_id),
    CHECK (is_company_admin OR is_worker)
);

INSERT INTO membership (user_id, company_id, is_company_admin, is_worker, active, deleted_at)
SELECT id, company_id, role = 'COMPANY_ADMIN', role = 'WORKER', active, deleted_at
FROM app_user
WHERE company_id IS NOT NULL
ORDER BY seq;

-- Para quien pertenece a una empresa, desactivar ya no toca a la persona sino a su pertenencia.
UPDATE app_user SET active = true WHERE company_id IS NOT NULL;

ALTER TABLE app_user ADD COLUMN group_admin boolean NOT NULL DEFAULT false;
UPDATE app_user SET group_admin = true WHERE role = 'GROUP_ADMIN';

ALTER TABLE access_key ADD COLUMN company_id uuid REFERENCES company (id);
UPDATE access_key SET company_id = (SELECT company_id FROM app_user WHERE app_user.id = access_key.user_id);
ALTER TABLE access_key ALTER COLUMN company_id SET NOT NULL;

ALTER TABLE app_user DROP COLUMN role;
ALTER TABLE app_user DROP COLUMN company_id;

GRANT SELECT, INSERT, UPDATE, DELETE ON membership TO odin_app;

ALTER TABLE membership ENABLE ROW LEVEL SECURITY;
CREATE POLICY membership_scope ON membership
    USING (current_setting('app.group_scope', true) = 'on' OR company_id = ANY (string_to_array(nullif(current_setting('app.company_ids', true), ''), ',')::uuid[]));
