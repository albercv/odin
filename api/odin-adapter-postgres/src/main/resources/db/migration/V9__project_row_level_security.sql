-- Fase 2a: segunda barrera por proyecto (D-038). Además de la empresa, solo se ve lo que no tiene proyecto,
-- lo de los proyectos de app.project_ids o todo si app.all_projects = 'on'.
DROP POLICY document_scope ON document;
CREATE POLICY document_scope ON document
    USING ((current_setting('app.group_scope', true) = 'on' OR company_id = ANY (string_to_array(nullif(current_setting('app.company_ids', true), ''), ',')::uuid[]))
        AND (project_id IS NULL OR current_setting('app.all_projects', true) = 'on'
            OR project_id = ANY (string_to_array(nullif(current_setting('app.project_ids', true), ''), ',')::uuid[])));

DROP POLICY knowledge_card_scope ON knowledge_card;
CREATE POLICY knowledge_card_scope ON knowledge_card
    USING ((current_setting('app.group_scope', true) = 'on' OR company_id = ANY (string_to_array(nullif(current_setting('app.company_ids', true), ''), ',')::uuid[]))
        AND (project_id IS NULL OR current_setting('app.all_projects', true) = 'on'
            OR project_id = ANY (string_to_array(nullif(current_setting('app.project_ids', true), ''), ',')::uuid[])));

-- La página toma el proyecto de su documento.
DROP POLICY document_page_scope ON document_page;
CREATE POLICY document_page_scope ON document_page
    USING ((current_setting('app.group_scope', true) = 'on' OR company_id = ANY (string_to_array(nullif(current_setting('app.company_ids', true), ''), ',')::uuid[]))
        AND EXISTS (SELECT 1 FROM document d WHERE d.id = document_page.document_id
            AND (d.project_id IS NULL OR current_setting('app.all_projects', true) = 'on'
                OR d.project_id = ANY (string_to_array(nullif(current_setting('app.project_ids', true), ''), ',')::uuid[]))));

-- El miembro toma la empresa de su proyecto.
ALTER TABLE project_member ENABLE ROW LEVEL SECURITY;
CREATE POLICY project_member_scope ON project_member
    USING (EXISTS (SELECT 1 FROM project p WHERE p.id = project_member.project_id
        AND (current_setting('app.group_scope', true) = 'on'
            OR p.company_id = ANY (string_to_array(nullif(current_setting('app.company_ids', true), ''), ',')::uuid[]))));
