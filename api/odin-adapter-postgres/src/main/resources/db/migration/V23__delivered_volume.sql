-- Fase 2, bloque 4: volumen que Odín entrega a la IA del trabajador, por empresa, mes y herramienta. Nunca por persona.
-- month es el primer día del mes en la zona de la instalación.
CREATE TABLE delivery_usage (
    company_id uuid NOT NULL REFERENCES company (id),
    month      date NOT NULL,
    tool       text NOT NULL,
    calls      bigint NOT NULL DEFAULT 0,
    bytes      bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (company_id, month, tool)
);

GRANT SELECT, INSERT, UPDATE, DELETE ON delivery_usage TO odin_app;

ALTER TABLE delivery_usage ENABLE ROW LEVEL SECURITY;
CREATE POLICY delivery_usage_scope ON delivery_usage
    USING (current_setting('app.group_scope', true) = 'on' OR company_id = ANY (string_to_array(nullif(current_setting('app.company_ids', true), ''), ',')::uuid[]));
