-- Catálogo IMDb. Nomes de tabelas e colunas iguais aos do ETL (grupo7_banco_filmes.sql),
-- adaptados para PostgreSQL: listas JSON viram text[] e "characters" vira jsonb.

CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE filmes (
    tconst          VARCHAR(20) PRIMARY KEY,
    title_type      VARCHAR(30) NOT NULL,
    primary_title   TEXT        NOT NULL,
    original_title  TEXT        NOT NULL,
    is_adult        BOOLEAN     NOT NULL DEFAULT FALSE,
    start_year      INTEGER     NULL,
    end_year        INTEGER     NULL,
    runtime_minutes INTEGER     NULL,
    genres          TEXT[]      NULL DEFAULT '{}'
);

CREATE TABLE pessoas (
    nconst             VARCHAR(20)  PRIMARY KEY,
    primary_name       VARCHAR(255) NOT NULL,
    birth_year         INTEGER      NULL,
    death_year         INTEGER      NULL,
    primary_profession TEXT[]       NULL DEFAULT '{}',
    known_for_titles   TEXT[]       NULL DEFAULT '{}'
);

CREATE TABLE avaliacoes_filmes (
    tconst         VARCHAR(20)  PRIMARY KEY,
    average_rating NUMERIC(3, 1) NOT NULL,
    num_votes      INTEGER      NOT NULL CHECK (num_votes >= 0),
    CONSTRAINT fk_avaliacoes_filme FOREIGN KEY (tconst) REFERENCES filmes (tconst) ON DELETE CASCADE
);

CREATE TABLE titulos_alternativos (
    title_id          VARCHAR(20) NOT NULL,
    ordering          INTEGER     NOT NULL,
    title             TEXT        NOT NULL,
    region            VARCHAR(10) NULL,
    language          VARCHAR(10) NULL,
    types             TEXT[]      NULL DEFAULT '{}',
    attributes        TEXT[]      NULL DEFAULT '{}',
    is_original_title BOOLEAN     NOT NULL DEFAULT FALSE,
    PRIMARY KEY (title_id, ordering),
    CONSTRAINT fk_akas_filme FOREIGN KEY (title_id) REFERENCES filmes (tconst) ON DELETE CASCADE
);

CREATE TABLE equipe_filme (
    tconst    VARCHAR(20) PRIMARY KEY,
    directors TEXT[]      NULL DEFAULT '{}',
    writers   TEXT[]      NULL DEFAULT '{}',
    CONSTRAINT fk_crew_filme FOREIGN KEY (tconst) REFERENCES filmes (tconst) ON DELETE CASCADE
);

CREATE TABLE episodios (
    tconst         VARCHAR(20) PRIMARY KEY,
    parent_tconst  VARCHAR(20) NOT NULL,
    season_number  INTEGER     NULL,
    episode_number INTEGER     NULL,
    CONSTRAINT fk_episode_filme FOREIGN KEY (tconst) REFERENCES filmes (tconst) ON DELETE CASCADE,
    CONSTRAINT fk_episode_parent FOREIGN KEY (parent_tconst) REFERENCES filmes (tconst) ON DELETE CASCADE
);

CREATE TABLE principais_filme (
    tconst     VARCHAR(20)  NOT NULL,
    ordering   INTEGER      NOT NULL,
    nconst     VARCHAR(20)  NOT NULL,
    category   VARCHAR(50)  NOT NULL,
    job        VARCHAR(255) NULL,
    characters JSONB        NULL,
    PRIMARY KEY (tconst, ordering),
    CONSTRAINT fk_principals_filme FOREIGN KEY (tconst) REFERENCES filmes (tconst) ON DELETE CASCADE,
    CONSTRAINT fk_principals_pessoa FOREIGN KEY (nconst) REFERENCES pessoas (nconst)
);

-- Busca e filtros
CREATE INDEX idx_filmes_genres ON filmes USING GIN (genres);
CREATE INDEX idx_filmes_primary_title_trgm ON filmes USING GIN (primary_title gin_trgm_ops);
CREATE INDEX idx_filmes_original_title_trgm ON filmes USING GIN (original_title gin_trgm_ops);
CREATE INDEX idx_filmes_start_year ON filmes (start_year);
CREATE INDEX idx_filmes_title_type ON filmes (title_type);
CREATE INDEX idx_avaliacoes_num_votes ON avaliacoes_filmes (num_votes DESC);
CREATE INDEX idx_avaliacoes_average_rating ON avaliacoes_filmes (average_rating DESC);
CREATE INDEX idx_akas_title_trgm ON titulos_alternativos USING GIN (title gin_trgm_ops);
CREATE INDEX idx_akas_region ON titulos_alternativos (title_id, region);
CREATE INDEX idx_pessoas_primary_name ON pessoas (primary_name);
CREATE INDEX idx_principals_nconst ON principais_filme (nconst);
CREATE INDEX idx_episode_parent ON episodios (parent_tconst);
