import { Page } from '../../../core/http/page';
import { PhysiotherapistOption, ReportSummary, ReportTreatment } from './report-api';

/** Synthetic data of the operations report for unit tests: the numbers of prototype 13. */

export function fakeSummary(overrides: Partial<ReportSummary> = {}): ReportSummary {
  return {
    treatments: { active: 5, completed: 1, abandoned: 1 },
    sessions: { total: 28, completed: 24, rescheduled: 3, noShow: 1 },
    sessionsByPhysiotherapist: [
      { physiotherapistId: 3, name: 'Lcda. Valeria Andrade', sessions: 12 },
      { physiotherapistId: 4, name: 'Lcdo. Mateo Ríos', sessions: 6 },
      { physiotherapistId: 5, name: 'Lcdo. Ricardo Silva', sessions: 5 },
      { physiotherapistId: 6, name: 'Lcdo. Julián Rivas', sessions: 3 },
      { physiotherapistId: 7, name: 'Lcda. Paula Mena', sessions: 2 },
    ],
    averageSessionsPerPhysiotherapist: 5.6,
    ...overrides,
  };
}

export function fakeReportTreatment(overrides: Partial<ReportTreatment> = {}): ReportTreatment {
  return {
    id: 30,
    patientName: 'Rosa Elena Ortiz Vega',
    diagnosis: { code: 'M54.5', description: 'Lumbago no especificado' },
    responsibleName: 'Lcda. Valeria Andrade',
    status: 'ACTIVE',
    startDate: '2026-10-05',
    ...overrides,
  };
}

export function fakeTreatmentPage(
  content: ReportTreatment[],
  overrides: Partial<Page<ReportTreatment>> = {},
): Page<ReportTreatment> {
  return {
    content,
    page: 0,
    size: 10,
    totalElements: content.length,
    totalPages: Math.max(1, Math.ceil(content.length / 10)),
    ...overrides,
  };
}

export const FAKE_PHYSIOTHERAPISTS: PhysiotherapistOption[] = [
  { id: 3, fullName: 'Lcda. Valeria Andrade' },
  { id: 4, fullName: 'Lcdo. Mateo Ríos' },
];
