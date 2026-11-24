package ec.rehabelite.patients;

import java.util.Collection;

/**
 * Which patients of the people registry to list by their portal account (MEJ-02): any, only those without an
 * account, or only those whose account is among the given ids (e.g. the accounts with a status).
 */
public record PortalAccountFilter(boolean withoutAccount, Collection<Long> accountIds) {

    public static final PortalAccountFilter ANY = new PortalAccountFilter(false, null);

    public static PortalAccountFilter noAccount() {
        return new PortalAccountFilter(true, null);
    }

    public static PortalAccountFilter accounts(Collection<Long> accountIds) {
        return new PortalAccountFilter(false, accountIds);
    }

}
