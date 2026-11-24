package ec.rehabelite.patients;

import java.util.List;

/**
 * Patients of the people registry from an offset (MEJ-02), and how many match in total. The registry puts the staff
 * first and the patients after them, so a page can start anywhere among the patients, not only at a page boundary.
 */
public record PatientRegistrySlice(List<PatientRegistryEntry> content, long total) {
}
