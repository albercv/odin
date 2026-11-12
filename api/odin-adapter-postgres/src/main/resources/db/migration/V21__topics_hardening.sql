-- Fase 2, bloque 3: refuerzo del aislamiento de los temas (revisión de aislamiento). V13 ya está aplicada y no se toca.
-- Con claves compuestas, la base misma impide guardar un tema de otra empresa en un documento (D-014).
ALTER TABLE topic ADD CONSTRAINT topic_id_company_unique UNIQUE (id, company_id);

-- document (id, company_id) ya es único desde V11.
ALTER TABLE document_topic ADD CONSTRAINT document_topic_same_company_document_fk
    FOREIGN KEY (document_id, company_id) REFERENCES document (id, company_id) ON DELETE CASCADE;
ALTER TABLE document_topic ADD CONSTRAINT document_topic_same_company_topic_fk
    FOREIGN KEY (topic_id, company_id) REFERENCES topic (id, company_id);
ALTER TABLE document_tagging ADD CONSTRAINT document_tagging_same_company_document_fk
    FOREIGN KEY (document_id, company_id) REFERENCES document (id, company_id) ON DELETE CASCADE;

-- El borrado de un tema es lógico (deleted_at): la aplicación no necesita borrar filas de topic.
REVOKE DELETE ON topic FROM odin_app;
