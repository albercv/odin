-- Fase 2, bloque 1: carril del lector OCR.
-- Los documentos que ya esperan al lector llevan los intentos del carril local; el del lector empieza de cero
-- (el estado nuevo, READER_PROCESSING, es texto: la columna status no tiene restricción).
UPDATE document SET attempt_count = 0 WHERE status = 'PENDING_READER';

-- Páginas de un PDF mixto que quedaron sin texto (solo espacios o saltos de línea, como strip() en Java); el panel las avisa sin volver a procesar el documento.
ALTER TABLE document ADD COLUMN pages_without_text integer[] NOT NULL DEFAULT '{}';

UPDATE document d SET pages_without_text = blank.numbers
FROM (SELECT p.document_id, array_agg(p.number ORDER BY p.number) AS numbers
      FROM document_page p WHERE p.text ~ '^\s*$' GROUP BY p.document_id) blank
WHERE d.id = blank.document_id AND d.status = 'READY' AND d.paginated;
