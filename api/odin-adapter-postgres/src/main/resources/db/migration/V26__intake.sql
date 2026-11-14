-- Primera carga: lotes, candidatos a proyecto y propuestas. Los documentos de un lote nacen pendientes (D1):
-- la RLS los oculta a quien no activa app.include_intake (solo las rutas de la bandeja).

-- Otro encargo crea la misma restricción: solo si no existe.
DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'project_id_company_unique') THEN ALTER TABLE project ADD CONSTRAINT project_id_company_unique UNIQUE (id, company_id); END IF; END $$;

CREATE TABLE intake_batch (
    id         uuid PRIMARY KEY,
    company_id uuid NOT NULL REFERENCES company (id),
    started_by uuid NOT NULL REFERENCES app_user (id),
    channel    text NOT NULL CHECK (channel IN ('COMMAND', 'PANEL')),
    state      text NOT NULL CHECK (state IN ('OPEN', 'READING', 'IN_REVIEW', 'CLOSED')),
    created_at timestamptz NOT NULL,
    closed_at  timestamptz NULL,
    -- ficheros que al subirse ya estaban en la empresa, y los descartados en este lote; sobreviven a reanudar
    already_there integer NOT NULL DEFAULT 0 CHECK (already_there >= 0),
    discarded     integer NOT NULL DEFAULT 0 CHECK (discarded >= 0),
    UNIQUE (id, company_id),
    CHECK ((state = 'CLOSED') = (closed_at IS NOT NULL))
);
-- Solo hay un lote abierto o en lectura por empresa a la vez.
CREATE UNIQUE INDEX intake_batch_one_active_key ON intake_batch (company_id) WHERE state IN ('OPEN', 'READING');

ALTER TABLE document ADD COLUMN intake_batch_id uuid NULL;
ALTER TABLE document ADD COLUMN intake_pending boolean NOT NULL DEFAULT false;
ALTER TABLE document ADD COLUMN intake_path text NULL;
ALTER TABLE document ADD COLUMN scan_of uuid NULL;
ALTER TABLE document ADD CONSTRAINT document_intake_batch_same_company_fk
    FOREIGN KEY (intake_batch_id, company_id) REFERENCES intake_batch (id, company_id);
ALTER TABLE document ADD CONSTRAINT document_scan_of_same_company_fk
    FOREIGN KEY (scan_of, company_id) REFERENCES document (id, company_id);
ALTER TABLE document ADD CONSTRAINT document_scan_of_not_self CHECK (scan_of IS NULL OR scan_of <> id);
ALTER TABLE document ADD CONSTRAINT document_pending_has_batch CHECK (NOT intake_pending OR intake_batch_id IS NOT NULL);
-- D1: lo pendiente no cuenta para los duplicados de quien no lo ve (subir lo mismo no delata el documento oculto);
-- entre los propios pendientes, la huella es única en la empresa para que reanudar no duplique.
DROP INDEX document_company_project_sha256_key;
CREATE UNIQUE INDEX document_company_project_sha256_key ON document (company_id, project_id, sha256) NULLS NOT DISTINCT
    WHERE withdrawn_at IS NULL AND NOT intake_pending;
CREATE UNIQUE INDEX document_pending_sha256_key ON document (company_id, sha256)
    WHERE withdrawn_at IS NULL AND intake_pending;
CREATE INDEX document_intake_batch_idx ON document (intake_batch_id) WHERE intake_batch_id IS NOT NULL;

-- Copia exacta de la política de V24 más la condición de lo pendiente.
DROP POLICY document_scope ON document;
CREATE POLICY document_scope ON document
    USING ((current_setting('app.group_scope', true) = 'on' OR company_id = ANY (string_to_array(nullif(current_setting('app.company_ids', true), ''), ',')::uuid[]))
        AND (project_id IS NULL OR current_setting('app.all_projects', true) = 'on'
            OR project_id = ANY (string_to_array(nullif(current_setting('app.project_ids', true), ''), ',')::uuid[]))
        AND (withdrawn_at IS NULL OR current_setting('app.include_withdrawn', true) = 'on')
        AND (NOT intake_pending OR current_setting('app.include_intake', true) = 'on'));

