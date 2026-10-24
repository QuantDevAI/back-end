-- Coleção e notas do usuário. Substitui usuario_filmes_assistidos/usuario_filmes_espera do SQL legado:
-- um registro por (usuário, filme) com os quatro estados independentes.

CREATE TABLE user_movie_states (
    user_id    BIGINT      NOT NULL,
    tconst     VARCHAR(20) NOT NULL,
    watchlist  BOOLEAN     NOT NULL DEFAULT FALSE,
    watched    BOOLEAN     NOT NULL DEFAULT FALSE,
    favorite   BOOLEAN     NOT NULL DEFAULT FALSE,
    rewatch    BOOLEAN     NOT NULL DEFAULT FALSE,
    watched_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, tconst),
    CONSTRAINT fk_user_movie_states_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_movie_states_filme FOREIGN KEY (tconst) REFERENCES filmes (tconst) ON DELETE CASCADE
);

CREATE INDEX idx_user_movie_states_watched_at ON user_movie_states (user_id, watched_at DESC) WHERE watched;
CREATE INDEX idx_user_movie_states_updated_at ON user_movie_states (user_id, updated_at DESC);

CREATE TABLE user_movie_ratings (
    user_id    BIGINT        NOT NULL,
    tconst     VARCHAR(20)   NOT NULL,
    rating     NUMERIC(2, 1) NOT NULL,
    rated_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, tconst),
    CONSTRAINT fk_user_movie_ratings_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_movie_ratings_filme FOREIGN KEY (tconst) REFERENCES filmes (tconst) ON DELETE CASCADE,
    CONSTRAINT ck_user_movie_ratings_scale CHECK (rating >= 0.5 AND rating <= 5.0 AND (rating * 2) = trunc(rating * 2))
);

CREATE INDEX idx_user_movie_ratings_user ON user_movie_ratings (user_id, rated_at DESC);
