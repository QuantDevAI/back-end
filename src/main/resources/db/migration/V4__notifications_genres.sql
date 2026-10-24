-- Notificações do usuário. O id público exposto na API tem o formato "n_<id>".
CREATE TABLE notifications (
    id         BIGSERIAL    PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    type       VARCHAR(30)  NOT NULL,
    title      VARCHAR(200) NOT NULL,
    message    TEXT         NOT NULL,
    read       BOOLEAN      NOT NULL DEFAULT FALSE,
    -- Chave opcional para evitar duplicatas (ex.: "daily_recommendation:2026-10-24").
    dedupe_key VARCHAR(120) NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    read_at    TIMESTAMPTZ  NULL,
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_notifications_type CHECK (type IN ('collection', 'daily_recommendation', 'onboarding', 'social', 'system')),
    CONSTRAINT uk_notifications_dedupe UNIQUE (user_id, dedupe_key)
);

CREATE INDEX idx_notifications_user_created ON notifications (user_id, created_at DESC);
CREATE INDEX idx_notifications_user_unread ON notifications (user_id) WHERE NOT read;

-- Ponte entre os slugs locais do front (src/data/preferences.ts), os gêneros do IMDb
-- (filmes.genres) e o nome em português. "canonical" marca o slug principal de cada
-- gênero IMDb; aliases (ex.: suspense -> Thriller) têm canonical = false.
CREATE TABLE genre_catalog (
    slug       VARCHAR(40) PRIMARY KEY,
    imdb_name  VARCHAR(40) NOT NULL,
    name_pt    VARCHAR(60) NOT NULL,
    canonical  BOOLEAN     NOT NULL DEFAULT TRUE,
    sort_order INTEGER     NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_genre_catalog_imdb_canonical ON genre_catalog (imdb_name) WHERE canonical;
CREATE INDEX idx_genre_catalog_imdb_name ON genre_catalog (lower(imdb_name));

INSERT INTO genre_catalog (slug, imdb_name, name_pt, canonical, sort_order) VALUES
    -- Slugs usados no onboarding do front (mesma ordem de GENRES em preferences.ts)
    ('acao',              'Action',      'Ação',              TRUE,  10),
    ('aventura',          'Adventure',   'Aventura',          TRUE,  20),
    ('animacao',          'Animation',   'Animação',          TRUE,  30),
    ('comedia',           'Comedy',      'Comédia',           TRUE,  40),
    ('crime',             'Crime',       'Crime',             TRUE,  50),
    ('documentario',      'Documentary', 'Documentário',      TRUE,  60),
    ('drama',             'Drama',       'Drama',             TRUE,  70),
    ('fantasia',          'Fantasy',     'Fantasia',          TRUE,  80),
    ('ficcao-cientifica', 'Sci-Fi',      'Ficção Científica', TRUE,  90),
    ('terror',            'Horror',      'Terror',            TRUE, 100),
    ('romance',           'Romance',     'Romance',           TRUE, 110),
    ('suspense',          'Thriller',    'Suspense',          FALSE, 120),
    ('thriller',          'Thriller',    'Thriller',          TRUE, 130),
    ('misterio',          'Mystery',     'Mistério',          TRUE, 140),
    -- Demais gêneros do IMDb (aparecem no catálogo, filtros e dashboard)
    ('biografia',         'Biography',   'Biografia',         TRUE, 200),
    ('familia',           'Family',      'Família',           TRUE, 210),
    ('historia',          'History',     'História',          TRUE, 220),
    ('musica',            'Music',       'Música',            TRUE, 230),
    ('musical',           'Musical',     'Musical',           TRUE, 240),
    ('esporte',           'Sport',       'Esporte',           TRUE, 250),
    ('guerra',            'War',         'Guerra',            TRUE, 260),
    ('faroeste',          'Western',     'Faroeste',          TRUE, 270),
    ('noir',              'Film-Noir',   'Noir',              TRUE, 280),
    ('curta',             'Short',       'Curta-metragem',    TRUE, 290),
    ('noticias',          'News',        'Notícias',          TRUE, 300),
    ('reality',           'Reality-TV',  'Reality show',      TRUE, 310),
    ('talk-show',         'Talk-Show',   'Talk show',         TRUE, 320),
    ('game-show',         'Game-Show',   'Game show',         TRUE, 330);
