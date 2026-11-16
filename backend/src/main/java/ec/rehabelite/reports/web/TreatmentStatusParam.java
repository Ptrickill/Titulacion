package ec.rehabelite.reports.web;

/**
 * Treatment status accepted by the filters of the reports (same values as treatments.status). Any other value is a
 * parameter of an invalid type: 400.
 */
enum TreatmentStatusParam {
    ACTIVE,
    COMPLETED,
    ABANDONED
}
