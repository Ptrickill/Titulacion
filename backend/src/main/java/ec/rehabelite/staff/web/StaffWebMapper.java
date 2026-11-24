package ec.rehabelite.staff.web;

import ec.rehabelite.auth.AccountSummary;
import ec.rehabelite.catalogs.AddressFormatter;
import ec.rehabelite.staff.domain.StaffRecord;
import java.time.LocalDate;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
interface StaffWebMapper {

    /** The age on the given date (today in the clinic), the address composed with the INEC names, and the account. */
    @Mapping(target = "age", expression = "java(staff.ageOn(today))")
    @Mapping(target = "address", expression = "java(addressFormatter.view(staff.getAddress()))")
    @Mapping(target = "role", expression = "java(account == null ? null : account.role())")
    @Mapping(target = "account", expression = "java(AccountResponse.of(account))")
    @Mapping(target = "needsCompletion", expression = "java(staff.needsCompletion())")
    StaffResponse toResponse(StaffRecord staff, @Context LocalDate today, @Context AddressFormatter addressFormatter,
            @Context AccountSummary account);

}
