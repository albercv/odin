-- Fase 2, bloque 2: casos parecidos. La marca de ficha de caso la pone una persona (el administrador), nunca la IA.
-- Los documentos que ya existen quedan sin marca. Las políticas de V9 cubren las columnas nuevas.
ALTER TABLE document ADD COLUMN case_document boolean NOT NULL DEFAULT false;
-- Con qué herramienta se hizo la pregunta registrada: la búsqueda normal o los casos parecidos.
ALTER TABLE question_record ADD COLUMN kind text NOT NULL DEFAULT 'SEARCH';
-- Las empresas que ya existen reciben la capacidad nueva, igual que las que se creen desde ahora.
INSERT INTO company_capability (company_id, capability) SELECT id, 'SIMILAR_CASES' FROM company ON CONFLICT DO NOTHING;
