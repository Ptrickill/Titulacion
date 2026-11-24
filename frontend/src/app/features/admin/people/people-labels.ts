import { Role } from '../../../core/auth/role';
import { BadgeTone } from '../../../shared/ui/status-badge';
import { PersonAccountStatus, PersonType, StaffRole } from './people-api';

/** Account status badges of the people registry (MEJ-02), the same wording as "Gestión de Usuarios". */
export const ACCOUNT_STATUS_BADGES: Record<
  PersonAccountStatus,
  { label: string; tone: BadgeTone }
> = {
  NONE: { label: 'Sin cuenta', tone: 'neutral' },
  PENDING_ACTIVATION: { label: 'Pendiente de activación', tone: 'warning' },
  ACTIVE: { label: 'Activa', tone: 'success' },
  INACTIVE: { label: 'Inactiva', tone: 'neutral' },
};

export const PERSON_TYPE_LABELS: Record<PersonType, string> = {
  PATIENT: 'Paciente',
  STAFF: 'Personal',
};

/** The role of a person in the registry: patients show "Paciente". */
export const PERSON_ROLE_LABELS: Record<Role, string> = {
  PATIENT: 'Paciente',
  PHYSIOTHERAPIST: 'Fisioterapeuta',
  ADMINISTRATOR: 'Administración',
  OPERATIONS_DIRECTOR: 'Dirección operativa',
};

export const PERSON_ROLE_TONES: Record<Role, BadgeTone> = {
  PATIENT: 'info',
  PHYSIOTHERAPIST: 'teal',
  ADMINISTRATOR: 'purple',
  OPERATIONS_DIRECTOR: 'warning',
};

/** Roles that can be chosen for a staff member, in this order. */
export const STAFF_ROLES: readonly StaffRole[] = [
  'PHYSIOTHERAPIST',
  'ADMINISTRATOR',
  'OPERATIONS_DIRECTOR',
];
