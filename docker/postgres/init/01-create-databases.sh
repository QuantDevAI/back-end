#!/bin/sh
# Executado só na primeira inicialização do volume: cria um banco e um usuário por serviço.
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    CREATE USER filmae_user WITH PASSWORD '${FILMAE_DB_PASSWORD}';
    CREATE DATABASE filmae_db OWNER filmae_user;

    CREATE USER auth_user WITH PASSWORD '${AUTH_DB_PASSWORD}';
    CREATE DATABASE auth_service_db OWNER auth_user;
EOSQL
