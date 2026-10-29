import { Role } from '../../../core/auth/role';
import { BadgeTone } from '../../../shared/ui/status-badge';

/** Role names and badge colors of the user management screen (prototype 09). */
export const USER_ROLE_LABELS: Record<Role, string> = {
  PHYSIOTHERAPIST: 'Fisioterapeuta',
  ADMINISTRATOR: 'Administrador',
  PATIENT: 'Paciente (Portal)',
  OPERATIONS_DIRECTOR: 'Dirección Operativa',
};

export const USER_ROLE_TONES: Record<Role, BadgeTone> = {
  PHYSIOTHERAPIST: 'teal',
  ADMINISTRATOR: 'purple',
  PATIENT: 'info',
  OPERATIONS_DIRECTOR: 'warning',
};

/** Order of the role filter, as in the prototype. */
export const USER_ROLE_FILTERS: readonly Role[] = [
  'PHYSIOTHERAPIST',
  'ADMINISTRATOR',
  'PATIENT',
  'OPERATIONS_DIRECTOR',
];
