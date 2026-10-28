import { Component, input } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';

/** White card shown by role panels whose features are not implemented yet. */
@Component({
  selector: 'app-panel-placeholder',
  imports: [MatCardModule, MatIconModule],
  template: `
    <mat-card appearance="outlined" class="placeholder">
      <mat-card-content>
        <mat-icon class="icon">{{ icon() }}</mat-icon>
        <h2 class="heading">{{ heading() }}</h2>
        <p class="description">{{ description() }}</p>
      </mat-card-content>
    </mat-card>
  `,
  styles: `
    .placeholder {
      border: none;
      box-shadow: 0 1px 3px rgb(15 23 42 / 8%);
    }
    mat-card-content {
      padding: 32px;
    }
    .icon {
      color: var(--re-teal);
      font-size: 40px;
      width: 40px;
      height: 40px;
    }
    .heading {
      margin: 12px 0 4px;
      font: var(--mat-sys-headline-small);
      color: var(--re-navy);
    }
    .description {
      margin: 0;
      color: var(--mat-sys-on-surface-variant);
    }
  `,
})
export class PanelPlaceholder {
  readonly icon = input('dashboard');
  readonly heading = input.required<string>();
  readonly description = input(
    'Las funcionalidades de este panel estarán disponibles próximamente.',
  );
}
