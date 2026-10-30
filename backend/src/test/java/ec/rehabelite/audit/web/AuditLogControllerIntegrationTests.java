package ec.rehabelite.audit.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ec.rehabelite.AbstractIntegrationTest;
import ec.rehabelite.auth.Role;
import ec.rehabelite.auth.application.JwtTokenService;
import ec.rehabelite.auth.domain.User;
import ec.rehabelite.auth.domain.UserRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * HU-18: GET /api/audit-logs, read-only, for the administrator and the operations director, with filters by user,
 * action, entity and date range (days in Quito, both included).
 */
@AutoConfigureMockMvc
class AuditLogControllerIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenService tokenService;

    @Autowired
    private JdbcTemplate jdbc;

    private User admin;
    private User valeria;
    private User mateo;
    /** Entity type no other test uses, so each test sees only its own entries. */
    private String entity;
    private Long newestId;

    @BeforeEach
    void setUp() {
        admin = createUser(Role.ADMINISTRATOR, "Administrador de prueba");
        valeria = createUser(Role.PHYSIOTHERAPIST, "Lcda. Valeria Andrade");
        mateo = createUser(Role.PHYSIOTHERAPIST, "Lcdo. Mateo Ríos");
        entity = "Prueba" + randomLetters();
        // 10 March 10:00 and 23:30 in Quito (the second is already 11 March in UTC), 12 and 13 March
        insert(valeria.getId(), "READ", 1L, "2026-03-10T15:00:00Z");
        insert(valeria.getId(), "UPDATE", 2L, "2026-03-11T04:30:00Z");
        insert(mateo.getId(), "STATUS_CHANGE", 2L, "2026-03-12T15:00:00Z");
        newestId = insert(null, "ACCESS_DENIED", null, "2026-03-13T15:00:00Z");
    }

    @Test
    void listsTheEntriesNewestFirstWithTheUserNamesAndOnlyMetadata() throws Exception {
        search(admin, "entityType", entity)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.content[*].action")
                        .value(contains("ACCESS_DENIED", "STATUS_CHANGE", "UPDATE", "READ")))
                .andExpect(jsonPath("$.content[0].id").value(newestId))
                .andExpect(jsonPath("$.content[0].userId").doesNotExist())
                .andExpect(jsonPath("$.content[0].userName").doesNotExist())
                .andExpect(jsonPath("$.content[1].userName").value("Lcdo. Mateo Ríos"))
                .andExpect(jsonPath("$.content[3].userId").value(valeria.getId()))
                .andExpect(jsonPath("$.content[3].userName").value("Lcda. Valeria Andrade"))
                .andExpect(jsonPath("$.content[3].entityType").value(entity))
                .andExpect(jsonPath("$.content[3].entityId").value(1))
                .andExpect(jsonPath("$.content[3].ipAddress").value("190.152.10.4"))
                .andExpect(jsonPath("$.content[3].occurredAt").value("2026-03-10T15:00:00Z"))
                .andExpect(jsonPath("$.content[3].length()").value(8));
    }

    @Test
    void filtersByUserActionAndEntityId() throws Exception {
        search(admin, "entityType", entity, "userId", valeria.getId().toString())
                .andExpect(jsonPath("$.content[*].action").value(contains("UPDATE", "READ")));
        search(admin, "entityType", entity, "action", "STATUS_CHANGE")
                .andExpect(jsonPath("$.content[*].userId").value(contains(mateo.getId().intValue())));
        search(admin, "entityType", entity, "entityId", "2")
                .andExpect(jsonPath("$.content[*].action").value(contains("STATUS_CHANGE", "UPDATE")));
        search(admin, "entityType", entity, "userId", valeria.getId().toString(), "action", "STATUS_CHANGE")
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void theDateRangeUsesWholeDaysInQuitoWithBothEndsIncluded() throws Exception {
        // 23:30 in Quito on 10 March belongs to that day although it is 11 March in UTC
        search(admin, "entityType", entity, "from", "2026-03-10", "to", "2026-03-10")
                .andExpect(jsonPath("$.content[*].action").value(contains("UPDATE", "READ")));
        search(admin, "entityType", entity, "from", "2026-03-11")
                .andExpect(jsonPath("$.content[*].action").value(contains("ACCESS_DENIED", "STATUS_CHANGE")));
        search(admin, "entityType", entity, "to", "2026-03-12")
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void pagesTheResults() throws Exception {
        search(admin, "entityType", entity, "page", "1", "size", "3")
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(3))
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content[*].action").value(contains("READ")));
    }

    @Test
    void theOperationsDirectorAlsoReadsTheLog() throws Exception {
        User director = createUser(Role.OPERATIONS_DIRECTOR, "Dirección de prueba");

        search(director, "entityType", entity).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4));
        perform(director, get("/api/audit-logs/entity-types")).andExpect(status().isOk())
                .andExpect(jsonPath("$").value(hasItem(entity)));
        perform(director, get("/api/audit-logs/users")).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == %d)].fullName".formatted(mateo.getId()))
                        .value(contains("Lcdo. Mateo Ríos")));
    }

    @Test
    void invalidFiltersAre400() throws Exception {
        search(admin, "from", "2026-03-12", "to", "2026-03-10")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("La fecha inicial no puede ser posterior a la fecha final."));
        search(admin, "size", "0").andExpect(status().isBadRequest());
        search(admin, "size", "101").andExpect(status().isBadRequest());
        search(admin, "action", "DELETE").andExpect(status().isBadRequest());
        search(admin, "from", "10/03/2026").andExpect(status().isBadRequest());
    }

    @Test
    void onlyTheAdministratorAndTheDirectorReadTheLog() throws Exception {
        for (Role role : List.of(Role.PHYSIOTHERAPIST, Role.PATIENT)) {
            User user = createUser(role, "Usuario de prueba");
            search(user, "entityType", entity).andExpect(status().isForbidden());
            perform(user, get("/api/audit-logs/users")).andExpect(status().isForbidden());
            perform(user, get("/api/audit-logs/entity-types")).andExpect(status().isForbidden());
        }
        mockMvc.perform(get("/api/audit-logs")).andExpect(status().isUnauthorized());
    }

    @Test
    void theLogIsReadOnlyThroughTheApi() throws Exception {
        // The collection only answers GET; a single entry is not even a resource of the API
        perform(admin, post("/api/audit-logs")).andExpect(status().isMethodNotAllowed());
        perform(admin, put("/api/audit-logs")).andExpect(status().isMethodNotAllowed());
        perform(admin, delete("/api/audit-logs")).andExpect(status().isMethodNotAllowed());
        perform(admin, put("/api/audit-logs/{id}", newestId)).andExpect(status().isNotFound());
        perform(admin, delete("/api/audit-logs/{id}", newestId)).andExpect(status().isNotFound());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE entity_type = ?", Integer.class,
                entity)).isEqualTo(4);
    }

    @Test
    void readingTheLogIsNotAudited() throws Exception {
        search(admin, "entityType", entity).andExpect(status().isOk());
        perform(admin, get("/api/audit-logs/users")).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE user_id = ?", Integer.class,
                admin.getId())).isZero();
    }

    @Test
    void openApiDocumentsTheAuditLog() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/audit-logs'].get.tags[0]").value("Auditoría"))
                .andExpect(jsonPath("$.paths['/api/audit-logs'].get.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/api/audit-logs'].put").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/audit-logs/users'].get").exists())
                .andExpect(jsonPath("$.paths['/api/audit-logs/entity-types'].get").exists());
    }

    private ResultActions search(User user, String... params) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/audit-logs");
        for (int i = 0; i < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
        return perform(user, request);
    }

    private ResultActions perform(User user, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION,
                "Bearer " + tokenService.issue(user, Instant.now()).token()));
    }

    private Long insert(Long userId, String action, Long entityId, String occurredAt) {
        return jdbc.queryForObject("""
                INSERT INTO audit_logs (user_id, action, entity_type, entity_id, occurred_at, ip_address)
                VALUES (?, ?, ?, ?, ?, '190.152.10.4') RETURNING id""", Long.class,
                userId, action, entity, entityId, Timestamp.from(Instant.parse(occurredAt)));
    }

    private User createUser(Role role, String fullName) {
        return userRepository.save(User.create("bitacora-" + UUID.randomUUID() + "@rehabelite.test",
                "not-a-real-hash", fullName, role, null, Instant.now()));
    }

    private static String randomLetters() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        StringBuilder letters = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            letters.append((char) ('a' + random.nextInt(26)));
        }
        return letters.toString();
    }

}
