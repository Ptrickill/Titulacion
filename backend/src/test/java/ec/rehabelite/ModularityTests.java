package ec.rehabelite;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Verifies that modules only use each other through their public API and that there are no cycles.
 */
class ModularityTests {

    private final ApplicationModules modules = ApplicationModules.of(BackendApplication.class);

    @Test
    void verifiesModularStructure() {
        modules.verify();
    }

    @Test
    void writesModuleDocumentation() {
        new Documenter(modules).writeDocumentation();
    }

}
