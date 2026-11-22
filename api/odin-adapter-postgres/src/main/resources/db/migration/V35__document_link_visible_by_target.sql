-- Encargo 30, tarea 3: una salida tiene que poder decir «esto está corregido por un documento que no ves» sin dar
-- su id ni su título. Para eso la consulta lee el vínculo cuando se ve uno de sus dos extremos; el filtro explícito de
-- la consulta oculta el otro extremo y el texto de lo que no se ve. Solo lectura: escribir sigue pidiendo ver el origen.
CREATE POLICY document_link_visible_by_target ON document_link FOR SELECT
    USING ((current_setting('app.group_scope', true) = 'on' OR company_id = ANY (string_to_array(nullif(current_setting('app.company_ids', true), ''), ',')::uuid[]))
        AND (EXISTS (SELECT 1 FROM document d WHERE d.id = document_link.source_document_id)
            OR EXISTS (SELECT 1 FROM document d WHERE d.id = document_link.target_document_id)));
