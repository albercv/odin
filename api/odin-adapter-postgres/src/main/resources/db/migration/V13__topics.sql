-- Fase 2, bloque 3: etiquetado por tema (D-031). Lista de temas de cada empresa, temas de cada documento y estado del etiquetado.
CREATE EXTENSION IF NOT EXISTS unaccent;

-- unaccent no es inmutable y un índice lo exige: este envoltorio fija el diccionario y la minúscula.
CREATE FUNCTION topic_name_key(text) RETURNS text LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT
AS $$ SELECT lower(public.unaccent('public.unaccent'::regdictionary, $1)) $$;

ALTER TABLE company ADD COLUMN topic_list_version integer NOT NULL DEFAULT 0;
ALTER TABLE company ADD COLUMN topic_list_changed_at timestamptz;

CREATE TABLE topic (
    id         uuid PRIMARY KEY,
    company_id uuid NOT NULL REFERENCES company (id),
    name       text NOT NULL,
    definition text NOT NULL,
    deleted_at timestamptz
);
CREATE UNIQUE INDEX topic_company_name_idx ON topic (company_id, topic_name_key(name)) WHERE deleted_at IS NULL;

CREATE TABLE document_topic (
    document_id    uuid NOT NULL REFERENCES document (id) ON DELETE CASCADE,
    topic_id       uuid NOT NULL REFERENCES topic (id),
    company_id     uuid NOT NULL REFERENCES company (id),
    source         text NOT NULL,
    state          text NOT NULL,
    tagger_version text,
    decided_by     uuid REFERENCES app_user (id),
    decided_at     timestamptz,
    PRIMARY KEY (document_id, topic_id)
);

CREATE TABLE document_tagging (
    seq                bigint GENERATED ALWAYS AS IDENTITY,
    document_id        uuid PRIMARY KEY REFERENCES document (id) ON DELETE CASCADE,
    company_id         uuid NOT NULL REFERENCES company (id),
    topic_list_version integer NOT NULL,
    status             text NOT NULL,
    attempt_count      integer NOT NULL,
    deadline_at        timestamptz,
    failure_reason     text,
    tagger_version     text
);

GRANT SELECT, INSERT, UPDATE, DELETE ON topic, document_topic, document_tagging TO odin_app;

-- La lista es por empresa, como los proyectos.
ALTER TABLE topic ENABLE ROW LEVEL SECURITY;
CREATE POLICY topic_scope ON topic
    USING (current_setting('app.group_scope', true) = 'on' OR company_id = ANY (string_to_array(nullif(current_setting('app.company_ids', true), ''), ',')::uuid[]));

-- Los temas y el estado de un documento toman el proyecto de su documento, como sus páginas (V9).
ALTER TABLE document_topic ENABLE ROW LEVEL SECURITY;
CREATE POLICY document_topic_scope ON document_topic
    USING ((current_setting('app.group_scope', true) = 'on' OR company_id = ANY (string_to_array(nullif(current_setting('app.company_ids', true), ''), ',')::uuid[]))
        AND EXISTS (SELECT 1 FROM document d WHERE d.id = document_topic.document_id
            AND (d.project_id IS NULL OR current_setting('app.all_projects', true) = 'on'
                OR d.project_id = ANY (string_to_array(nullif(current_setting('app.project_ids', true), ''), ',')::uuid[]))));

ALTER TABLE document_tagging ENABLE ROW LEVEL SECURITY;
CREATE POLICY document_tagging_scope ON document_tagging
    USING ((current_setting('app.group_scope', true) = 'on' OR company_id = ANY (string_to_array(nullif(current_setting('app.company_ids', true), ''), ',')::uuid[]))
        AND EXISTS (SELECT 1 FROM document d WHERE d.id = document_tagging.document_id
            AND (d.project_id IS NULL OR current_setting('app.all_projects', true) = 'on'
                OR d.project_id = ANY (string_to_array(nullif(current_setting('app.project_ids', true), ''), ',')::uuid[]))));
