package com.enterprise.order;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Testcontainers: the order scenarios run against a throw-away PostgreSQL 16 in Docker. @ServiceConnection
 * points the datasource at the container. Skipped automatically on machines without Docker (runs in CI).
 */
@Testcontainers(disabledWithoutDocker = true)
class OrderPostgresIntegrationTest extends OrderPostgresScenarios {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");
}
