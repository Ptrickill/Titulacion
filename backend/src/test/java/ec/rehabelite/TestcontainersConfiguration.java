package ec.rehabelite;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Used only by {@link TestBackendApplication} to run the app locally against a throwaway container.
 * Tests extend {@link AbstractIntegrationTest} instead.
 */
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(AbstractIntegrationTest.POSTGRES_IMAGE);
    }

}
