import { BadgeTone } from '../ui/status-badge';
import { ConsentChannel, ConsentStatus, ConsentType } from './legal-api';

/** Spanish labels of the consents and the requests of the data subject (MEJ-06). */
export const CONSENT_TYPE_LABELS: Record<ConsentType, string> = {
  DATA_PROCESSING: 'Tratamiento de datos personales y de salud',
  EMAIL_COMMUNICATIONS: 'Comunicaciones por correo',
};

export const CONSENT_STATUS_LABELS: Record<ConsentStatus, string> = {
  CURRENT: 'Vigente',
  PREVIOUS_VERSION: 'Versión anterior',
  PENDING: 'Pendiente',
  REVOKED: 'Revocado',
};

export const CONSENT_STATUS_TONES: Record<ConsentStatus, BadgeTone> = {
  CURRENT: 'success',
  PREVIOUS_VERSION: 'info',
  PENDING: 'warning',
  REVOKED: 'danger',
};

export const CONSENT_CHANNEL_LABELS: Record<ConsentChannel, string> = {
  PHYSICAL_SIGNATURE: 'firma física',
  PORTAL: 'portal del paciente',
  ERASURE: 'solicitud de eliminación',
};

export type DataRequestType =
  'ACCESS' | 'RECTIFICATION' | 'UPDATE' | 'OBJECTION' | 'ERASURE' | 'PORTABILITY';
export type DataRequestStatus = 'RECEIVED' | 'IN_PROGRESS' | 'RESOLVED' | 'REJECTED';

export const DATA_REQUEST_TYPES: readonly DataRequestType[] = [
  'ACCESS',
  'RECTIFICATION',
  'UPDATE',
  'OBJECTION',
  'ERASURE',
  'PORTABILITY',
];

export const DATA_REQUEST_TYPE_LABELS: Record<DataRequestType, string> = {
  ACCESS: 'Acceso',
  RECTIFICATION: 'Rectificación',
  UPDATE: 'Actualización',
  OBJECTION: 'Oposición',
  ERASURE: 'Eliminación',
  PORTABILITY: 'Portabilidad',
};

export const DATA_REQUEST_STATUS_LABELS: Record<DataRequestStatus, string> = {
  RECEIVED: 'Recibida',
  IN_PROGRESS: 'En curso',
  RESOLVED: 'Resuelta',
  REJECTED: 'Rechazada',
};

export const DATA_REQUEST_STATUS_TONES: Record<DataRequestStatus, BadgeTone> = {
  RECEIVED: 'info',
  IN_PROGRESS: 'warning',
  RESOLVED: 'success',
  REJECTED: 'neutral',
};

/** A request of the data subject with its due date, as the API returns it. */
export interface DataRequest {
  id: number;
  type: DataRequestType;
  /** ISO date (yyyy-MM-dd). */
  receivedOn: string;
  status: DataRequestStatus;
  response: string | null;
  resolvedAt: string | null;
  /** ISO date: the reception plus app.compliance.request-response-days (to be confirmed by the clinic). */
  dueOn: string;
  daysElapsed: number;
  overdue: boolean;
}

export function isOpenRequest(request: DataRequest): boolean {
  return request.status === 'RECEIVED' || request.status === 'IN_PROGRESS';
}
