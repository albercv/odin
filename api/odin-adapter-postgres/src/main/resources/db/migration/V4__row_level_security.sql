DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'odin_app') THEN
        CREATE ROLE odin_app LOGIN NOBYPASSRLS PASSWORD '${appPassword}';
    END IF;
END
$$;

GRANT SELECT, INSERT, UPDATE, DELETE ON odin_group, company, company_capability, project, app_user, access_key,
    admin_session, document, document_page, knowledge_card, question_record TO odin_app;

ALTER TABLE company ENABLE ROW LEVEL SECURITY;
CREATE POLICY company_scope ON company
    USING (current_setting('app.group_scope', true) = 'on' OR id = ANY (string_to_array(nullif(current_setting('app.company_ids', true), ''), ',')::uuid[]));

ALTER TABLE project ENABLE ROW LEVEL SECURITY;
CREATE POLICY project_scope ON project
    USING (current_setting('app.group_scope', true) = 'on' OR company_id = ANY (string_to_array(nullif(current_setting('app.company_ids', true), ''), ',')::uuid[]));

ALTER TABLE document ENABLE ROW LEVEL SECURITY;
CREATE POLICY document_scope ON document
    USING (current_setting('app.group_scope', true) = 'on' OR company_id = ANY (string_to_array(nullif(current_setting('app.company_ids', true), ''), ',')::uuid[]));

ALTER TABLE document_page ENABLE ROW LEVEL SECURITY;
CREATE POLICY document_page_scope ON document_page
    USING (current_setting('app.group_scope', true) = 'on' OR company_id = ANY (string_to_array(nullif(current_setting('app.company_ids', true), ''), ',')::uuid[]));

ALTER TABLE knowledge_card ENABLE ROW LEVEL SECURITY;
CREATE POLICY knowledge_card_scope ON knowledge_card
    USING (current_setting('app.group_scope', true) = 'on' OR company_id = ANY (string_to_array(nullif(current_setting('app.company_ids', true), ''), ',')::uuid[]));

ALTER TABLE question_record ENABLE ROW LEVEL SECURITY;
CREATE POLICY question_record_scope ON question_record
    USING (current_setting('app.group_scope', true) = 'on' OR company_id = ANY (string_to_array(nullif(current_setting('app.company_ids', true), ''), ',')::uuid[]));
