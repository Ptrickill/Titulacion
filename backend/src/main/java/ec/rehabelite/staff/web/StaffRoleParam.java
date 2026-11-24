package ec.rehabelite.staff.web;

import ec.rehabelite.auth.Role;

/** Roles of the staff (MEJ-02): every role except PATIENT. Any other value answers 400. */
enum StaffRoleParam {
    ADMINISTRATOR(Role.ADMINISTRATOR),
    PHYSIOTHERAPIST(Role.PHYSIOTHERAPIST),
    OPERATIONS_DIRECTOR(Role.OPERATIONS_DIRECTOR);

    private final Role role;

    StaffRoleParam(Role role) {
        this.role = role;
    }

    Role role() {
        return role;
    }

}
