package ec.rehabelite.patients.web;

import ec.rehabelite.patients.application.AddressText;
import ec.rehabelite.patients.application.AddressView;
import ec.rehabelite.patients.domain.Patient;
import java.time.LocalDate;
import java.util.List;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
interface PatientWebMapper {

    @Mapping(target = "fullName", expression = "java(patient.getLastName() + \" \" + patient.getFirstName())")
    PortalCandidateResponse toPortalCandidate(Patient patient);

    List<PortalCandidateResponse> toPortalCandidates(List<Patient> patients);

    /**
     * The age is calculated on the given date (today in the clinic); it is never stored. The address is composed with
     * the place names of the INEC catalog (MEJ-03).
     */
    @Mapping(target = "age", expression = "java(patient.ageOn(today))")
    @Mapping(target = "address", expression = "java(toAddress(addressText.view(patient.getAddress())))")
    @Mapping(target = "needsCompletion", expression = "java(patient.needsCompletion())")
    PatientResponse toResponse(Patient patient, @Context LocalDate today, @Context AddressText addressText);

    @Mapping(target = "age", expression = "java(patient.ageOn(today))")
    @Mapping(target = "needsCompletion", expression = "java(patient.needsCompletion())")
    PatientSummaryResponse toSummary(Patient patient, @Context LocalDate today);

    AddressResponse toAddress(AddressView address);

}
