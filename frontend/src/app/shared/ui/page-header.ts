import { Component, input } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';

/**
 * Page title block: optional back link, title, subtitle and an actions slot on the right.
 *
 * <app-page-header title="Gestión de Pacientes" subtitle="…">
 *   <button actions mat-flat-button>…</button>
 * </app-page-header>
 */
@Component({
  selector: 'app-page-header',
  imports: [RouterLink, MatButtonModule, MatIconModule],
  template: `
    <div class="heading">
      @if (backLink(); as link) {
        <a mat-icon-button class="back" [routerLink]="link" aria-label="Volver">
          <mat-icon>chevron_left</mat-icon>
        </a>
      }
      <div>
        <h1 class="title">{{ title() }}</h1>
        @if (subtitle()) {
          <p class="subtitle">{{ subtitle() }}</p>
        }
      </div>
    </div>
    <div class="actions">
      <ng-content select="[actions]" />
    </div>
  `,
  styles: `
    :host {
      display: flex;
      flex-wrap: wrap;
      align-items: flex-start;
      justify-content: space-between;
      gap: 16px;
      margin-bottom: 24px;
    }
    .heading {
      display: flex;
      align-items: flex-start;
      gap: 8px;
    }
    .back {
      margin-top: -4px;
      border: 1px solid var(--re-gray-200);
      border-radius: var(--re-radius-sm);
    }
    .title {
      margin: 0;
      color: var(--re-text);
      font-size: 22px;
      font-weight: 700;
      line-height: 1.3;
    }
    .subtitle {
      margin: 4px 0 0;
      color: var(--re-text-muted);
      font-size: 13px;
    }
    .actions {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
    }
    .actions:empty {
      display: none;
    }
  `,
})
export class PageHeader {
  readonly title = input.required<string>();
  readonly subtitle = input<string>();
  readonly backLink = input<string>();
}
