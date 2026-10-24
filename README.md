# Filmaê — back-end

API principal do app. O front chama só este serviço. Ele autentica via fachada HTTP no `auth-microservice` e responde no envelope `{data, meta}` / `{error}`.

Rotas em `/api/v1/...`. Sem prefixo extra além do que estiver em `EXPO_PUBLIC_API_URL`.

## Pré-requisitos

- Docker e Docker Compose (caminho recomendado)
- Ou, para rodar fora do Docker: Java 21, Maven 3.9+, Postgres 16 na porta 5432
- O repositório `auth-microservice` no diretório irmão `../auth-microservice` (o compose faz o build dele)

## Forma recomendada: stack completa

Sobe Postgres (`filmae_db` + `auth_service_db`), auth na **8081** e este back-end na **8080**.

```bash
cp .env.example .env
# JWT_SECRET precisa ter pelo menos 64 bytes (HS512). O example já tem um valor de dev.
docker compose up --build
```

| Serviço | URL |
|---|---|
| Back-end | http://localhost:8080 |
| Health | http://localhost:8080/actuator/health |
| Swagger | http://localhost:8080/swagger-ui/index.html |
| Auth (interno) | http://localhost:8081 |
| Postgres | `localhost:5432` |

No perfil `dev` o Flyway aplica o seed de filmes (`db/seed`) para testar sem o ETL.

Não suba ao mesmo tempo o `docker-compose.yml` do `auth-microservice`: os dois querem a porta **5432** (e o auth sozinho ainda usa **8080**).

Parar:

```bash
docker compose down
```

## Rodando o back-end no host (Maven)

1. Postgres e auth já precisam estar no ar (compose acima, ou auth + banco manuais).
2. Confira no `.env` / ambiente:

```bash
export JWT_SECRET='o-mesmo-segredo-usado-no-auth-com-64-bytes-ou-mais'
export DATABASE_URL=jdbc:postgresql://localhost:5432/filmae_db
export DATABASE_USERNAME=filmae_user
export DATABASE_PASSWORD=filmae_password
export AUTH_SERVICE_BASE_URL=http://localhost:8081
```

3. Rode:

```bash
mvn spring-boot:run
```

O perfil padrão é `dev`.

## Testes

```bash
mvn test                  # unitários + integração (Testcontainers; precisa de Docker)
mvn -q -DskipTests package
```

## O que este serviço não faz

- Login Google: `POST /api/v1/auth/google` responde 503 até o auth ter o endpoint.
- Arcade / Pipocai: fora do contrato atual.
- O front deve apontar para `http://localhost:8080/api/v1`, não para a 8081.
