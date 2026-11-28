import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

/** A legal text in force (MEJ-06), versioned in the backend repository; `content` is Markdown. */
export interface LegalText {
  key: string;
  version: string;
  title: string;
  content: string;
}

export type ConsentType = 'DATA_PROCESSING' | 'EMAIL_COMMUNICATIONS';
export type ConsentStatus = 'CURRENT' | 'PREVIOUS_VERSION' | 'PENDING' | 'REVOKED';
export type ConsentChannel = 'PHYSICAL_SIGNATURE' | 'PORTAL' | 'ERASURE';

/** The state of a consent type of a patient (same shape for the reception and the portal). */
export interface PatientConsentView {
  type: ConsentType;
  status: ConsentStatus;
  textVersion: string | null;
  currentTextVersion: string;
  channel: ConsentChannel | null;
  grantedAt: string | null;
  revokedAt: string | null;
  revocationChannel: ConsentChannel | null;
}

export const CONSENT_TEXT_KEYS: Record<ConsentType, string> = {
  DATA_PROCESSING: 'consent-data-processing',
  EMAIL_COMMUNICATIONS: 'consent-email-communications',
};

/** The privacy notice (public) and the consent texts (authenticated), MEJ-06. */
@Injectable({ providedIn: 'root' })
export class LegalApi {
  private readonly http = inject(HttpClient);

  privacyNotice(): Observable<LegalText> {
    return this.http.get<LegalText>('/api/legal/privacy-notice');
  }

  consentTexts(): Observable<LegalText[]> {
    return this.http.get<LegalText[]>('/api/legal/consents');
  }
}
