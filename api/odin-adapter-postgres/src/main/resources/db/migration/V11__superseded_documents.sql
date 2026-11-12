-- Fase 2, bloque 7: un documento puede quedar sustituido por otro (D-049). La marca va en el documento viejo.
-- Las políticas de V9 ya cubren las columnas nuevas. Los documentos que ya existen quedan sin marca.
-- El sustituto es de la misma empresa: la FK compuesta lo exige en la base, además del caso de uso.
ALTER TABLE document ADD CONSTRAINT document_id_company_unique UNIQUE (id, company_id);
ALTER TABLE document ADD COLUMN superseded_by uuid NULL;
ALTER TABLE document ADD CONSTRAINT document_superseded_by_same_company_fk
    FOREIGN KEY (superseded_by, company_id) REFERENCES document (id, company_id);
ALTER TABLE document ADD COLUMN superseded_at timestamptz NULL;
ALTER TABLE document ADD CONSTRAINT document_not_superseded_by_itself CHECK (superseded_by <> id);
ALTER TABLE document ADD CONSTRAINT document_supersession_complete
    CHECK ((superseded_by IS NULL) = (superseded_at IS NULL));
CREATE INDEX document_superseded_by_idx ON document (superseded_by);
