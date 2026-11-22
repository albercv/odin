-- Encargo 30: vínculos entre documentos por el identificador que un documento cita en otro. Sin IA: lo calcula un
-- planificador con reglas cerradas. Un vínculo detectado no marca ninguna sustitución (D-050).
-- Las V31 a V33 están escritas en otras ramas: el salto es intencionado.

CREATE TABLE document_identifier (
    company_id  uuid NOT NULL REFERENCES company (id),
    document_id uuid NOT NULL,
    identifier  text NOT NULL,
    page_number integer NOT NULL,
    PRIMARY KEY (document_id, identifier),
    FOREIGN KEY (document_id, company_id) REFERENCES document (id, company_id) ON DELETE CASCADE
);
CREATE INDEX document_identifier_company_identifier_idx ON document_identifier (company_id, identifier);

CREATE TABLE document_link (
    id                  uuid PRIMARY KEY,
    company_id          uuid NOT NULL REFERENCES company (id),
    source_document_id  uuid NOT NULL,
    target_document_id  uuid NULL,
    relation            text NOT NULL CHECK (relation IN ('RECTIFIES', 'REPLACES', 'MODIFIES')),
    cited_identifier    text NOT NULL,
    evidence_page       integer NOT NULL,
    evidence_quote      text NOT NULL,
    origin              text NOT NULL CHECK (origin IN ('DETECTED', 'ADMIN')),
    created_at          timestamptz NOT NULL,
    UNIQUE (source_document_id, relation, cited_identifier, origin),
    FOREIGN KEY (source_document_id, company_id) REFERENCES document (id, company_id) ON DELETE CASCADE,
    FOREIGN KEY (target_document_id, company_id) REFERENCES document (id, company_id) ON DELETE CASCADE,
    CHECK (source_document_id IS DISTINCT FROM target_document_id)
);
CREATE INDEX document_link_company_cited_idx ON document_link (company_id, cited_identifier);
CREATE INDEX document_link_target_idx ON document_link (target_document_id);

-- Qué documentos ya se analizaron y con qué versión del extractor: cambiar la versión los vuelve a analizar.
CREATE TABLE document_link_scan (
    company_id        uuid NOT NULL REFERENCES company (id),
    document_id       uuid NOT NULL,
    extractor_version text NOT NULL,
    scanned_at        timestamptz NOT NULL,
    PRIMARY KEY (document_id),
    FOREIGN KEY (document_id, company_id) REFERENCES document (id, company_id) ON DELETE CASCADE
);

GRANT SELECT, INSERT, UPDATE, DELETE ON document_identifier, document_link, document_link_scan TO odin_app;

-- Solo se ve lo que se ve del documento al que pertenece: la consulta a document pasa por su propia RLS.
ALTER TABLE document_identifier ENABLE ROW LEVEL SECURITY;
CREATE POLICY document_identifier_scope ON document_identifier
    USING ((current_setting('app.group_scope', true) = 'on' OR company_id = ANY (string_to_array(nullif(current_setting('app.company_ids', true), ''), ',')::uuid[]))
        AND EXISTS (SELECT 1 FROM document d WHERE d.id = document_identifier.document_id));

ALTER TABLE document_link ENABLE ROW LEVEL SECURITY;
CREATE POLICY document_link_scope ON document_link
    USING ((current_setting('app.group_scope', true) = 'on' OR company_id = ANY (string_to_array(nullif(current_setting('app.company_ids', true), ''), ',')::uuid[]))
        AND EXISTS (SELECT 1 FROM document d WHERE d.id = document_link.source_document_id)
        AND (target_document_id IS NULL OR EXISTS (SELECT 1 FROM document d WHERE d.id = document_link.target_document_id)));

ALTER TABLE document_link_scan ENABLE ROW LEVEL SECURITY;
CREATE POLICY document_link_scan_scope ON document_link_scan
    USING (current_setting('app.group_scope', true) = 'on' OR company_id = ANY (string_to_array(nullif(current_setting('app.company_ids', true), ''), ',')::uuid[]));
