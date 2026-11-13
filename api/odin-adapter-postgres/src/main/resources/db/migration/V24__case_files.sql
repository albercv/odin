-- Encargo 13: los casos como expediente. Relaciones caso-documento, historial, enlaces de corta duración y retirada
-- de documentos. Cada documento puede estar en varios expedientes del mismo proyecto o generales.

-- Retirada de documentos: la oculta la RLS de document, salvo que la sesión active app.include_withdrawn.
ALTER TABLE document ADD COLUMN withdrawn_at timestamptz NULL;
ALTER TABLE document ADD COLUMN withdrawn_by uuid NULL REFERENCES app_user (id);
ALTER TABLE document ADD CONSTRAINT document_withdrawal_complete CHECK ((withdrawn_at IS NULL) = (withdrawn_by IS NULL));

-- D-078: el mismo fichero puede estar en dos proyectos de la misma empresa, no dos veces en el mismo sitio.
-- Lo retirado no cuenta para los duplicados.
ALTER TABLE document DROP CONSTRAINT document_company_id_sha256_key;
CREATE UNIQUE INDEX document_company_project_sha256_key ON document (company_id, project_id, sha256) NULLS NOT DISTINCT
    WHERE withdrawn_at IS NULL;
CREATE INDEX document_company_sha256_idx ON document (company_id, sha256);

-- Copia exacta de la política de V9 más la condición de retirada.
DROP POLICY document_scope ON document;
CREATE POLICY document_scope ON document
    USING ((current_setting('app.group_scope', true) = 'on' OR company_id = ANY (string_to_array(nullif(current_setting('app.company_ids', true), ''), ',')::uuid[]))
        AND (project_id IS NULL OR current_setting('app.all_projects', true) = 'on'
            OR project_id = ANY (string_to_array(nullif(current_setting('app.project_ids', true), ''), ',')::uuid[]))
        AND (withdrawn_at IS NULL OR current_setting('app.include_withdrawn', true) = 'on'));

CREATE TABLE case_file_entry (
    company_id       uuid NOT NULL REFERENCES company (id),
    case_document_id uuid NOT NULL,
    document_id      uuid NOT NULL,
    added_by         uuid NOT NULL REFERENCES app_user (id),
    added_at         timestamptz NOT NULL,
    PRIMARY KEY (case_document_id, document_id),
    FOREIGN KEY (case_document_id, company_id) REFERENCES document (id, company_id),
    FOREIGN KEY (document_id, company_id) REFERENCES document (id, company_id),
    CHECK (case_document_id <> document_id)
);
CREATE INDEX case_file_entry_document_idx ON case_file_entry (document_id);

CREATE TABLE case_file_event (
    id               uuid PRIMARY KEY,
    company_id       uuid NOT NULL REFERENCES company (id),
    case_document_id uuid NOT NULL,
    document_id      uuid NOT NULL,
    action           text NOT NULL CHECK (action IN ('ADDED', 'REMOVED', 'MOVED_OUT')),
    actor_id         uuid NOT NULL REFERENCES app_user (id),
    at               timestamptz NOT NULL,
    FOREIGN KEY (case_document_id, company_id) REFERENCES document (id, company_id),
    FOREIGN KEY (document_id, company_id) REFERENCES document (id, company_id)
);
CREATE INDEX case_file_event_case_idx ON case_file_event (case_document_id, at);

CREATE TABLE access_link (
    id               uuid PRIMARY KEY,
    kind             text NOT NULL CHECK (kind IN ('UPLOAD', 'ORIGINAL')),
    token_hash       text NOT NULL UNIQUE,
    company_id       uuid NOT NULL REFERENCES company (id),
    user_id          uuid NOT NULL REFERENCES app_user (id),
    case_document_id uuid NULL,
    document_id      uuid NULL,
    created_at       timestamptz NOT NULL,
    expires_at       timestamptz NOT NULL,
    bound_address    text NULL,
    upload_count     integer NOT NULL DEFAULT 0,
    FOREIGN KEY (case_document_id, company_id) REFERENCES document (id, company_id),
    FOREIGN KEY (document_id, company_id) REFERENCES document (id, company_id),
    CHECK ((kind = 'UPLOAD' AND case_document_id IS NOT NULL AND document_id IS NULL)
        OR (kind = 'ORIGINAL' AND document_id IS NOT NULL AND case_document_id IS NULL))
);

