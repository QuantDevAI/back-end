-- Usuários locais do Filmaê. O id público é numérico (o front espera id: number);
-- auth_user_id liga ao "sub" (UUID) do JWT emitido pelo auth-microservice.

CREATE TABLE users (
    id                      BIGSERIAL    PRIMARY KEY,
    auth_user_id            UUID         NOT NULL,
    email                   VARCHAR(254) NOT NULL,
    name                    VARCHAR(150) NOT NULL,
    bio                     TEXT         NOT NULL DEFAULT '',
    onboarding_completed_at TIMESTAMPTZ  NULL,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_users_auth_user_id UNIQUE (auth_user_id)
);

CREATE INDEX idx_users_email ON users (lower(email));

CREATE TABLE user_preferences (
    user_id                           BIGINT      PRIMARY KEY,
    receive_recommendations_by_email  BOOLEAN     NOT NULL DEFAULT TRUE,
    spoiler_mode_enabled              BOOLEAN     NOT NULL DEFAULT FALSE,
    updated_at                        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_user_preferences_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE user_onboarding (
    user_id       BIGINT      PRIMARY KEY,
    genres        TEXT[]      NOT NULL DEFAULT '{}',
    profile_story TEXT        NULL,
    current_step  VARCHAR(20) NOT NULL DEFAULT 'genres',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_user_onboarding_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_user_onboarding_step CHECK (current_step IN ('genres', 'favoriteMovies', 'profileStory', 'complete'))
);

CREATE TABLE user_onboarding_favorite_movies (
    user_id    BIGINT      NOT NULL,
    tconst     VARCHAR(20) NOT NULL,
    position   INTEGER     NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, tconst),
    CONSTRAINT fk_onboarding_fav_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_onboarding_fav_filme FOREIGN KEY (tconst) REFERENCES filmes (tconst) ON DELETE CASCADE
);
