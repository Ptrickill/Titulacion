import { Sex } from './patient-api';

export const SEX_LABELS: Record<Sex, string> = {
  MALE: 'Masculino',
  FEMALE: 'Femenino',
};

export const SEXES: readonly Sex[] = ['MALE', 'FEMALE'];

/** Stored as shown: the columns are free text, so these lists can grow without a migration. */
export const EDUCATION_LEVELS: readonly string[] = [
  'Ninguno',
  'Primaria',
  'Secundaria',
  'Superior',
  'Posgrado',
];

export const HEALTH_INSURANCES: readonly string[] = [
  'IESS',
  'ISSFA',
  'ISSPOL',
  'Seguro privado',
  'Ninguno',
];

/** Self-identification categories of the Ecuadorian census. */
export const ETHNIC_IDENTIFICATIONS: readonly string[] = [
  'Mestizo',
  'Indígena',
  'Afroecuatoriano',
  'Montubio',
  'Blanco',
  'Otro',
];
