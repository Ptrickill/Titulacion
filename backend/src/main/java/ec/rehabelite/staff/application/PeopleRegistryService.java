package ec.rehabelite.staff.application;

import ec.rehabelite.auth.AccountProvisioning;
import ec.rehabelite.auth.AccountStatistics;
import ec.rehabelite.auth.AccountStatus;
import ec.rehabelite.auth.AccountSummary;
import ec.rehabelite.auth.Role;
import ec.rehabelite.patients.PatientDirectory;
import ec.rehabelite.patients.PatientRegistryEntry;
import ec.rehabelite.patients.PatientRegistrySlice;
import ec.rehabelite.patients.PortalAccountFilter;
import ec.rehabelite.shared.domain.InvalidRequestException;
import ec.rehabelite.staff.domain.StaffRecord;
import ec.rehabelite.staff.domain.StaffRecordRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The people registry ("Registro de Usuarios", MEJ-02): patients and staff in one list, with the status of each
 * account, and the lookup that tells the registration form whether a national ID or an email is already registered.
 * Personal data only: nothing here is audited.
 */
@Service
@RequiredArgsConstructor
public class PeopleRegistryService {

    static final String ROLE_ONLY_FOR_STAFF = "El filtro de rol solo aplica al personal.";

    private final StaffRecordRepository staffRepository;
    private final PatientDirectory patients;
    private final AccountProvisioning accounts;

    /** Filter by the status of the account: NONE is a person without account. */
    public enum AccountFilter {
        NONE,
        PENDING_ACTIVATION,
        ACTIVE,
        INACTIVE
    }

    /** Who a person is in the registry. */
    public enum PersonType {
        PATIENT,
        STAFF
    }

    /** A row of the registry; {@code account} is null for a patient without a portal account. */
    public record PersonEntry(PersonType type, Long id, String nationalId, String firstName, String lastName,
            Role role, String email, String phone, boolean needsCompletion, AccountSummary account) {
    }

    public record PeoplePage(List<PersonEntry> content, long total) {
    }

    /** A registered person that already has the national ID or the email looked up. */
    public record PersonMatch(PersonType type, Long id, String fullName, String field) {
    }

    /**
     * One page of the registry: the staff first and then the patients, each by last name and first name, so a page is
     * the concatenation of two searches with a fixed number of statements (staff count and slice, patient count and
     * slice, the accounts of the role or of the status when filtered, and the accounts of the page), whatever the page.
     *
     * @param type only patients or only staff; null for both
     * @param role only the staff with that role (it implies the staff); null for every role
     * @param accountStatus only the people whose account has that status, or without account (NONE: only patients,
     *        because every staff member has one); null for any
     * @throws InvalidRequestException if a role is combined with the patients
     */
    @Transactional(readOnly = true)
    public PeoplePage list(String query, PersonType type, Role role, AccountFilter accountStatus, int page,
            int size) {
        if (role != null && (type == PersonType.PATIENT || role == Role.PATIENT)) {
            throw new InvalidRequestException(ROLE_ONLY_FOR_STAFF);
        }
        boolean withStaff = type != PersonType.PATIENT && accountStatus != AccountFilter.NONE;
        boolean withPatients = type != PersonType.STAFF && role == null;
        long offset = (long) page * size;

        Collection<Long> statusAccounts = accountStatus == null || accountStatus == AccountFilter.NONE
                ? null
                : accounts.idsWithStatus(AccountStatus.valueOf(accountStatus.name()));
        Collection<Long> staffAccounts = intersection(role == null ? null : accounts.idsWithRole(role),
                statusAccounts);
        long staffTotal = withStaff ? staffRepository.countSearch(query, staffAccounts) : 0;
        List<StaffRecord> staff = withStaff && offset < staffTotal
                ? staffRepository.searchSlice(query, staffAccounts, offset, size)
                : List.of();
        PortalAccountFilter patientAccounts = accountStatus == AccountFilter.NONE
                ? PortalAccountFilter.noAccount()
                : statusAccounts == null ? PortalAccountFilter.ANY : PortalAccountFilter.accounts(statusAccounts);
        PatientRegistrySlice patientSlice = withPatients
                ? patients.registry(query, patientAccounts, Math.max(0, offset - staffTotal), size - staff.size())
                : new PatientRegistrySlice(List.of(), 0);

        List<Long> userIds = Stream.concat(staff.stream().map(StaffRecord::getUserId),
                patientSlice.content().stream().map(PatientRegistryEntry::portalUserId))
                .filter(Objects::nonNull).toList();
        Map<Long, AccountSummary> accountsById = accounts.summaries(userIds);

        List<PersonEntry> content = new ArrayList<>();
        staff.forEach(record -> {
            AccountSummary account = accountsById.get(record.getUserId());
            content.add(new PersonEntry(PersonType.STAFF, record.getId(), record.getNationalId(),
                    record.getFirstName(), record.getLastName(), account == null ? null : account.role(),
                    record.getEmail(), record.getPhone(), record.needsCompletion(), account));
        });
        patientSlice.content().forEach(patient -> content.add(new PersonEntry(PersonType.PATIENT, patient.id(),
                patient.nationalId(), patient.firstName(), patient.lastName(), Role.PATIENT, patient.email(),
                patient.phone(), patient.needsCompletion(),
                patient.portalUserId() == null ? null : accountsById.get(patient.portalUserId()))));
        return new PeoplePage(content, staffTotal + patientSlice.total());
    }

