package ec.rehabelite.patients.web;

import ec.rehabelite.auth.AccountSummary;
import ec.rehabelite.catalogs.AddressFormatter;
import ec.rehabelite.patients.domain.Patient;
import java.time.LocalDate;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
interface PatientWebMapper {

    /**
     * The age is calculated on the given date (today in the clinic); it is never stored. The address is composed with
     * the place names of the INEC catalog (MEJ-03), and the portal account (MEJ-02) is the given one, or null. MEJ-06:
     * whether the DATA_PROCESSING consent is pending, and the block of an accepted erasure request.
     */
    @Mapping(target = "age", expression = "java(patient.ageOn(today))")
    @Mapping(target = "address", expression = "java(addressFormatter.view(patient.getAddress()))")
    @Mapping(target = "needsCompletion", expression = "java(patient.needsCompletion())")
    @Mapping(target = "portalAccount", expression = "java(PortalAccountResponse.of(account))")
    @Mapping(target = "consentPending", expression = "java(consentPending)")
    @Mapping(target = "blocked", expression = "java(patient.isBlocked())")
    PatientResponse toResponse(Patient patient, @Context LocalDate today, @Context AddressFormatter addressFormatter,
            @Context AccountSummary account, @Context boolean consentPending);

    @Mapping(target = "age", expression = "java(patient.ageOn(today))")
    @Mapping(target = "needsCompletion", expression = "java(patient.needsCompletion())")
    PatientSummaryResponse toSummary(Patient patient, @Context LocalDate today);

}
