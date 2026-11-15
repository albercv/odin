-- Cómo llama cada empresa a sus proyectos. Las tres columnas o ninguna; nulo significa «proyectos».
ALTER TABLE company
    ADD COLUMN project_label_singular text,
    ADD COLUMN project_label_plural text,
    ADD COLUMN project_label_feminine boolean,
    ADD CONSTRAINT company_project_label_all_or_none CHECK (
        (project_label_singular IS NULL AND project_label_plural IS NULL AND project_label_feminine IS NULL)
        OR (project_label_singular IS NOT NULL AND project_label_plural IS NOT NULL
            AND project_label_feminine IS NOT NULL));
