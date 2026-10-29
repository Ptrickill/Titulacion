package ec.rehabelite.auth.config;

import ec.rehabelite.auth.Role;
import java.util.List;

/**
 * Which roles may call each API area. Requests to an area by any other role get 403 (audited as
 * ACCESS_DENIED); other authenticated endpoints (e.g. /api/auth/**) are open to every role.
 */
final class RoleAccessRules {

    record Rule(String pathPattern, List<Role> roles) {

        Rule(String pathPattern, Role... roles) {
            this(pathPattern, List.of(roles));
        }

        String[] roleNames() {
            return roles.stream().map(Role::name).toArray(String[]::new);
        }

    }

    static final List<Rule> RULES = List.of(
            // User and system administration (HU-02, HU-03)
            new Rule("/api/admin/**", Role.ADMINISTRATOR),
            new Rule("/api/users/**", Role.ADMINISTRATOR),
            // Patients that can receive a portal account: only for account creation (HU-02).
            // More specific than /api/patients/**, so it must stay before it (first match wins)
            new Rule("/api/patients/portal-candidates", Role.ADMINISTRATOR),
            // Personal data: registered by the administrator, looked up by physiotherapists (HU-05, HU-06)
            new Rule("/api/patients/**", Role.ADMINISTRATOR, Role.PHYSIOTHERAPIST),
            // Clinical data: physiotherapists only; the administrator never sees it (HU-06, HU-09)
            new Rule("/api/clinical-records/**", Role.PHYSIOTHERAPIST),
            new Rule("/api/treatments/**", Role.PHYSIOTHERAPIST),
            new Rule("/api/therapy-sessions/**", Role.PHYSIOTHERAPIST),
            new Rule("/api/ai-summaries/**", Role.PHYSIOTHERAPIST),
            // Consolidated reports and audit log queries (HU-14, HU-18)
            new Rule("/api/reports/**", Role.OPERATIONS_DIRECTOR, Role.ADMINISTRATOR),
            new Rule("/api/audit-logs/**", Role.OPERATIONS_DIRECTOR, Role.ADMINISTRATOR),
            // Read-only patient portal
            new Rule("/api/portal/**", Role.PATIENT));

    private RoleAccessRules() {
    }

}
