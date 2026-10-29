package ec.rehabelite.patients.web;

import ec.rehabelite.patients.domain.Patient;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
interface PatientWebMapper {

    @Mapping(target = "fullName", expression = "java(patient.getLastName() + \" \" + patient.getFirstName())")
    PortalCandidateResponse toPortalCandidate(Patient patient);

    List<PortalCandidateResponse> toPortalCandidates(List<Patient> patients);

}
