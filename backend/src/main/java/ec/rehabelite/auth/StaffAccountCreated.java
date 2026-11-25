package ec.rehabelite.auth;

/**
 * Published inside the transaction that creates a staff account outside the registries: the initial operations
 * director from {@code app.initial-director} (MEJ-02, MEJ-05). The staff module listens synchronously, in the same transaction, and
 * creates its staff record with the data the account has, so no staff account exists without its record.
 */
public record StaffAccountCreated(Long userId, String email, String fullName, Role role) {

    /** Never print personal data, e.g. in logs or debugger views. */
    @Override
    public String toString() {
        return "StaffAccountCreated[userId=" + userId + ", role=" + role + "]";
    }

}
