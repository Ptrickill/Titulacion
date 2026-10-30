package ec.rehabelite.audit.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class AuditLogFilterTests {

    @Test
    void aDayStartsAtMidnightInQuito() {
        // Quito is UTC-5 all year
        assertThat(AuditLogFilter.startOf(LocalDate.of(2026, 3, 10))).isEqualTo(Instant.parse("2026-03-10T05:00:00Z"));
        assertThat(AuditLogFilter.startOf(LocalDate.of(2026, 12, 31)))
                .isEqualTo(Instant.parse("2026-12-31T05:00:00Z"));
    }

    @Test
    void anEmptyFilterHasNoCriteria() {
        AuditLogFilter none = AuditLogFilter.none();

        assertThat(none.userId()).isNull();
        assertThat(none.action()).isNull();
        assertThat(none.entityType()).isNull();
        assertThat(none.from()).isNull();
        assertThat(none.toSpecification()).isNotNull();
    }

}
