import { Component, input } from '@angular/core';

/**
 * Numbered block of a form ("1. DATOS DE ADMISIÓN") that lays its fields out in a responsive
 * two-column grid. Add the class "full" to a projected field to make it span both columns.
 */
@Component({
  selector: 'app-form-section',
  template: `
    <h2 class="title">
      @if (step()) {
        <span>{{ step() }}.</span>
      }
      {{ title() }}
    </h2>
    @if (description()) {
      <p class="description">{{ description() }}</p>
    }
    <div class="grid">
      <ng-content />
    </div>
  `,
  styles: `
    :host {
      display: block;
    }
    :host + :host {
      margin-top: 24px;
    }
    .title {
      margin: 0 0 16px;
      padding-bottom: 8px;
      color: var(--re-text-teal);
      border-bottom: 1px solid var(--re-teal-100);
      font-size: 12px;
      font-weight: 700;
      letter-spacing: 0.06em;
      text-transform: uppercase;
    }
    .description {
      margin: -8px 0 16px;
      color: var(--re-text-muted);
      font-size: 13px;
    }
    .grid {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: 4px 16px;
    }
    .grid > ::ng-deep .full {
      grid-column: 1 / -1;
    }
    @media (max-width: 719.98px) {
      .grid {
        grid-template-columns: minmax(0, 1fr);
      }
    }
  `,
})
export class FormSection {
  readonly title = input.required<string>();
  readonly step = input<number>();
  readonly description = input<string>();
}
