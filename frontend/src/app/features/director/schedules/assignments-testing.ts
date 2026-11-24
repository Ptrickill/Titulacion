import { Page } from '../../../core/http/page';
import { slot } from '../../../shared/schedules/schedule-testing';
import {
  AvailablePhysiotherapist,
  BoardPhysiotherapist,
  OperationsTreatment,
  ScheduleBoard,
} from './assignments-api';

/** The three synthetic physiotherapists of the dev seed, and one with a deactivated account. */
export function fakeBoard(overrides: Partial<ScheduleBoard> = {}): ScheduleBoard {
  return {
    openingTime: '07:00',
    closingTime: '20:00',
    physiotherapists: [
      fakeBoardPhysiotherapist(1, 'Valeria Andrade Mora', 4, [
        slot('MONDAY', '08:00', '13:00'),
        slot('MONDAY', '14:00', '17:00'),
        slot('TUESDAY', '08:00', '13:00'),
      ]),
      fakeBoardPhysiotherapist(2, 'Martín Cevallos Ruiz', 2, [
        slot('MONDAY', '07:00', '15:00'),
        slot('SATURDAY', '08:00', '12:00'),
      ]),
      { ...fakeBoardPhysiotherapist(3, 'Iván Inactivo Prueba', 1, []), active: false },
      fakeBoardPhysiotherapist(4, 'Andrés Paredes Salinas', 1, [slot('TUESDAY', '13:00', '19:00')]),
    ],
    ...overrides,
  };
}

export function fakeBoardPhysiotherapist(
  id: number,
  fullName: string,
  activeTreatments: number,
  slots = [slot('MONDAY', '08:00', '12:00')],
): BoardPhysiotherapist {
  return {
    staffId: id + 100,
    physiotherapistId: id,
    fullName,
    active: true,
    activeTreatments,
    slots,
  };
}

export function fakeAvailable(
  id: number,
  fullName: string,
  activeTreatments: number,
): AvailablePhysiotherapist {
  return {
    physiotherapistId: id,
    staffId: id + 100,
    fullName,
    activeTreatments,
    slots: [slot('MONDAY', '07:00', '15:00')],
  };
}

export function fakeOperationsTreatment(
  id: number,
  overrides: Partial<OperationsTreatment> = {},
): OperationsTreatment {
  return {
    id,
    patientName: 'Rosa Elena Ortiz Vega',
    startDate: '2026-09-08',
    responsible: { id: 1, fullName: 'Valeria Andrade Mora' },
    responsibleInactive: false,
    scheduleDays: ['MONDAY', 'TUESDAY'],
    ...overrides,
  };
}

export function fakeOperationsPage(
  content: OperationsTreatment[],
  totalElements = content.length,
  page = 0,
): Page<OperationsTreatment> {
  return { content, page, size: 10, totalElements, totalPages: Math.ceil(totalElements / 10) };
}
