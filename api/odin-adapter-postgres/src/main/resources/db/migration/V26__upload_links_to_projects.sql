-- Encargo 17: los enlaces de subida también pueden ir a un proyecto. La capacidad UPLOAD_DOCUMENTS no se siembra:
-- queda desactivada hasta que el administrador la active (D-106).

-- Hace falta la clave única para la clave foránea compuesta, como V11 hizo con document.
-- Otra migración crea la misma clave: solo se añade si falta, para que apliquen en cualquier orden.
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'project_id_company_unique') THEN
        ALTER TABLE project ADD CONSTRAINT project_id_company_unique UNIQUE (id, company_id);
    END IF;
END $$;

ALTER TABLE access_link ADD COLUMN project_id uuid NULL;
ALTER TABLE access_link ADD CONSTRAINT access_link_project_same_company_fk
    FOREIGN KEY (project_id, company_id) REFERENCES project (id, company_id);

-- Los enlaces existentes (de caso o de original) cumplen la regla nueva: no hay project_id en ninguno.
ALTER TABLE access_link DROP CONSTRAINT access_link_check;
ALTER TABLE access_link ADD CONSTRAINT access_link_target_check CHECK (
    (kind = 'UPLOAD' AND document_id IS NULL AND (case_document_id IS NULL) <> (project_id IS NULL))
    OR (kind = 'ORIGINAL' AND document_id IS NOT NULL AND case_document_id IS NULL AND project_id IS NULL));
