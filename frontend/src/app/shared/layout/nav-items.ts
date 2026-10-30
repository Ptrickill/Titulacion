import { Role } from '../../core/auth/role';
import { BadgeTone } from '../ui/status-badge';

export interface NavItem {
  label: string;
  icon: string;
  route: string;
  /** Exact match for the role home ("Inicio"), prefix match for the rest. */
  exact?: boolean;
}

/** Sidebar menu per role, as defined in the prototype (docs/prototype). */
export const NAV_ITEMS: Record<Role, readonly NavItem[]> = {
  PHYSIOTHERAPIST: [
    { label: 'Inicio', icon: 'home', route: '/physio', exact: true },
    { label: 'Gestión de Pacientes', icon: 'group', route: '/physio/patients' },
    { label: 'Historial Clínico', icon: 'description', route: '/physio/clinical-records' },
    { label: 'Línea de Tiempo', icon: 'schedule', route: '/physio/timeline' },
    { label: 'Asistente Clínico IA', icon: 'neurology', route: '/physio/ai-assistant' },
  ],
  ADMINISTRATOR: [
    { label: 'Inicio', icon: 'home', route: '/admin', exact: true },
    { label: 'Gestión de Usuarios', icon: 'shield_person', route: '/admin/users' },
    { label: 'Registro de Pacientes', icon: 'group', route: '/admin/patients' },
    { label: 'Reportes Consolidados', icon: 'bar_chart', route: '/admin/reports' },
  ],
  OPERATIONS_DIRECTOR: [
    { label: 'Reportes Consolidados', icon: 'bar_chart', route: '/director/reports' },
  ],
  PATIENT: [{ label: 'Mi Tratamiento', icon: 'home', route: '/patient', exact: true }],
};

/** Subtitle under the logo in the sidebar. */
export const PANEL_LABELS: Record<Role, string> = {
  PHYSIOTHERAPIST: 'Panel de especialistas',
  ADMINISTRATOR: 'Control administrativo',
  OPERATIONS_DIRECTOR: 'Dirección operativa',
  PATIENT: 'Portal del paciente',
};

/** Color of the role badge and avatar in the header. */
export const ROLE_TONES: Record<Role, BadgeTone> = {
  PHYSIOTHERAPIST: 'teal',
  ADMINISTRATOR: 'purple',
  OPERATIONS_DIRECTOR: 'info',
  PATIENT: 'success',
};
