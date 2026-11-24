package ec.rehabelite.staff.application;

import ec.rehabelite.auth.Role;
import ec.rehabelite.staff.domain.StaffPersonalData;

/** A new staff member: the role of its account, the national ID and the personal data (MEJ-02). */
public record RegisterStaffCommand(Role role, String nationalId, StaffPersonalData personalData) {

    /** Never print personal data, e.g. in logs or debugger views. */
    @Override
    public String toString() {
        return "RegisterStaffCommand[role=" + role + "]";
    }

}
