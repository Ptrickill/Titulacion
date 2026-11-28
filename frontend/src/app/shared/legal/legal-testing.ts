import { HttpTestingController } from '@angular/common/http/testing';
import { ConsentType, LegalText, PatientConsentView } from './legal-api';
import { DataRequest } from './privacy-labels';

/** Synthetic legal texts and consents for unit tests (MEJ-06). */
export const FAKE_VERSION = '0.1-borrador';

export function fakeLegalText(key: string, title: string): LegalText {
  return {
    key,
    version: FAKE_VERSION,
    title,
    content: `> **BORRADOR.** Texto de ejemplo.\n\n## Finalidad\n\nTexto de prueba de ${title}.\n\n- Primer punto\n- Segundo punto`,
  };
}

export const FAKE_CONSENT_TEXTS: LegalText[] = [
  fakeLegalText(
    'consent-data-processing',
    'Consentimiento para el tratamiento de datos personales y de salud',
  ),
  fakeLegalText('consent-email-communications', 'Consentimiento para comunicaciones por correo'),
];

export function fakeConsent(
  type: ConsentType,
  overrides: Partial<PatientConsentView> = {},
): PatientConsentView {
  return {
    type,
    status: 'CURRENT',
    textVersion: FAKE_VERSION,
    currentTextVersion: FAKE_VERSION,
    channel: 'PHYSICAL_SIGNATURE',
    grantedAt: '2026-11-20T14:00:00Z',
    revokedAt: null,
    revocationChannel: null,
    ...overrides,
  };
}

/** DATA_PROCESSING current and EMAIL_COMMUNICATIONS pending, as most patients. */
export function fakeConsents(
  dataProcessing: Partial<PatientConsentView> = {},
  emailCommunications: Partial<PatientConsentView> = {
    status: 'PENDING',
    textVersion: null,
    channel: null,
    grantedAt: null,
  },
): PatientConsentView[] {
  return [
    fakeConsent('DATA_PROCESSING', dataProcessing),
    fakeConsent('EMAIL_COMMUNICATIONS', emailCommunications),
  ];
}

export function fakeDataRequest(id: number, overrides: Partial<DataRequest> = {}): DataRequest {
  return {
    id,
    type: 'ACCESS',
    receivedOn: '2026-11-20',
    status: 'RECEIVED',
    response: null,
    resolvedAt: null,
    dueOn: '2026-12-05',
    daysElapsed: 6,
    overdue: false,
    ...overrides,
  };
}

/** Answers the consent texts if the page asked for them. */
export function flushConsentTexts(backend: HttpTestingController): void {
  backend.match('/api/legal/consents').forEach((request) => request.flush(FAKE_CONSENT_TEXTS));
}
