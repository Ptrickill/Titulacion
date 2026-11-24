/**
 * Staff (MEJ-02).
 * <p>
 * Personal data of the staff (physiotherapists, administration and operations director) and the people registry
 * ("Registro de Usuarios"): the unified list of patients and staff with the status of their accounts. A staff record
 * and its account are created together, so no account is created by typing a name or an email. Uses auth, patients,
 * catalogs and audit only through their public interfaces; no module depends on staff.
 * Only types in this root package are the module's public API; web, application and domain are internal.
 */
@ApplicationModule(displayName = "Staff")
package ec.rehabelite.staff;

import org.springframework.modulith.ApplicationModule;
