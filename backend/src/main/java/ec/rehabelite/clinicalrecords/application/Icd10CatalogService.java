package ec.rehabelite.clinicalrecords.application;

import ec.rehabelite.clinicalrecords.domain.Icd10Diagnosis;
import ec.rehabelite.clinicalrecords.domain.Icd10DiagnosisRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Search in the ICD-10 catalog, to choose the diagnosis of a treatment (HU-07). */
@Service
@RequiredArgsConstructor
public class Icd10CatalogService {

    static final int MAX_RESULTS = 20;

    private final Icd10DiagnosisRepository diagnosisRepository;

    /** Up to 20 entries whose code starts with the query or whose description contains it, ignoring accents. */
    @Transactional(readOnly = true)
    public List<Icd10Diagnosis> search(String query) {
        return diagnosisRepository.search(sanitize(query), MAX_RESULTS);
    }

    /** LIKE wildcards typed by the user are removed so they match literally nothing special. */
    private static String sanitize(String query) {
        return query == null ? "" : query.replaceAll("[%_\\\\]", "").strip();
    }

}
