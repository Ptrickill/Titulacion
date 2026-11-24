package ec.rehabelite.auth;

/**
 * Published inside the transaction that creates a staff account outside the people registry: the initial
 * administrator from {@code app.admin} (MEJ-02). The staff module listens synchronously, in the same transaction, and
 * creates its staff record with the data the account has, so no staff account exists without its record.
 */
public record StaffAccountCreated(Long userId, String email, String fullName, Role role) {

    /** Never print personal data, e.g. in logs or debugger views. */
    @Override
    public String toString() {
        return "StaffAccountCreated[userId=" + userId + ", role=" + role + "]";
    }

}
