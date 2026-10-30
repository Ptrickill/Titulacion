import { Component, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

/** Centered message for empty lists or sections that are not available yet. */
@Component({
  selector: 'app-empty-state',
  imports: [MatIconModule],
  host: { role: 'status' },
  template: `
    <div class="icon-circle">
      <mat-icon aria-hidden="true">{{ icon() }}</mat-icon>
    </div>
    <h2 class="title">{{ title() }}</h2>
    @if (message()) {
      <p class="message">{{ message() }}</p>
    }
    <div class="action">
      <ng-content />
    </div>
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      align-items: center;
      padding: 48px 24px;
      text-align: center;
      background: var(--re-card);
      border: 1px solid var(--re-gray-200);
      border-radius: var(--re-radius-lg);
      box-shadow: var(--re-shadow-sm);
    }
    .icon-circle {
      display: grid;
      place-items: center;
      width: 64px;
      height: 64px;
      color: var(--re-text-teal);
      background: var(--re-teal-50);
      border-radius: 50%;
    }
    .icon-circle mat-icon {
      width: 32px;
      height: 32px;
      font-size: 32px;
    }
    .title {
      margin: 16px 0 4px;
      color: var(--re-text);
      font-size: 16px;
      font-weight: 700;
    }
    .message {
      max-width: 440px;
      margin: 0;
      color: var(--re-text-muted);
      font-size: 13px;
      line-height: 1.5;
    }
    .action:not(:empty) {
      margin-top: 16px;
    }
  `,
})
export class EmptyState {
  readonly icon = input('inbox');
  readonly title = input.required<string>();
  readonly message = input<string>();
}
