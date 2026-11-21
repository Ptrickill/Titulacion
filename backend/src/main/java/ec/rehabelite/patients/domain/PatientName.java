package ec.rehabelite.patients.domain;

/** Only the names of a patient (HU-14): a projection, so no other personal data is read. */
public interface PatientName {

    Long getId();

    String getFirstName();

    String getLastName();

}
