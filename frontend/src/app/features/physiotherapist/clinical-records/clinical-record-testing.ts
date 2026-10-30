import { ActiveTreatment, PatientClinicalRecord, PreviousTreatment } from './clinical-record-api';

/** Active treatment of the record view, with its current responsible. */
export function fakeActiveTreatment(
  id: number,
  overrides: Partial<ActiveTreatment> = {},
): ActiveTreatment {
  return {
    id,
    diagnosis: { code: 'M51.1', description: 'Trastorno de disco lumbar con radiculopatía' },
    chiefComplaint: 'Dolor muscular asociado al ejercicio',
    currentIllness: 'Cuatro meses de evolución relacionado con la actividad física',
    referringPhysician: 'Dr. Carlos Andrade',
    complementaryTests: 'Radiografía de columna dorsal',
    severity: 'MODERATE',
    painType: 'Presión',
    painLocation: 'Pecho y espalda alta',
    initialPainScore: 7,
    painFrequency: 'Intermitente',
    sessionFrequency: '3 veces por semana',
    goal: 'Reducción de dolor y fortalecimiento lumbar progresivo',
    plannedSessions: 12,
    status: 'ACTIVE',
    startDate: '2026-10-01',
    currentResponsible: {
      physiotherapistId: 7,
      fullName: 'Lcda. Valeria Andrade',
      validFrom: '2026-10-01T15:00:00Z',
      validTo: null,
    },
    ...overrides,
  };
}

/** Completed treatment of the record view. */
export function fakePreviousTreatment(
  id: number,
  overrides: Partial<PreviousTreatment> = {},
): PreviousTreatment {
  return {
    id,
    diagnosis: { code: 'M54.5', description: 'Lumbago no especificado' },
    severity: 'MILD',
    status: 'COMPLETED',
    startDate: '2025-03-10',
    plannedSessions: 8,
    updatedAt: '2025-05-02T16:00:00Z',
    responsible: {
      physiotherapistId: 8,
      fullName: 'Lcdo. Martín Cevallos',
      validFrom: '2025-03-10T15:00:00Z',
      validTo: null,
    },
    ...overrides,
  };
}

/** Synthetic clinical record view (GET /api/patients/{id}/clinical-record) for unit tests. */
export function fakeClinicalRecord(
  patientId: number,
  overrides: Partial<PatientClinicalRecord> = {},
): PatientClinicalRecord {
  return {
    patient: {
      id: patientId,
      nationalId: '1710034065',
      medicalRecordNumber: '1710034065',
      firstName: 'Juan Carlos',
      lastName: 'Pérez Gómez',
      birthDate: '1981-03-15',
      age: 45,
      sex: 'MALE',
      phone: '+593 98 765 4321',
      email: 'juan.perez@correo.ec',
      address: 'Av. Amazonas N32-12, Quito',
      occupation: 'Ingeniero civil',
      educationLevel: 'Superior',
      healthInsurance: 'IESS',
      admissionDate: '2026-07-08',
    },
    clinicalRecord: {
      id: 40,
      patientId,
      habits: 'No fuma; café diario',
      physicalActivity: 'Camina 30 minutos',
      allergies: 'Ninguna conocida',
      currentMedication: 'Ibuprofeno 400 mg',
      personalHistory: 'Hipertensión controlada',
      surgicalHistory: 'Apendicectomía (2010)',
      familyHistory: 'Madre con artrosis',
      createdAt: '2025-03-10T15:00:00Z',
      updatedAt: null,
    },
    activeTreatment: fakeActiveTreatment(30),
    previousTreatments: [fakePreviousTreatment(20)],
    ...overrides,
  };
}
