import { Page } from '../../../core/http/page';
import { fakeAddress, legacyAddress } from '../patients/patient-testing';
import { PersonAccount, PersonMatch, PersonSummary } from './people-api';
import { Staff } from './staff-api';

/** Synthetic people for the unit tests of the registry (MEJ-02); national IDs with a valid check digit. */
export function fakePerson(id: number, overrides: Partial<PersonSummary> = {}): PersonSummary {
  return {
    type: 'PATIENT',
    id,
    nationalId: '1710034065',
    firstName: 'Juan Carlos',
    lastName: 'Pérez Gómez',
    role: 'PATIENT',
    email: 'juan.perez@correo.ec',
    phone: '0987654321',
    accountStatus: 'NONE',
    userId: null,
    emailChangePending: false,
    needsCompletion: false,
    lastLoginAt: null,
    ...overrides,
  };
}

export function fakePeoplePage(
  content: PersonSummary[],
  totalElements = content.length,
): Page<PersonSummary> {
  return { content, page: 0, size: 10, totalElements, totalPages: Math.ceil(totalElements / 10) };
}

export function fakeAccount(overrides: Partial<PersonAccount> = {}): PersonAccount {
  return {
    userId: 31,
    status: 'PENDING_ACTIVATION',
    emailChangePending: false,
    lastLoginAt: null,
    ...overrides,
  };
}

export function fakeStaff(id: number, overrides: Partial<Staff> = {}): Staff {
  return {
    id,
    nationalId: '1702000165',
    firstName: 'Valeria',
    lastName: 'Andrade Mora',
    birthDate: '1990-03-14',
    age: 36,
    sex: 'FEMALE',
    phone: '0990000201',
    email: 'valeria.andrade@rehabelite.test',
    address: fakeAddress(),
    role: 'PHYSIOTHERAPIST',
    account: fakeAccount({ userId: 12, status: 'ACTIVE' }),
    needsCompletion: false,
    createdAt: '2026-11-20T15:00:00Z',
    updatedAt: null,
    ...overrides,
  };
}

/** A staff record created by V7 for an account that existed before MEJ-02: only names and email. */
export function legacyStaff(id: number): Staff {
  return fakeStaff(id, {
    nationalId: null,
    firstName: 'Paula',
    lastName: 'Herrera Vásconez',
    birthDate: null,
    age: null,
    sex: null,
    phone: null,
    email: 'direccion@rehabelite.test',
    address: { ...legacyAddress(''), reference: null, formatted: '' },
    role: 'OPERATIONS_DIRECTOR',
    needsCompletion: true,
  });
}

export function fakeMatch(overrides: Partial<PersonMatch> = {}): PersonMatch {
  return {
    type: 'PATIENT',
    id: 15,
    fullName: 'Juan Carlos Pérez Gómez',
    field: 'nationalId',
    ...overrides,
  };
}
