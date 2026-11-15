-- Tipo de documento calculado al clasificar la primera carga: solo para proponer y enseñarlo en la bandeja (no es un
-- campo de Odín). Sin CHECK de valores: las reglas de tipo pueden crecer.
ALTER TABLE document ADD COLUMN intake_type text NULL;

-- Quién cerró la carga desde el panel; NULL es el cierre automático (sin nada pendiente).
ALTER TABLE intake_batch ADD COLUMN closed_by uuid NULL REFERENCES app_user (id);
ALTER TABLE intake_batch ADD CONSTRAINT intake_batch_closed_by_only_closed CHECK (closed_by IS NULL OR state = 'CLOSED');
