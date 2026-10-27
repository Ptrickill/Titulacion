import { Role, ROLE_HOME } from '../../core/auth/role';

export interface NavItem {
  label: string;
  icon: string;
  route: string;
}

/** Sidebar entries per role; each feature story adds its own items here. */
export const NAV_ITEMS: Record<Role, readonly NavItem[]> = {
  ADMINISTRATOR: [{ label: 'Panel', icon: 'dashboard', route: ROLE_HOME.ADMINISTRATOR }],
  PHYSIOTHERAPIST: [{ label: 'Panel', icon: 'dashboard', route: ROLE_HOME.PHYSIOTHERAPIST }],
  OPERATIONS_DIRECTOR: [
    { label: 'Panel', icon: 'dashboard', route: ROLE_HOME.OPERATIONS_DIRECTOR },
  ],
  PATIENT: [{ label: 'Panel', icon: 'dashboard', route: ROLE_HOME.PATIENT }],
};
