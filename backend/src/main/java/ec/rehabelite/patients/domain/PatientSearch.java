package ec.rehabelite.patients.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Patient search (HU-06), implemented with native SQL in {@link PatientSearchImpl}. */
public interface PatientSearch {

    /**
     * Patients ordered by last name, first name and id. A query of digits matches the beginning of the national ID;
     * otherwise every word must appear in the name, ignoring accents and case. An empty query matches every
     * patient. The pageable's sort is ignored.
     */
    Page<Patient> search(String query, Pageable pageable);

}
