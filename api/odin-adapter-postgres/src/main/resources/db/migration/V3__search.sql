CREATE EXTENSION IF NOT EXISTS unaccent;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TEXT SEARCH CONFIGURATION odin_spanish (COPY = spanish);
ALTER TEXT SEARCH CONFIGURATION odin_spanish
    ALTER MAPPING FOR hword, hword_part, word WITH unaccent, spanish_stem;

ALTER TABLE document_page
    ADD COLUMN tsv tsvector GENERATED ALWAYS AS (to_tsvector('odin_spanish', text)) STORED;
CREATE INDEX document_page_tsv_idx ON document_page USING gin (tsv);
CREATE INDEX document_page_text_trgm_idx ON document_page USING gin (text gin_trgm_ops);

ALTER TABLE knowledge_card
    ADD COLUMN tsv tsvector GENERATED ALWAYS AS (to_tsvector('odin_spanish', title || ' ' || body)) STORED;
CREATE INDEX knowledge_card_tsv_idx ON knowledge_card USING gin (tsv);
CREATE INDEX knowledge_card_body_trgm_idx ON knowledge_card USING gin (body gin_trgm_ops);
