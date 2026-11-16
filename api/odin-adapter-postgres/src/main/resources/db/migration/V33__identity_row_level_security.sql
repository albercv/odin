-- Encargo 26: RLS en las tablas de identidad. admin_chat_key pasa a tener RLS aunque la V30 dijera otra cosa:
-- su búsqueda por prefijo ya corre en ámbito de grupo, así que la política no la cambia.
-- WIP de pausa: sin ejecutar contra la base ni probar.

ALTER TABLE company_capability ENABLE ROW LEVEL SECURITY;
CREATE POLICY company_capability_scope ON company_capability
    USING (current_setting('app.group_scope', true) = 'on' OR company_id = ANY (string_to_array(nullif(current_setting('app.company_ids', true), ''), ',')::uuid[]));

ALTER TABLE access_key ENABLE ROW LEVEL SECURITY;
CREATE POLICY access_key_scope ON access_key
    USING (current_setting('app.group_scope', true) = 'on' OR company_id = ANY (string_to_array(nullif(current_setting('app.company_ids', true), ''), ',')::uuid[]));

ALTER TABLE admin_chat_key ENABLE ROW LEVEL SECURITY;
CREATE POLICY admin_chat_key_scope ON admin_chat_key
    USING (current_setting('app.group_scope', true) = 'on' OR company_id = ANY (string_to_array(nullif(current_setting('app.company_ids', true), ''), ',')::uuid[]));

ALTER TABLE app_user ENABLE ROW LEVEL SECURITY;
CREATE POLICY app_user_scope ON app_user
    USING (current_setting('app.group_scope', true) = 'on' OR group_admin
        OR EXISTS (SELECT 1 FROM membership m WHERE m.user_id = app_user.id));

ALTER TABLE admin_session ENABLE ROW LEVEL SECURITY;
CREATE POLICY admin_session_scope ON admin_session USING (current_setting('app.group_scope', true) = 'on');

ALTER TABLE odin_group ENABLE ROW LEVEL SECURITY;
CREATE POLICY odin_group_scope ON odin_group USING (current_setting('app.group_scope', true) = 'on');
