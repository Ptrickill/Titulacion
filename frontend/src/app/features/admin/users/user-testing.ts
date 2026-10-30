import { Role } from '../../../core/auth/role';
import { PortalCandidate, UserAccount } from './user-api';

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
    lastLoginAt: null,
    createdAt: '2026-10-01T15:00:00Z',
    ...overrides,
  };
}

/** Synthetic patient without portal account (national ID with a valid check digit). */
export function fakeCandidate(id: number, fullName: string): PortalCandidate {
  return { id, fullName, nationalId: '1710034065', medicalRecordNumber: '1710034065' };
}
