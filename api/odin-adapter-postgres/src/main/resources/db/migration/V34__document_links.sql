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
    -- Lo detectado solo cuenta cuando una persona lo confirma (D-050); un vínculo descartado no vuelve a salir.
    status              text NOT NULL DEFAULT 'DETECTED' CHECK (status IN ('DETECTED', 'CONFIRMED', 'DISMISSED')),
    reviewed_by         uuid NULL REFERENCES app_user (id),
    reviewed_at         timestamptz NULL,
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

-- Lo que una respuesta puede decir de un vínculo o un sustituto que quien lee no ve: solo cuántos, nunca cuáles. La RLS
-- de arriba es estricta (un vínculo solo se ve con sus dos extremos a la vista); esta función es la única vía para
-- contar lo oculto. Es SECURITY DEFINER: lee saltándose la RLS, así que comprueba aquí mismo, con las mismas variables
-- de sesión que fija ScopedTransactions, que el documento de partida se ve, y no devuelve ni ids, ni títulos, ni
-- identificadores, ni frases.
CREATE FUNCTION document_visible_to_caller(p_company uuid, p_project uuid, p_withdrawn timestamptz,
        p_pending boolean, p_scan_of uuid) RETURNS boolean
    LANGUAGE sql STABLE SET search_path = pg_catalog, public
    AS $$
        SELECT coalesce((current_setting('app.group_scope', true) = 'on'
                OR p_company = ANY (string_to_array(nullif(current_setting('app.company_ids', true), ''), ',')::uuid[]))
            AND (p_project IS NULL OR current_setting('app.all_projects', true) = 'on'
                OR p_project = ANY (string_to_array(nullif(current_setting('app.project_ids', true), ''), ',')::uuid[]))
            AND (p_withdrawn IS NULL OR current_setting('app.include_withdrawn', true) = 'on')
            AND (NOT p_pending OR current_setting('app.include_intake', true) = 'on')
            AND p_scan_of IS NULL, false)
    $$;

CREATE FUNCTION document_hidden_context(p_document uuid, p_chain uuid[])
    RETURNS TABLE (kind text, relation text, hidden_count integer)
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = pg_catalog, public
    AS $$
        WITH seed AS (
            SELECT d.id FROM document d
            WHERE d.id = p_document
              AND document_visible_to_caller(d.company_id, d.project_id, d.withdrawn_at, d.intake_pending, d.scan_of)
        ), chain AS (
            SELECT d.id, d.superseded_by FROM document d
            WHERE d.id = ANY (p_chain) AND EXISTS (SELECT 1 FROM seed)
              AND document_visible_to_caller(d.company_id, d.project_id, d.withdrawn_at, d.intake_pending, d.scan_of)
        ), links AS (
            SELECT CASE WHEN l.source_document_id = (SELECT id FROM seed) THEN 'CORRECTS' ELSE 'CORRECTED_BY' END AS kind,
                   l.relation
            FROM document_link l
            JOIN document s ON s.id = l.source_document_id
            JOIN document t ON t.id = l.target_document_id
            WHERE EXISTS (SELECT 1 FROM seed) AND l.status <> 'DISMISSED'
              AND (l.source_document_id = (SELECT id FROM seed) OR l.target_document_id = (SELECT id FROM seed))
              AND NOT (l.relation = 'REPLACES' AND t.superseded_by = l.source_document_id)
              AND NOT (document_visible_to_caller(s.company_id, s.project_id, s.withdrawn_at, s.intake_pending, s.scan_of)
                   AND document_visible_to_caller(t.company_id, t.project_id, t.withdrawn_at, t.intake_pending, t.scan_of))
        ), hidden_chain AS (
            SELECT n.id FROM document n
            WHERE (n.superseded_by IN (SELECT id FROM chain) OR n.id IN (SELECT superseded_by FROM chain))
              AND NOT document_visible_to_caller(n.company_id, n.project_id, n.withdrawn_at, n.intake_pending, n.scan_of)
        )
        SELECT links.kind, links.relation, count(*)::integer FROM links GROUP BY links.kind, links.relation
        UNION ALL
        SELECT 'CHAIN', NULL, count(*)::integer FROM hidden_chain HAVING count(*) > 0
    $$;

REVOKE ALL ON FUNCTION document_visible_to_caller(uuid, uuid, timestamptz, boolean, uuid) FROM PUBLIC;
REVOKE ALL ON FUNCTION document_hidden_context(uuid, uuid[]) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION document_hidden_context(uuid, uuid[]) TO odin_app;
