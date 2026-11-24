/**
 * Assignments (MEJ-04).
 * <p>
 * The operations director's view of the physiotherapists' work: the weekly schedules board with each one's load, the
 * availability for a day and range of hours, the list of active treatments (no clinical content) and the
 * reassignment of their responsible. Uses staff, clinicalrecords, patients, auth and audit only through their public
 * interfaces; no module depends on assignments.
 * Only types in this root package are the module's public API; web, application and domain are internal.
 */
@ApplicationModule(displayName = "Assignments")
package ec.rehabelite.assignments;

import org.springframework.modulith.ApplicationModule;