GRANT SELECT, INSERT, DELETE ON case_file_entry TO odin_app;
GRANT SELECT, INSERT ON case_file_event TO odin_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON access_link TO odin_app;

-- Una relación solo se ve si se ven los dos documentos: la consulta a document pasa por su propia RLS.
ALTER TABLE case_file_entry ENABLE ROW LEVEL SECURITY;
CREATE POLICY case_file_entry_scope ON case_file_entry
    USING ((current_setting('app.group_scope', true) = 'on' OR company_id = ANY (string_to_array(nullif(current_setting('app.company_ids', true), ''), ',')::uuid[]))
        AND EXISTS (SELECT 1 FROM document d WHERE d.id = case_file_entry.case_document_id)
        AND EXISTS (SELECT 1 FROM document d WHERE d.id = case_file_entry.document_id));

ALTER TABLE case_file_event ENABLE ROW LEVEL SECURITY;
CREATE POLICY case_file_event_scope ON case_file_event
    USING ((current_setting('app.group_scope', true) = 'on' OR company_id = ANY (string_to_array(nullif(current_setting('app.company_ids', true), ''), ',')::uuid[]))
        AND EXISTS (SELECT 1 FROM document d WHERE d.id = case_file_event.case_document_id)
        AND EXISTS (SELECT 1 FROM document d WHERE d.id = case_file_event.document_id));

ALTER TABLE access_link ENABLE ROW LEVEL SECURITY;
CREATE POLICY access_link_scope ON access_link
    USING (current_setting('app.group_scope', true) = 'on' OR company_id = ANY (string_to_array(nullif(current_setting('app.company_ids', true), ''), ',')::uuid[]));

-- Las dos reglas leen document sin la RLS de quien escribe: tienen que ver todos los documentos.
CREATE FUNCTION case_file_entry_rules() RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
    c document%ROWTYPE;
    d document%ROWTYPE;
BEGIN
    SELECT * INTO c FROM document WHERE id = NEW.case_document_id;
    SELECT * INTO d FROM document WHERE id = NEW.document_id;
    IF NOT c.case_document THEN
        RAISE EXCEPTION 'El documento % no es una ficha de caso', c.id USING ERRCODE = 'check_violation';
    END IF;
    IF d.project_id IS NOT NULL AND d.project_id IS DISTINCT FROM c.project_id THEN
        RAISE EXCEPTION 'El documento % no es del proyecto del caso ni general', d.id USING ERRCODE = 'check_violation';
    END IF;
    IF c.withdrawn_at IS NOT NULL OR d.withdrawn_at IS NOT NULL THEN
        RAISE EXCEPTION 'Un documento retirado no entra en un expediente' USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER case_file_entry_rules BEFORE INSERT ON case_file_entry
    FOR EACH ROW EXECUTE FUNCTION case_file_entry_rules();

CREATE FUNCTION document_project_change_guard() RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN
    IF EXISTS (SELECT 1 FROM case_file_entry e JOIN document m ON m.id = e.document_id
            WHERE e.case_document_id = NEW.id AND m.project_id IS NOT NULL AND m.project_id IS DISTINCT FROM NEW.project_id)
        OR EXISTS (SELECT 1 FROM case_file_entry e JOIN document c ON c.id = e.case_document_id
            WHERE e.document_id = NEW.id AND NEW.project_id IS NOT NULL AND NEW.project_id IS DISTINCT FROM c.project_id) THEN
        RAISE EXCEPTION 'El cambio de proyecto deja relaciones de expediente incompatibles' USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER document_project_change_guard BEFORE UPDATE OF project_id ON document
    FOR EACH ROW WHEN (OLD.project_id IS DISTINCT FROM NEW.project_id) EXECUTE FUNCTION document_project_change_guard();

-- Las empresas que ya pueden buscar reciben la capacidad nueva, como en V22.
INSERT INTO company_capability (company_id, capability)
SELECT company_id, 'CASE_FILES' FROM company_capability WHERE capability = 'SEARCH_KNOWLEDGE' ON CONFLICT DO NOTHING;
