package ec.rehabelite.clinicalrecords.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface Icd10DiagnosisRepository extends JpaRepository<Icd10Diagnosis, String> {

    /**
     * Catalog entries whose code starts with the query (case-insensitive) or whose description contains it ignoring
     * accents and case (normalize_text); an empty query lists the catalog. Ordered by code.
     */
    @Query(nativeQuery = true, value = """
            SELECT d.* FROM icd10_diagnoses d
            WHERE upper(d.code) LIKE upper(:query) || '%'
               OR normalize_text(d.description) LIKE '%' || normalize_text(:query) || '%'
            ORDER BY d.code
            LIMIT :limit""")
    List<Icd10Diagnosis> search(@Param("query") String query, @Param("limit") int limit);

}
