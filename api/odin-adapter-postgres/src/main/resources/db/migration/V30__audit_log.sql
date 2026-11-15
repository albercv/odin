-- Encargo 16: registro de actividad inalterable. V27 y V28 son de otros equipos; el hueco es intencionado.
--
-- Serialización de la huella (para reconstruir un verificador fuera de la base):
--   hash = hex minúsculas de SHA-256 sobre los bytes UTF-8 del texto de
--     jsonb_build_array(id, occurred_at, group_id, company_id, project_id, actor_kind, actor_user_id, actor_role,
--                       channel, via, action, target_kind, target_id, target_label, outcome, details, request_id,
--                       chain_key, prev_hash)::text
--   en ese orden, con occurred_at como texto UTC 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"', los uuid en minúsculas con guiones,
--   el nulo como null y details tal como lo escribe jsonb::text (claves ordenadas, espacio tras ':' y ',').
--   El primer eslabón de la cadena 'main' usa prev_hash = 64 ceros. audit_actor queda fuera de la huella.

CREATE TABLE audit_event (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    occurred_at   timestamptz NOT NULL,
    group_id      uuid NULL,
    company_id    uuid NULL,
    project_id    uuid NULL,
    actor_kind    text NOT NULL CHECK (actor_kind IN ('PERSON', 'SYSTEM', 'INSTALLER', 'LINK')),
    actor_user_id uuid NULL,
    actor_role    text NULL,
    channel       text NOT NULL CHECK (channel IN ('PANEL', 'API', 'CHAT', 'CHAT_ADMIN', 'SCRIPT', 'SYSTEM')),
    via           text NULL,
    action        text NOT NULL,
    target_kind   text NULL,
    target_id     text NULL,
    target_label  text NULL,
    outcome       text NOT NULL CHECK (outcome IN ('DONE', 'DENIED', 'FAILED')),
    details       jsonb NOT NULL DEFAULT '{}',
    request_id    text NULL,
    chain_key     text NOT NULL,
    prev_hash     text NOT NULL,
    hash          text NOT NULL,
    UNIQUE (chain_key, prev_hash),
    UNIQUE (hash)
);
CREATE INDEX audit_event_company_idx ON audit_event (company_id, occurred_at DESC, id DESC);
CREATE INDEX audit_event_action_idx ON audit_event (action, occurred_at DESC, id DESC);
CREATE INDEX audit_event_actor_idx ON audit_event (actor_user_id);

-- Nombre y correo en el momento de actuar, fuera de la huella: suprimir a una persona borra su fila sin romper la cadena.
CREATE TABLE audit_actor (
    user_id      uuid PRIMARY KEY,
    display_name text NOT NULL,
    email        text NULL,
    updated_at   timestamptz NOT NULL
);

CREATE TABLE audit_setting (
    singleton              boolean PRIMARY KEY DEFAULT true CHECK (singleton),
    enabled                boolean NOT NULL DEFAULT false,
    usage_policy_reference text NULL,
    chat_enabled           boolean NOT NULL DEFAULT false,
    updated_by             uuid NULL,
    updated_at             timestamptz NULL
);
INSERT INTO audit_setting DEFAULT VALUES;

CREATE FUNCTION audit_event_immutable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'audit_event es de solo añadir (append-only)' USING ERRCODE = 'insufficient_privilege';
END $$;
CREATE TRIGGER audit_event_no_change BEFORE UPDATE OR DELETE ON audit_event
    FOR EACH ROW EXECUTE FUNCTION audit_event_immutable();
CREATE TRIGGER audit_event_no_truncate BEFORE TRUNCATE ON audit_event
    FOR EACH STATEMENT EXECUTE FUNCTION audit_event_immutable();

