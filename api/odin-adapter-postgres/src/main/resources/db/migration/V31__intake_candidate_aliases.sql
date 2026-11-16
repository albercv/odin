-- Grafías normalizadas con las que un candidato también se busca en los textos: el nombre que tenía antes de
-- renombrarlo y los nombres de los candidatos que se juntaron con él.
ALTER TABLE intake_project_candidate ADD COLUMN aliases text[] NOT NULL DEFAULT '{}';

-- Juntar candidatos borra el que desaparece (la política de filas de V26 ya lo limita a la empresa y a la bandeja).
GRANT DELETE ON intake_project_candidate TO odin_app;