    /** The indicators of the registry: active accounts and accounts per role (MEJ-02). One statement. */
    @Transactional(readOnly = true)
    public AccountStatistics statistics() {
        return accounts.statistics();
    }

    /**
     * Patients and staff that already have the national ID or the email (as a record or through their account, also
     * as a pending new email). The form uses it to stop a duplicate or, for patients sharing an email, to warn.
     */
    @Transactional(readOnly = true)
    public List<PersonMatch> matches(String nationalId, String email) {
        Map<String, PersonMatch> found = new LinkedHashMap<>();
        if (nationalId != null && !nationalId.isBlank()) {
            String id = nationalId.strip();
            staffRepository.findByNationalId(id).ifPresent(record -> add(found,
                    new PersonMatch(PersonType.STAFF, record.getId(), record.fullName(), "nationalId")));
            patients.findIdByNationalId(id).ifPresent(patientId -> add(found, new PersonMatch(PersonType.PATIENT,
                    patientId, patients.fullNames(List.of(patientId)).get(patientId), "nationalId")));
        }
        if (email != null && !email.isBlank()) {
            String normalized = email.strip().toLowerCase(Locale.ROOT);
            staffRepository.findByEmail(normalized).ifPresent(record -> add(found,
                    new PersonMatch(PersonType.STAFF, record.getId(), record.fullName(), "email")));
            List<Long> patientIds = new ArrayList<>(patients.idsWithEmail(normalized));

            List<Long> accountIds = accounts.accountsUsingEmail(normalized).stream().map(AccountSummary::userId)
                    .toList();
            if (!accountIds.isEmpty()) {
                staffRepository.findByUserIdIn(accountIds).forEach(record -> add(found,
                        new PersonMatch(PersonType.STAFF, record.getId(), record.fullName(), "email")));
                accountIds.forEach(userId -> patients.findByPortalUser(userId)
                        .ifPresent(card -> patientIds.add(card.id())));
            }
            Map<Long, String> names = patients.fullNames(patientIds);
            patientIds.forEach(patientId -> add(found,
                    new PersonMatch(PersonType.PATIENT, patientId, names.get(patientId), "email")));
        }
        return List.copyOf(found.values());
    }

    /** Accounts in both lists, or the one list given; null when neither filters. */
    private static Collection<Long> intersection(Collection<Long> first, Collection<Long> second) {
        if (first == null || second == null) {
            return first == null ? second : first;
        }
        Set<Long> both = new HashSet<>(first);
        both.retainAll(second);
        return both;
    }

    private static void add(Map<String, PersonMatch> found, PersonMatch match) {
        found.putIfAbsent(match.type() + ":" + match.id() + ":" + match.field(), match);
    }

}
