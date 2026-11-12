-- Fase 2, bloque 7: un documento puede quedar sustituido por otro (D-049). La marca va en el documento viejo.
-- Las políticas de V9 ya cubren las columnas nuevas. Los documentos que ya existen quedan sin marca.
ALTER TABLE document ADD COLUMN superseded_by uuid NULL REFERENCES document (id);
ALTER TABLE document ADD COLUMN superseded_at timestamptz NULL;
ALTER TABLE document ADD CONSTRAINT document_not_superseded_by_itself CHECK (superseded_by <> id);
ALTER TABLE document ADD CONSTRAINT document_supersession_complete
    CHECK ((superseded_by IS NULL) = (superseded_at IS NULL));
CREATE INDEX document_superseded_by_idx ON document (superseded_by);
