package ec.rehabelite.reports.web;

import ec.rehabelite.auth.StaffMember;
import ec.rehabelite.clinicalrecords.PatientTreatment;
import ec.rehabelite.reports.application.ReportService.ReportTreatment;
import ec.rehabelite.reports.domain.OperationsSummary;
import ec.rehabelite.reports.web.ReportResponses.DiagnosisResponse;
import ec.rehabelite.reports.web.ReportResponses.PhysiotherapistOptionResponse;
import ec.rehabelite.reports.web.ReportResponses.ReportSummaryResponse;
import ec.rehabelite.reports.web.ReportResponses.ReportTreatmentResponse;
import java.util.List;
import org.mapstruct.Mapper;

@Mapper
interface ReportWebMapper {

    ReportSummaryResponse toResponse(OperationsSummary summary);

    List<PhysiotherapistOptionResponse> toOptions(List<StaffMember> physiotherapists);

    /** Only the name of the patient: no patient id, national ID nor other personal data. */
    default ReportTreatmentResponse toResponse(ReportTreatment listed) {
        PatientTreatment treatment = listed.treatment();
        return new ReportTreatmentResponse(treatment.id(), listed.patientName(),
                new DiagnosisResponse(treatment.diagnosisCode(), treatment.diagnosisDescription()),
                listed.responsibleName(), treatment.status(), treatment.startDate());
    }

}
