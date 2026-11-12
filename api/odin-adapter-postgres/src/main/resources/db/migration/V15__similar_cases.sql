-- Fase 2, bloque 2: casos parecidos. La marca de ficha de caso la pone una persona (el administrador), nunca la IA.
-- Los documentos que ya existen quedan sin marca. Las políticas de V9 cubren las columnas nuevas.
ALTER TABLE document ADD COLUMN case_document boolean NOT NULL DEFAULT false;
-- Con qué herramienta se hizo la pregunta registrada: la búsqueda normal o los casos parecidos.
ALTER TABLE question_record ADD COLUMN kind text NOT NULL DEFAULT 'SEARCH' CHECK (kind IN ('SEARCH', 'SIMILAR_CASES'));
-- Las empresas que ya pueden buscar reciben la capacidad nueva: los casos usan lo que el trabajador ya puede leer.
-- Una empresa sin búsqueda no la recibe.
INSERT INTO company_capability (company_id, capability)
SELECT company_id, 'SIMILAR_CASES' FROM company_capability WHERE capability = 'SEARCH_KNOWLEDGE' ON CONFLICT DO NOTHING;
