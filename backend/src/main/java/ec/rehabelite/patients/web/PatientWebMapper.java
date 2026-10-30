package ec.rehabelite.patients.web;

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

    /** The age is calculated on the given date (today in the clinic); it is never stored. */
    @Mapping(target = "age", expression = "java(patient.ageOn(today))")
    PatientResponse toResponse(Patient patient, @Context LocalDate today);

    @Mapping(target = "age", expression = "java(patient.ageOn(today))")
    PatientSummaryResponse toSummary(Patient patient, @Context LocalDate today);

}
