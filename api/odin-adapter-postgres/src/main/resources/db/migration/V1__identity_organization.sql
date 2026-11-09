CREATE TABLE odin_group (
    id        uuid PRIMARY KEY,
    name      text NOT NULL,
    singleton boolean NOT NULL DEFAULT true UNIQUE CHECK (singleton)
);

CREATE TABLE company (
    seq  bigint GENERATED ALWAYS AS IDENTITY,
    id   uuid PRIMARY KEY,
    name text NOT NULL
);

CREATE TABLE company_capability (
    company_id uuid NOT NULL REFERENCES company (id) ON DELETE CASCADE,
    capability text NOT NULL,
    PRIMARY KEY (company_id, capability)
);

CREATE TABLE project (
    seq        bigint GENERATED ALWAYS AS IDENTITY,
    id         uuid PRIMARY KEY,
    company_id uuid NOT NULL REFERENCES company (id),
    name       text NOT NULL
);

CREATE TABLE app_user (
    seq           bigint GENERATED ALWAYS AS IDENTITY,
    id            uuid PRIMARY KEY,
    display_name  text NOT NULL,
    email         text,
    role          text NOT NULL,
    company_id    uuid REFERENCES company (id),
    active        boolean NOT NULL,
    password_hash text
);
CREATE UNIQUE INDEX app_user_email_key ON app_user (lower(email)) WHERE email IS NOT NULL;

CREATE TABLE access_key (
    seq         bigint GENERATED ALWAYS AS IDENTITY,
    id          uuid PRIMARY KEY,
    user_id     uuid NOT NULL REFERENCES app_user (id),
    label       text NOT NULL,
    prefix      text NOT NULL UNIQUE,
    secret_hash text NOT NULL,
    created_at  timestamptz NOT NULL,
    revoked_at  timestamptz
);

CREATE TABLE admin_session (
    token_hash   text PRIMARY KEY,
    user_id      uuid NOT NULL REFERENCES app_user (id),
    created_at   timestamptz NOT NULL,
    last_seen_at timestamptz NOT NULL
);
