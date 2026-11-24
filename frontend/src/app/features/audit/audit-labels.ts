import { BadgeTone } from '../../shared/ui/status-badge';
import { AuditAction } from './audit-log-api';

export const AUDIT_ACTIONS: readonly AuditAction[] = [
  'CREATE',
  'READ',
  'UPDATE',
  'STATUS_CHANGE',
  'ACCESS_DENIED',
  'LOGIN',
  'EXPORT',
  'INVITATION_SENT',
  'INVITATION_RESENT',
  'ACTIVATION',
  'RESET_REQUEST',
  'PASSWORD_RESET',
  'EMAIL_CHANGE',
  'EMAIL_CONFIRMATION',
];

export const AUDIT_ACTION_LABELS: Record<AuditAction, string> = {
  CREATE: 'Creación',
  READ: 'Consulta',
  UPDATE: 'Modificación',
  STATUS_CHANGE: 'Cambio de estado',
  ACCESS_DENIED: 'Acceso denegado',
  LOGIN: 'Inicio de sesión',
  // HU-15: the PDF of the operations report
  EXPORT: 'Exportación',
  // MEJ-01: account activation and password reset
  INVITATION_SENT: 'Invitación enviada',
  INVITATION_RESENT: 'Invitación reenviada',
  ACTIVATION: 'Activación de cuenta',
  RESET_REQUEST: 'Solicitud de restablecimiento',
  PASSWORD_RESET: 'Restablecimiento de contraseña',
  // MEJ-02: neither address is recorded
  EMAIL_CHANGE: 'Cambio de correo',
  EMAIL_CONFIRMATION: 'Confirmación de correo',
};

export const AUDIT_ACTION_TONES: Record<AuditAction, BadgeTone> = {
  CREATE: 'success',
  READ: 'info',
  UPDATE: 'warning',
  STATUS_CHANGE: 'purple',
  ACCESS_DENIED: 'danger',
  LOGIN: 'neutral',
  EXPORT: 'teal',
  INVITATION_SENT: 'info',
  INVITATION_RESENT: 'info',
  ACTIVATION: 'success',
  RESET_REQUEST: 'warning',
  PASSWORD_RESET: 'purple',
  EMAIL_CHANGE: 'info',
  EMAIL_CONFIRMATION: 'success',
};

/**
 * Names of the entities recorded by the services. A denied access records the API area instead (e.g. "patients"),
 * which is shown as is.
 */
const ENTITY_LABELS: Record<string, string> = {
  Patient: 'Paciente',
  ClinicalRecord: 'Historial clínico',
  Treatment: 'Tratamiento',
  TherapySession: 'Sesión terapéutica',
  User: 'Usuario',
  Report: 'Reporte',
  StaffRecord: 'Personal',
};

export function entityLabel(entityType: string): string {
  return ENTITY_LABELS[entityType] ?? entityType;
}
