import { Patient } from './patient-api';

/** Synthetic patient for unit tests (national ID with a valid check digit). */
export function fakePatient(id: number, overrides: Partial<Patient> = {}): Patient {
  return {
    id,
    nationalId: '1710034065',
    medicalRecordNumber: '1710034065',
    admissionDate: '2026-10-29',
    firstName: 'Juan Carlos',
    lastName: 'Pérez Gómez',
    birthDate: '1996-03-15',
    age: 30,
    sex: 'MALE',
    phone: '+593 98 765 4321',
    email: 'juan.perez@correo.ec',
    address: 'Av. Ilaló y Río Pita, Valle de los Chillos',
    occupation: 'Ingeniero civil',
    educationLevel: 'Superior',
    healthInsurance: 'IESS',
    ethnicSelfIdentification: 'Mestizo',
    religion: null,
    createdAt: '2026-10-29T15:00:00Z',
    updatedAt: null,
    ...overrides,
  };
}