CREATE FUNCTION audit_event_hash(e audit_event) RETURNS text LANGUAGE sql IMMUTABLE SET search_path = pg_catalog AS $$
    SELECT encode(sha256(convert_to(jsonb_build_array(e.id,
            to_char(e.occurred_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"'), e.group_id, e.company_id,
            e.project_id, e.actor_kind, e.actor_user_id, e.actor_role, e.channel, e.via, e.action, e.target_kind,
            e.target_id, e.target_label, e.outcome, e.details, e.request_id, e.chain_key, e.prev_hash)::text,
            'UTF8')), 'hex') $$;

-- Un solo lugar para el interruptor y para la cadena: lee la cabeza fuera de la RLS bajo el bloqueo de la cadena.
CREATE FUNCTION audit_append(p_company_id uuid, p_project_id uuid, p_actor_kind text, p_actor_user_id uuid,
        p_actor_role text, p_actor_name text, p_channel text, p_via text, p_action text, p_target_kind text,
        p_target_id text, p_target_label text, p_outcome text, p_details jsonb, p_request_id text)
    RETURNS bigint LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp AS $$
DECLARE
    e audit_event;
BEGIN
    IF NOT (SELECT enabled FROM audit_setting) THEN
        RETURN NULL;
    END IF;
    PERFORM pg_advisory_xact_lock(hashtext('odin.audit_event.main'));
    SELECT h.hash INTO e.prev_hash FROM audit_event h WHERE h.chain_key = 'main' ORDER BY h.id DESC LIMIT 1;
    e.id := nextval(pg_get_serial_sequence('audit_event', 'id'));
    e.occurred_at := clock_timestamp();
    e.group_id := (SELECT g.id FROM odin_group g);
    e.company_id := p_company_id;
    e.project_id := p_project_id;
    e.actor_kind := p_actor_kind;
    e.actor_user_id := p_actor_user_id;
    e.actor_role := p_actor_role;
    e.channel := p_channel;
    e.via := p_via;
    e.action := p_action;
    e.target_kind := p_target_kind;
    e.target_id := p_target_id;
    e.target_label := p_target_label;
    e.outcome := p_outcome;
    e.details := coalesce(p_details, '{}');
    e.request_id := p_request_id;
    e.chain_key := 'main';
    e.prev_hash := coalesce(e.prev_hash, repeat('0', 64));
    e.hash := audit_event_hash(e);
    INSERT INTO audit_event OVERRIDING SYSTEM VALUE VALUES (e.*);
    IF p_actor_kind = 'PERSON' AND p_actor_user_id IS NOT NULL THEN
        INSERT INTO audit_actor (user_id, display_name, email, updated_at)
        VALUES (p_actor_user_id, coalesce(p_actor_name, ''), (SELECT u.email FROM app_user u WHERE u.id = p_actor_user_id), now())
        ON CONFLICT (user_id) DO UPDATE SET display_name = coalesce(p_actor_name, audit_actor.display_name),
            email = EXCLUDED.email, updated_at = EXCLUDED.updated_at;
    END IF;
    RETURN e.id;
END $$;

CREATE FUNCTION audit_chain_head() RETURNS TABLE (id bigint, hash text) LANGUAGE sql SECURITY DEFINER
    SET search_path = public, pg_temp AS $$
    SELECT e.id, e.hash FROM audit_event e WHERE e.chain_key = 'main' ORDER BY e.id DESC LIMIT 1 $$;

-- El primer id roto (huella que no sale de la fila o eslabón que no cuelga del anterior), o nulo si la cadena está sana.
CREATE FUNCTION audit_verify() RETURNS bigint LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp AS $$
DECLARE
    r audit_event;
    previous text := repeat('0', 64);
BEGIN
    FOR r IN SELECT * FROM audit_event WHERE chain_key = 'main' ORDER BY id LOOP
        IF r.prev_hash <> previous OR r.hash <> audit_event_hash(r) THEN
            RETURN r.id;
        END IF;
        previous := r.hash;
    END LOOP;
    RETURN NULL;
END $$;

-- Compara contra un sello guardado fuera de la base: si el dueño reescribió la cadena, la huella de ese id ya no coincide.
CREATE FUNCTION audit_seal_matches(p_id bigint, p_hash text) RETURNS boolean LANGUAGE sql SECURITY DEFINER
    SET search_path = public, pg_temp AS $$
    SELECT EXISTS (SELECT 1 FROM audit_event e WHERE e.id = p_id AND e.hash = p_hash) $$;

-- La única vía para cambiar el interruptor: la app no tiene UPDATE sobre audit_setting.
CREATE FUNCTION audit_update_settings(p_enabled boolean, p_reference text, p_chat_enabled boolean, p_user uuid)
    RETURNS void LANGUAGE sql SECURITY DEFINER SET search_path = public, pg_temp AS $$
    UPDATE audit_setting SET enabled = p_enabled, usage_policy_reference = p_reference,
        chat_enabled = p_chat_enabled, updated_by = p_user, updated_at = now() $$;

ALTER TABLE audit_event ENABLE ROW LEVEL SECURITY;
CREATE POLICY audit_event_scope ON audit_event FOR SELECT
    USING (current_setting('app.group_scope', true) = 'on' OR company_id = ANY (string_to_array(nullif(current_setting('app.company_ids', true), ''), ',')::uuid[]));

ALTER TABLE audit_actor ENABLE ROW LEVEL SECURITY;
CREATE POLICY audit_actor_scope ON audit_actor FOR SELECT
    USING (current_setting('app.group_scope', true) = 'on'
        OR EXISTS (SELECT 1 FROM audit_event e WHERE e.actor_user_id = audit_actor.user_id));

ALTER TABLE audit_setting ENABLE ROW LEVEL SECURITY;
CREATE POLICY audit_setting_scope ON audit_setting FOR SELECT USING (current_setting('app.group_scope', true) = 'on');

REVOKE ALL ON audit_event, audit_actor, audit_setting FROM PUBLIC, odin_app;
GRANT SELECT ON audit_event, audit_actor, audit_setting TO odin_app;

REVOKE ALL ON FUNCTION audit_event_hash(audit_event), audit_event_immutable(), audit_append(uuid, uuid, text, uuid, text, text, text, text, text, text, text, text, text, jsonb, text),
    audit_chain_head(), audit_verify(), audit_seal_matches(bigint, text), audit_update_settings(boolean, text, boolean, uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION audit_append(uuid, uuid, text, uuid, text, text, text, text, text, text, text, text, text, jsonb, text),
    audit_chain_head(), audit_verify(), audit_seal_matches(bigint, text), audit_update_settings(boolean, text, boolean, uuid) TO odin_app;

-- La consulta del panel ordena siempre por fecha e id.
CREATE INDEX audit_event_time_idx ON audit_event (occurred_at DESC, id DESC);
