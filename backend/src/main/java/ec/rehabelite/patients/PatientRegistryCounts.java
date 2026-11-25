package ec.rehabelite.patients;

/** Registered patients and how many of them have no portal account (MEJ-05), counted in one statement. */
public record PatientRegistryCounts(long patients, long withoutPortal) {
}
