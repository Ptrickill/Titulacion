import { Component, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

export type BadgeTone = 'success' | 'warning' | 'danger' | 'info' | 'purple' | 'teal' | 'neutral';

/** Small pill used for statuses, roles and severities (e.g. "Activo", "Fisioterapeuta", "Grave"). */
@Component({
  selector: 'app-status-badge',
  imports: [MatIconModule],
  host: {
    '[class]': '"badge badge-" + tone() + (uppercase() ? " badge-upper" : "")',
  },
  template: `
    @if (icon(); as name) {
      <mat-icon class="badge-icon" aria-hidden="true">{{ name }}</mat-icon>
    }
    <span>{{ label() }}</span>
  `,
  styles: `
    :host {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      padding: 2px 10px;
      border: 1px solid transparent;
      border-radius: var(--re-radius-pill);
      font-size: 12px;
      font-weight: 600;
      line-height: 18px;
      white-space: nowrap;
    }
    :host(.badge-upper) {
      font-size: 11px;
      letter-spacing: 0.04em;
      text-transform: uppercase;
    }
    .badge-icon {
      width: 14px;
      height: 14px;
      font-size: 14px;
    }
    :host(.badge-success) {
      color: var(--re-success);
      background: var(--re-success-bg);
      border-color: var(--re-success-border);
    }
    :host(.badge-warning) {
      color: var(--re-warning);
      background: var(--re-warning-bg);
      border-color: var(--re-warning-border);
    }
    :host(.badge-danger) {
      color: var(--re-danger);
      background: var(--re-danger-bg);
      border-color: var(--re-danger-border);
    }
    :host(.badge-info) {
      color: var(--re-info);
      background: var(--re-info-bg);
      border-color: var(--re-info-border);
    }
    :host(.badge-purple) {
      color: var(--re-purple);
      background: var(--re-purple-bg);
      border-color: var(--re-purple-border);
    }
    :host(.badge-teal) {
      color: var(--re-text-teal);
      background: var(--re-teal-50);
      border-color: var(--re-teal-100);
    }
    :host(.badge-neutral) {
      color: var(--re-neutral);
      background: var(--re-neutral-bg);
      border-color: var(--re-neutral-border);
    }
  `,
})
export class StatusBadge {
  readonly label = input.required<string>();
  readonly tone = input<BadgeTone>('neutral');
  readonly icon = input<string>();
  readonly uppercase = input(false);
}
