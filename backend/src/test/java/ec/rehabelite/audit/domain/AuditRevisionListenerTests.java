package ec.rehabelite.audit.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

class AuditRevisionListenerTests {

    private final AuditRevisionListener listener = new AuditRevisionListener();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void theAuthorIsTheAuthenticatedUser() {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("42", null, "ROLE_PHYSIOTHERAPIST"));
        AuditRevision revision = new AuditRevision();

        listener.newRevision(revision);

        assertThat(revision.getUserId()).isEqualTo(42L);
    }

    @Test
    void withoutAnAuthenticatedUserTheRevisionHasNoAuthor() {
        AuditRevision startup = new AuditRevision();
        listener.newRevision(startup);

        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("key", "anonymous",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
        AuditRevision anonymous = new AuditRevision();
        listener.newRevision(anonymous);

        assertThat(startup.getUserId()).isNull();
        assertThat(anonymous.getUserId()).isNull();
    }

}
