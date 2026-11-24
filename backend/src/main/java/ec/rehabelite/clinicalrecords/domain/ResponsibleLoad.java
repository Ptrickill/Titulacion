package ec.rehabelite.clinicalrecords.domain;

/** Number of treatments of a current responsible physiotherapist (MEJ-04). */
public record ResponsibleLoad(Long physiotherapistId, Long treatments) {
}
