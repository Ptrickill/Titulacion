import { Role } from '../../core/auth/role';
import { UserAccount } from './user-api';

/** Synthetic account for unit tests. */
export function fakeUser(
  id: number,
  fullName: string,
  role: Role,
  overrides: Partial<UserAccount> = {},
): UserAccount {
  return {
    id,
    email: `usuario${id}@rehabelite.test`,
    fullName,
    role,
    active: true,
    status: 'ACTIVE',
    emailChangePending: false,
    lastLoginAt: null,
    createdAt: '2026-10-01T15:00:00Z',
    ...overrides,
  };
}
