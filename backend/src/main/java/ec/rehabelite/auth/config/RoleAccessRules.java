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
            // Reception area (HU-02, HU-03; the ADMINISTRATOR role is the reception since MEJ-05)
            new Rule("/api/admin/**", Role.ADMINISTRATOR),
            // Account actions (HU-03, MEJ-01): the reception on patient accounts, the operations director on staff
            // accounts; UserManagementService answers 403 for an account of the other side (MEJ-05)
            new Rule("/api/users/**", Role.ADMINISTRATOR, Role.OPERATIONS_DIRECTOR),
            // National ID and email lookup of both registration forms (MEJ-05), before /api/people/**
            new Rule("/api/people/matches", Role.ADMINISTRATOR, Role.OPERATIONS_DIRECTOR),
            // The reception's patient registry (MEJ-05): patients only
            new Rule("/api/people/**", Role.ADMINISTRATOR),
            // A physiotherapist's weekly schedule (MEJ-04, MEJ-05): the reception reads it, only the operations
            // director edits it; before /api/staff/**
            new Rule(HttpMethod.GET, "/api/staff/*/schedule", Role.ADMINISTRATOR, Role.OPERATIONS_DIRECTOR),
            // Staff management (MEJ-05): registration, records, accounts and schedules of the staff
            new Rule("/api/staff/**", Role.OPERATIONS_DIRECTOR),
            // Schedules board, availability and reassignment of treatments (MEJ-04): no clinical data
            new Rule("/api/operations/**", Role.OPERATIONS_DIRECTOR),
            // Erasure requests (MEJ-06): only the operations director accepts them
            new Rule("/api/data-requests/**", Role.OPERATIONS_DIRECTOR),
            // Portal account of a patient, created from its record (MEJ-02).
            // More specific than /api/patients/**, so it must stay before it (first match wins)
            new Rule("/api/patients/*/portal-account", Role.ADMINISTRATOR),
            // Consents, requests of the data subject and the export of the personal data (MEJ-06): the reception
            new Rule("/api/patients/*/consents/**", Role.ADMINISTRATOR),
            new Rule("/api/patients/*/data-requests/**", Role.ADMINISTRATOR),
            new Rule("/api/patients/*/data-export.*", Role.ADMINISTRATOR),
            // A patient's clinical record is clinical data (HU-07): physiotherapists only, also its export (MEJ-06);
            // before /api/patients/**
            new Rule("/api/patients/*/clinical-record/**", Role.PHYSIOTHERAPIST),
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
            // Audit log queries: metadata only (HU-18); the reception sees only the patients' entries (MEJ-05)
            new Rule("/api/audit-logs/**", Role.OPERATIONS_DIRECTOR, Role.ADMINISTRATOR),
            // Patient portal: read-only, except the patient's own consents (MEJ-06)
            new Rule("/api/portal/**", Role.PATIENT));

    private RoleAccessRules() {
    }

}
