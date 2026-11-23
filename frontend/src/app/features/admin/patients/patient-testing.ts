import { LocationCatalog } from '../../../shared/forms/location-catalog';
import { Patient, PatientAddress } from './patient-api';

/** Complete address of the synthetic patient: Conocoto, Distrito Metropolitano de Quito, Pichincha. */
export function fakeAddress(overrides: Partial<PatientAddress> = {}): PatientAddress {
  return {
    provinceCode: '17',
    provinceName: 'Pichincha',
    cantonCode: '1701',
    cantonName: 'Distrito Metropolitano de Quito',
    parishCode: '170156',
    parishName: 'Conocoto',
    mainStreet: 'Av. Ilaló',
    streetNumber: 'N12-34',
    crossStreet: 'Río Pita',
    reference: 'Frente al parque central',
    formatted:
      'Av. Ilaló N12-34 y Río Pita, Conocoto, Distrito Metropolitano de Quito, Pichincha. ' +
      'Referencia: Frente al parque central',
    complete: true,
    ...overrides,
  };
}

/** Address of a patient registered before MEJ-03: only its former free text, kept as the reference. */
export function legacyAddress(text = 'Av. Ilaló y Río Pita, Valle de los Chillos'): PatientAddress {
  return {
    provinceCode: null,
    provinceName: null,
    cantonCode: null,
    cantonName: null,
    parishCode: null,
    parishName: null,
    mainStreet: null,
    streetNumber: null,
    crossStreet: null,
    reference: text,
    formatted: text,
    complete: false,
  };
}

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
    phone: '0987654321',
    email: 'juan.perez@correo.ec',
    address: fakeAddress(),
    occupation: 'Ingeniero civil',
    educationLevel: 'Superior',
    hasHealthInsurance: true,
    ethnicSelfIdentification: 'Mestizo',
    religion: null,
    needsCompletion: false,
    createdAt: '2026-10-29T15:00:00Z',
    updatedAt: null,
    ...overrides,
  };
}

/** A small slice of INEC's catalog for unit tests: two provinces with real codes and names. */
export const FAKE_LOCATIONS: LocationCatalog = {
  source: 'INEC - Clasificador Geográfico Estadístico 2025',
  version: 'CGE 2025',
  cutoffDate: '2024-12-31',
  provinces: [
    {
      code: '09',
      name: 'GUAYAS',
      displayName: 'Guayas',
      cantons: [
        {
          code: '0901',
          name: 'GUAYAQUIL',
          displayName: 'Guayaquil',
          parishes: [
            { code: '090112', name: 'TARQUI', displayName: 'Tarqui', type: 'URBAN' },
            { code: '090114', name: 'XIMENA', displayName: 'Ximena', type: 'URBAN' },
          ],
        },
      ],
    },
    {
      code: '17',
      name: 'PICHINCHA',
      displayName: 'Pichincha',
      cantons: [
        {
          code: '1701',
          name: 'DISTRITO METROPOLITANO DE QUITO',
          displayName: 'Distrito Metropolitano de Quito',
          parishes: [
            { code: '170112', name: 'IÑAQUITO', displayName: 'Iñaquito', type: 'URBAN' },
            { code: '170156', name: 'CONOCOTO', displayName: 'Conocoto', type: 'RURAL' },
          ],
        },
        {
          code: '1707',
          name: 'SAN MIGUEL DE LOS BANCOS',
          displayName: 'San Miguel de los Bancos',
          parishes: [
            {
              code: '170750',
              name: 'SAN MIGUEL DE LOS BANCOS (CABECERA CANTONAL)',
              displayName: 'San Miguel de los Bancos (Cabecera Cantonal)',
              type: 'SEAT',
            },
          ],
        },
      ],
    },
  ],
};
