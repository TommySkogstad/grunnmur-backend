-- PostgreSQL-spesifikk DDL — H2 støtter ikke GENERATED ALWAYS AS IDENTITY, JSONB eller TIMESTAMPTZ
CREATE TABLE pg_test_table (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name       VARCHAR(100)  NOT NULL,
    metadata   JSONB,
    created_at TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX ON pg_test_table (created_at);
