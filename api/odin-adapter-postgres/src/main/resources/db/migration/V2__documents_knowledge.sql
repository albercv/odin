CREATE TABLE document (
    seq                   bigint GENERATED ALWAYS AS IDENTITY,
    id                    uuid PRIMARY KEY,
    company_id            uuid NOT NULL REFERENCES company (id),
    project_id            uuid REFERENCES project (id),
    title                 text NOT NULL,
    filename              text NOT NULL,
    format                text NOT NULL,
    sha256                text NOT NULL,
    status                text NOT NULL,
    failure_reason        text,
    paginated             boolean NOT NULL,
    uploaded_by           uuid NOT NULL REFERENCES app_user (id),
    uploaded_at           timestamptz NOT NULL,
    processing_started_at timestamptz,
    attempt_count         integer NOT NULL,
    UNIQUE (company_id, sha256)
);
CREATE INDEX document_status_idx ON document (status, seq);

CREATE TABLE document_page (
    document_id    uuid NOT NULL REFERENCES document (id) ON DELETE CASCADE,
    company_id     uuid NOT NULL REFERENCES company (id),
    number         integer NOT NULL,
    text           text NOT NULL,
    reader_name    text NOT NULL,
    reader_version text NOT NULL,
    PRIMARY KEY (document_id, number)
);

CREATE TABLE knowledge_card (
    seq           bigint GENERATED ALWAYS AS IDENTITY,
    id            uuid PRIMARY KEY,
    company_id    uuid NOT NULL REFERENCES company (id),
    project_id    uuid REFERENCES project (id),
    title         text NOT NULL,
    body          text NOT NULL,
    original_body text,
    author_id     uuid NOT NULL REFERENCES app_user (id),
    created_at    timestamptz NOT NULL,
    status        text NOT NULL,
    reviewer_id   uuid REFERENCES app_user (id),
    decision      text,
    reviewed_at   timestamptz,
    review_note   text
);
CREATE INDEX knowledge_card_status_idx ON knowledge_card (status, seq);

CREATE TABLE question_record (
    id         uuid PRIMARY KEY,
    company_id uuid NOT NULL REFERENCES company (id),
    author_id  uuid NOT NULL REFERENCES app_user (id),
    text       text NOT NULL,
    hit_count  integer NOT NULL,
    asked_at   timestamptz NOT NULL
);
