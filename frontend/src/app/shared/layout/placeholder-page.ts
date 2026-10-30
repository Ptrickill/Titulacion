import { Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { map } from 'rxjs';
import { EmptyState } from '../ui/empty-state';
import { PageHeader } from '../ui/page-header';

/** Route data read by {@link PlaceholderPage}. */
export interface PlaceholderPageData {
  heading: string;
  subtitle: string;
  icon: string;
}

/**
 * Empty page for sections whose user story is not implemented yet. Each feature replaces the
 * route's component with its real page when the story is delivered.
 */
@Component({
  selector: 'app-placeholder-page',
  imports: [PageHeader, EmptyState],
  template: `
    @if (page(); as page) {
      <app-page-header [title]="page.heading" [subtitle]="page.subtitle" />
      <app-empty-state
        [icon]="page.icon"
        title="Sección en construcción"
        message="Esta funcionalidad estará disponible en una próxima versión del sistema."
      />
    }
  `,
})
export class PlaceholderPage {
  protected readonly page = toSignal(
    inject(ActivatedRoute).data.pipe(map((data) => data as PlaceholderPageData)),
  );
}