CREATE TABLE intake_project_candidate (
    company_id     uuid NOT NULL REFERENCES company (id),
    batch_id       uuid NOT NULL,
    name           text NOT NULL,
    origin         text NOT NULL CHECK (origin IN ('IMPORTED', 'DISCOVERED')),
    document_count integer NOT NULL CHECK (document_count >= 0),
    state          text NOT NULL CHECK (state IN ('PROPOSED', 'CONFIRMED', 'REJECTED')),
    project_id     uuid NULL,
    PRIMARY KEY (batch_id, name),
    FOREIGN KEY (batch_id, company_id) REFERENCES intake_batch (id, company_id),
    FOREIGN KEY (project_id, company_id) REFERENCES project (id, company_id)
);

CREATE TABLE intake_proposal (
    id                 uuid PRIMARY KEY,
    company_id         uuid NOT NULL REFERENCES company (id),
    batch_id           uuid NOT NULL,
    document_id        uuid NOT NULL,
    kind               text NOT NULL CHECK (kind IN ('PROJECT', 'COMPANY_WIDE', 'CASE_MARK', 'CASE_LINK', 'SAME_AS_SCAN', 'SUPERSEDES')),
    target_project_id  uuid NULL,
    target_document_id uuid NULL,
    case_number        text NULL,
    confidence         text NOT NULL CHECK (confidence IN ('HIGH', 'LOW')),
    source             text NOT NULL CHECK (source IN ('RULES', 'FOLDER', 'MODEL')),
    reason             text NOT NULL CHECK (char_length(reason) <= 300),
    state              text NOT NULL CHECK (state IN ('PROPOSED', 'CONFIRMED', 'REJECTED')),
    decided_by         uuid NULL REFERENCES app_user (id),
    decided_at         timestamptz NULL,
    FOREIGN KEY (batch_id, company_id) REFERENCES intake_batch (id, company_id),
    FOREIGN KEY (document_id, company_id) REFERENCES document (id, company_id),
    FOREIGN KEY (target_document_id, company_id) REFERENCES document (id, company_id),
    FOREIGN KEY (target_project_id, company_id) REFERENCES project (id, company_id),
    CHECK ((state = 'PROPOSED') = (decided_at IS NULL))
);
CREATE INDEX intake_proposal_batch_idx ON intake_proposal (batch_id);
-- Idempotencia de la clasificación: la misma propuesta (lote, documento, tipo y destinos) no se guarda dos veces,
-- aunque dos instancias o dos pasadas la escriban a la vez. Un documento solo tiene un número de caso.
CREATE UNIQUE INDEX intake_proposal_same_uq ON intake_proposal (batch_id, document_id, kind,
    coalesce(target_document_id, '00000000-0000-0000-0000-000000000000'),
    coalesce(target_project_id, '00000000-0000-0000-0000-000000000000'));

GRANT SELECT, INSERT, UPDATE ON intake_batch, intake_project_candidate, intake_proposal TO odin_app;

-- Solo la bandeja (app.include_intake) ve los lotes, los candidatos y las propuestas: citan documentos pendientes.
ALTER TABLE intake_batch ENABLE ROW LEVEL SECURITY;
CREATE POLICY intake_batch_scope ON intake_batch
    USING ((current_setting('app.group_scope', true) = 'on' OR company_id = ANY (string_to_array(nullif(current_setting('app.company_ids', true), ''), ',')::uuid[]))
        AND current_setting('app.include_intake', true) = 'on');

ALTER TABLE intake_project_candidate ENABLE ROW LEVEL SECURITY;
CREATE POLICY intake_project_candidate_scope ON intake_project_candidate
    USING ((current_setting('app.group_scope', true) = 'on' OR company_id = ANY (string_to_array(nullif(current_setting('app.company_ids', true), ''), ',')::uuid[]))
        AND current_setting('app.include_intake', true) = 'on');

ALTER TABLE intake_proposal ENABLE ROW LEVEL SECURITY;
CREATE POLICY intake_proposal_scope ON intake_proposal
    USING ((current_setting('app.group_scope', true) = 'on' OR company_id = ANY (string_to_array(nullif(current_setting('app.company_ids', true), ''), ',')::uuid[]))
        AND current_setting('app.include_intake', true) = 'on'
        AND EXISTS (SELECT 1 FROM document d WHERE d.id = intake_proposal.document_id));
