package ec.rehabelite;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for integration tests of every module: full Spring context, profile "test" and a real
 * PostgreSQL 16 started by Testcontainers (Docker must be running).
 * <p>
 * The container is a JVM-wide singleton: it starts once, is shared by all subclasses (which also share
 * the cached Spring context) and is removed by Testcontainers when the test run ends. Tests must therefore
 * not rely on an empty database; create the data they need with unique values.
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    /** Same major version as production and as docker-compose.yml. */
    public static final DockerImageName POSTGRES_IMAGE = DockerImageName.parse("postgres:16");

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(POSTGRES_IMAGE);

    static {
        POSTGRES.start();
    }

}
