package ec.rehabelite.auth.config;

import ec.rehabelite.auth.Role;
import java.util.List;
import org.springframework.http.HttpMethod;

/**
 * Which roles may call each API area. Requests to an area by any other role get 403 (audited as
 * ACCESS_DENIED); other authenticated endpoints (e.g. /api/auth/**) are open to every role.
 */
final class RoleAccessRules {

    /** A null method means every HTTP method. */
    record Rule(HttpMethod method, String pathPattern, List<Role> roles) {

        Rule(String pathPattern, Role... roles) {
            this(null, pathPattern, List.of(roles));
        }

        Rule(HttpMethod method, String pathPattern, Role... roles) {
            this(method, pathPattern, List.of(roles));
        }

        String[] roleNames() {
            return roles.stream().map(Role::name).toArray(String[]::new);
        }

    }

    static final List<Rule> RULES = List.of(
            // User and system administration (HU-02, HU-03)
            new Rule("/api/admin/**", Role.ADMINISTRATOR),
            new Rule("/api/users/**", Role.ADMINISTRATOR),
            // People registry (MEJ-02): staff records and the unified list of patients and staff
            new Rule("/api/people/**", Role.ADMINISTRATOR),
            new Rule("/api/staff/**", Role.ADMINISTRATOR),
            // Portal account of a patient, created from its record (MEJ-02).
            // More specific than /api/patients/**, so it must stay before it (first match wins)
            new Rule("/api/patients/*/portal-account", Role.ADMINISTRATOR),
            // A patient's clinical record is clinical data (HU-07): physiotherapists only, before /api/patients/**
            new Rule("/api/patients/*/clinical-record", Role.PHYSIOTHERAPIST),
            // Only the administrator registers (HU-05) and updates (HU-06) patients; also before /api/patients/**
            new Rule(HttpMethod.POST, "/api/patients", Role.ADMINISTRATOR),
            new Rule(HttpMethod.PUT, "/api/patients/**", Role.ADMINISTRATOR),
            // Personal data: registered by the administrator, looked up by physiotherapists (HU-05, HU-06)
            new Rule("/api/patients/**", Role.ADMINISTRATOR, Role.PHYSIOTHERAPIST),
            // Clinical data: physiotherapists only; the administrator never sees it (HU-06, HU-09)
            new Rule("/api/clinical-records/**", Role.PHYSIOTHERAPIST),
            new Rule("/api/icd10-diagnoses/**", Role.PHYSIOTHERAPIST),
            // Patient list with the clinical summary of each one (HU-07)
            new Rule("/api/physio/**", Role.PHYSIOTHERAPIST),
            new Rule("/api/treatments/**", Role.PHYSIOTHERAPIST),
            new Rule("/api/therapy-sessions/**", Role.PHYSIOTHERAPIST),
            new Rule("/api/ai-summaries/**", Role.PHYSIOTHERAPIST),
            // Operations reports carry clinical data, which the administrator never sees (HU-06, HU-14)
            new Rule("/api/reports/**", Role.OPERATIONS_DIRECTOR),
            // Audit log queries: metadata only (HU-18)
            new Rule("/api/audit-logs/**", Role.OPERATIONS_DIRECTOR, Role.ADMINISTRATOR),
            // Read-only patient portal
            new Rule("/api/portal/**", Role.PATIENT));

    private RoleAccessRules() {
    }

}
