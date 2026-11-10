import { DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import {
  catchError,
  debounceTime,
  distinctUntilChanged,
  map,
  Observable,
  of,
  Subject,
  switchMap,
} from 'rxjs';
import { Page } from '../../../core/http/page';
import { ClinicalRecordApi, PhysioPatient } from '../clinical-records/clinical-record-api';

export interface PatientSearchState {
  query: string;
  page: number;
}

/**
 * Search of the physiotherapist's patients (GET /api/physio/patients) by name or national ID, paginated in the
 * server. What the user types is searched after 300 ms, from the first page. The pages that use it share the search
 * and differ only in what they show of each patient.
 * <p>
 * Create it in a field initializer of the component: it needs the injection context.
 */
export class PhysioPatientSearch {
  private readonly api = inject(ClinicalRecordApi);

  private readonly current = signal<PatientSearchState>({ query: '', page: 0 });
  private readonly page = signal<Page<PhysioPatient> | null>(null);
  private readonly pending = signal(true);
  private readonly failed = signal(false);
  private readonly typed = new Subject<string>();

  readonly state = this.current.asReadonly();
  /** The last page loaded; it stays while the next one loads or fails. */
  readonly results = this.page.asReadonly();
  readonly loading = this.pending.asReadonly();
  readonly loadError = this.failed.asReadonly();

  constructor(readonly pageSize: number) {
    const destroyRef = inject(DestroyRef);
    // A new query starts again from the first page
    this.typed
      .pipe(
        map((query) => query.trim()),
        debounceTime(300),
        distinctUntilChanged(),
        takeUntilDestroyed(destroyRef),
      )
      .subscribe((query) => this.current.set({ query, page: 0 }));

    toObservable(this.current)
      .pipe(
        switchMap((state) => this.load(state)),
        takeUntilDestroyed(destroyRef),
      )
      .subscribe((page) => {
        this.pending.set(false);
        this.failed.set(page === null);
        if (page) {
          this.page.set(page);
        }
      });
  }

  /** Handler of the (input) event of the search field. */
  onInput(event: Event): void {
    this.typed.next((event.target as HTMLInputElement).value);
  }

  goToPage(page: number): void {
    this.current.update((state) => ({ ...state, page }));
  }

  reload(): void {
    this.current.update((state) => ({ ...state }));
  }

  private load(state: PatientSearchState): Observable<Page<PhysioPatient> | null> {
    this.pending.set(true);
    return this.api
      .listPatients(state.query, state.page, this.pageSize)
      .pipe(catchError(() => of(null)));
  }
}
