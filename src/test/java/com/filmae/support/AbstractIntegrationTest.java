package com.filmae.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.UUID;

/**
 * Base para testes de integração: contexto Spring completo com MockMvc e um Postgres 16 real via
 * Testcontainers ({@code @ServiceConnection}). O container é único para toda a suíte (padrão
 * singleton), assim o cache de contexto do Spring continua válido entre classes.
 *
 * <p>As migrations rodam sem o seed de dev (perfil {@code test}); cada teste cria os dados de que
 * precisa e pode limpar o estado dos usuários com {@link #cleanUserData()}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("filmae_test")
        .withUsername("filmae_user")
        .withPassword("filmae_password");

    static {
        POSTGRES.start();
    }

    protected final JwtTestUtils jwt = new JwtTestUtils();

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    /** Apaga usuários e tudo que depende deles (coleção, notas, onboarding, notificações). */
    protected void cleanUserData() {
        jdbcTemplate.execute("TRUNCATE TABLE users RESTART IDENTITY CASCADE");
    }

    /** Apaga o catálogo inteiro (filmes, pessoas e tabelas relacionadas). */
    protected void cleanCatalog() {
        jdbcTemplate.execute("TRUNCATE TABLE filmes, pessoas RESTART IDENTITY CASCADE");
    }

    /** Access token válido para um usuário novo e aleatório. */
    protected String newUserToken() {
        UUID id = UUID.randomUUID();
        return jwt.accessToken(id, "user-" + id + "@example.com", "Usuário Teste");
    }

    protected String bearer(String token) {
        return JwtTestUtils.bearer(token);
    }
}
