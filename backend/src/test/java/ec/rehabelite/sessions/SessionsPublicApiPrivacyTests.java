package ec.rehabelite.sessions;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMember;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * HU-13: no public type of the sessions module carries the clinical observations nor the progress status. Other
 * modules (the patient portal) only see sessions through these types, so they cannot show a patient the internal
 * notes of the physiotherapy team, not even by mistake.
 */
class SessionsPublicApiPrivacyTests {

    private static final String PUBLIC_PACKAGE = "ec.rehabelite.sessions";
    private static final Pattern INTERNAL = Pattern.compile("observation|progress", Pattern.CASE_INSENSITIVE);

    /** The types of the root package of the module: its public API (web, application and domain are internal). */
    private final List<JavaClass> publicTypes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(PUBLIC_PACKAGE)
            .stream()
            .filter(type -> type.getPackageName().equals(PUBLIC_PACKAGE))
            .filter(type -> !type.getSimpleName().equals("package-info"))
            .toList();

    @Test
    void thePublicTypesAreTheOnesThisTestKnows() {
        assertThat(publicTypes).extracting(JavaClass::getSimpleName)
                .contains("PatientSessionView", "SessionSummary", "TherapySessionQueryPort");
    }

    @Test
    void noPublicTypeHasAMemberForTheClinicalObservationsNorTheProgressStatus() {
        assertThat(publicTypes)
                .flatExtracting(JavaClass::getMembers)
                .extracting(JavaMember::getFullName)
                .isNotEmpty()
                .noneMatch(member -> INTERNAL.matcher(member.substring(PUBLIC_PACKAGE.length())).find());
    }

    @Test
    void theEntityDoesHaveThoseFieldsSoTheCheckLooksForTheRightNames() {
        List<String> entityFields = new ClassFileImporter()
                .importPackages(PUBLIC_PACKAGE + ".domain")
                .get(PUBLIC_PACKAGE + ".domain.TherapySession")
                .getFields().stream().map(JavaMember::getName).toList();

        assertThat(entityFields).contains("clinicalObservations", "progressStatus")
                .anyMatch(field -> INTERNAL.matcher(field).find());
    }

}
