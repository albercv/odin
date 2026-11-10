ALTER TABLE project ADD COLUMN deleted_at timestamptz;

CREATE UNIQUE INDEX project_company_name_key ON project (company_id, lower(name)) WHERE deleted_at IS NULL;
