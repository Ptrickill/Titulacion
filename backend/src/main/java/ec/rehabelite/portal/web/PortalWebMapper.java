package ec.rehabelite.portal.web;

import ec.rehabelite.clinicalrecords.PatientTreatment;
import ec.rehabelite.portal.application.PortalService.PortalTreatment;
import ec.rehabelite.portal.domain.PainEvolutionPoint;
import ec.rehabelite.portal.domain.TreatmentProgress;
import ec.rehabelite.portal.web.PortalResponses.DiagnosisResponse;
import ec.rehabelite.portal.web.PortalResponses.PainPointResponse;
import ec.rehabelite.portal.web.PortalResponses.PortalTreatmentResponse;
import ec.rehabelite.portal.web.PortalResponses.TreatmentProgressResponse;
import java.util.List;
import org.mapstruct.Mapper;

@Mapper
interface PortalWebMapper {

    TreatmentProgressResponse toResponse(TreatmentProgress progress);

    PainPointResponse toResponse(PainEvolutionPoint point);

    /** Only what the patient may see: no patient id, no staff id and no initial pain score in the list. */
    default PortalTreatmentResponse toResponse(PortalTreatment listed) {
        PatientTreatment treatment = listed.treatment();
        return new PortalTreatmentResponse(treatment.id(),
                new DiagnosisResponse(treatment.diagnosisCode(), treatment.diagnosisDescription()),
                treatment.status(), treatment.active(), treatment.startDate(), listed.responsibleName(),
                treatment.plannedSessions(), listed.completedSessions());
    }

    default List<PortalTreatmentResponse> toResponses(List<PortalTreatment> treatments) {
        return treatments.stream().map(this::toResponse).toList();
    }

}
