import { formatDate } from '@angular/common';
import { Component, computed, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { APP_LOCALE } from '../../core/locale';
import { PortalInstruction } from './portal-api';

/**
 * "Indicación del terapeuta" (HU-13, prototype 14): the last instructions a physiotherapist wrote for the patient,
 * with who wrote them and in which session. Read only.
 */
@Component({
  selector: 'app-therapist-instruction-card',
  imports: [MatIconModule],
  templateUrl: './therapist-instruction-card.html',
  styleUrl: './therapist-instruction-card.scss',
})
export class TherapistInstructionCard {
  /** Null when no session of the treatment has instructions. */
  readonly instruction = input.required<PortalInstruction | null>();

  /** "Sesión 04 · 09 de noviembre": where the instructions come from. */
  protected readonly source = computed(() => {
    const instruction = this.instruction();
    if (!instruction) {
      return null;
    }
    const session = `Sesión ${String(instruction.sequenceNumber).padStart(2, '0')}`;
    return `${session} · ${formatDate(instruction.sessionDate, "dd 'de' MMMM", APP_LOCALE)}`;
  });
}
