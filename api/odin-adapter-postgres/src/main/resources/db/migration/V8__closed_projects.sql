-- Fase 2a: los proyectos nacen cerrados y solo los ven sus miembros (D-038, corrección del 2026-11-11).
ALTER TABLE project ADD COLUMN restricted boolean NOT NULL DEFAULT true;

CREATE TABLE project_member (
    project_id uuid NOT NULL REFERENCES project (id),
    user_id    uuid NOT NULL REFERENCES app_user (id),
    added_at   timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (project_id, user_id)
);
CREATE INDEX project_member_user ON project_member (user_id);

-- Los proyectos existentes quedan cerrados y con sus trabajadores como miembros: nadie pierde el acceso que tenía.
INSERT INTO project_member (project_id, user_id)
SELECT p.id, m.user_id
FROM project p JOIN membership m ON m.company_id = p.company_id
WHERE p.deleted_at IS NULL AND m.is_worker AND m.active AND m.deleted_at IS NULL;

GRANT SELECT, INSERT, UPDATE, DELETE ON project_member TO odin_app;
