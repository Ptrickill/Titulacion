import { booleanAttribute, Component, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

export type StatTone = 'teal' | 'info' | 'warning' | 'danger' | 'purple';

/** KPI card: pastel icon tile, uppercase label, large value and an optional hint. */
@Component({
  selector: 'app-stat-card',
  imports: [MatIconModule],
  host: { '[class.compact]': 'compact()' },
  template: `
    <div class="icon-tile" [class]="'icon-tile tone-' + tone()">
      <mat-icon aria-hidden="true">{{ icon() }}</mat-icon>
    </div>
    <div class="body">
      <p class="label">{{ label() }}</p>
      <p class="value">{{ value() }}</p>
      @if (hint()) {
        <p class="hint">{{ hint() }}</p>
      }
    </div>
  `,
  styles: `
    :host {
      display: flex;
      align-items: center;
      gap: 16px;
      padding: 20px;
      background: var(--re-card);
      border: 1px solid var(--re-gray-200);
      border-radius: var(--re-radius-lg);
      box-shadow: var(--re-shadow-sm);
    }
    .icon-tile {
      display: grid;
      flex: none;
      place-items: center;
      width: 48px;
      height: 48px;
      border-radius: var(--re-radius-md);
    }
    .tone-teal {
      color: var(--re-text-teal);
      background: var(--re-teal-50);
    }
    .tone-info {
      color: var(--re-info);
      background: var(--re-info-bg);
    }
    .tone-warning {
      color: var(--re-warning);
      background: var(--re-warning-bg);
    }
    .tone-danger {
      color: var(--re-danger);
      background: var(--re-danger-bg);
    }
    .tone-purple {
      color: var(--re-purple);
      background: var(--re-purple-bg);
    }
    .body {
      min-width: 0;
    }
    p {
      margin: 0;
    }
    .label {
      color: var(--re-text-muted);
      font-size: 11px;
      font-weight: 600;
      letter-spacing: 0.05em;
      text-transform: uppercase;
    }
    /* Compact: for rows of four cards in the 900px content area (e.g. user management) */
    :host(.compact) {
      gap: 12px;
      padding: 16px;
    }
    :host(.compact) .icon-tile {
      width: 40px;
      height: 40px;
    }
    .value {
      margin-top: 2px;
      color: var(--re-text);
      font-size: 24px;
      font-weight: 800;
      line-height: 1.2;
    }
    .hint {
      margin-top: 2px;
      color: var(--re-text-muted);
      font-size: 12px;
    }
  `,
})
export class StatCard {
  readonly label = input.required<string>();
  readonly value = input.required<string | number>();
  readonly icon = input('insights');
  readonly hint = input<string>();
  readonly tone = input<StatTone>('teal');
  /** Smaller icon and padding, so four cards fit side by side without wrapping their labels. */
  readonly compact = input(false, { transform: booleanAttribute });
}
