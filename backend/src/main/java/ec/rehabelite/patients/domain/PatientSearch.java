package ec.rehabelite.patients.domain;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Name or national ID search of patients (HU-06), implemented with native SQL in {@link PatientSearchImpl}. */
public interface PatientSearch {

    Page<Patient> search(String query, Pageable pageable);

    /** Same search and order from any offset, not only a page boundary (MEJ-02 people registry). */
    List<Patient> searchSlice(String query, long offset, int limit);

    /** How many patients match the search. */
    long countSearch(String query);

}
