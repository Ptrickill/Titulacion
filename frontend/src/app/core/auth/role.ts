/** User roles, matching the values of users.role in the backend. */
export type Role = 'ADMINISTRATOR' | 'PHYSIOTHERAPIST' | 'OPERATIONS_DIRECTOR' | 'PATIENT';

export const ROLES: readonly Role[] = [
  'ADMINISTRATOR',
  'PHYSIOTHERAPIST',
  'OPERATIONS_DIRECTOR',
  'PATIENT',
];

/** Landing route of each role after login. */
export const ROLE_HOME: Record<Role, string> = {
  ADMINISTRATOR: '/admin',
  PHYSIOTHERAPIST: '/physio',
  OPERATIONS_DIRECTOR: '/director',
  PATIENT: '/patient',
};

/** User-facing role names (Spanish). */
export const ROLE_LABELS: Record<Role, string> = {
  ADMINISTRATOR: 'Administrador',
  PHYSIOTHERAPIST: 'Fisioterapeuta',
  OPERATIONS_DIRECTOR: 'Dirección operativa',
  PATIENT: 'Paciente',
};
