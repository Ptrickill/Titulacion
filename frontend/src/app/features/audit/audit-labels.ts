import { BadgeTone } from '../../shared/ui/status-badge';
import { AuditAction } from './audit-log-api';

export const AUDIT_ACTIONS: readonly AuditAction[] = [
  'CREATE',
  'READ',
  'UPDATE',
  'STATUS_CHANGE',
  'ACCESS_DENIED',
  'LOGIN',
];

export const AUDIT_ACTION_LABELS: Record<AuditAction, string> = {
  CREATE: 'Creación',
  READ: 'Consulta',
  UPDATE: 'Modificación',
  STATUS_CHANGE: 'Cambio de estado',
  ACCESS_DENIED: 'Acceso denegado',
  LOGIN: 'Inicio de sesión',
};

export const AUDIT_ACTION_TONES: Record<AuditAction, BadgeTone> = {
  CREATE: 'success',
  READ: 'info',
  UPDATE: 'warning',
  STATUS_CHANGE: 'purple',
  ACCESS_DENIED: 'danger',
  LOGIN: 'neutral',
};

/**
 * Names of the entities recorded by the services. A denied access records the API area instead (e.g. "patients"),
 * which is shown as is.
 */
const ENTITY_LABELS: Record<string, string> = {
  Patient: 'Paciente',
  ClinicalRecord: 'Historial clínico',
  Treatment: 'Tratamiento',
  User: 'Usuario',
};

export function entityLabel(entityType: string): string {
  return ENTITY_LABELS[entityType] ?? entityType;
}
