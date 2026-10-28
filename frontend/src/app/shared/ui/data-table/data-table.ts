import { NgTemplateOutlet } from '@angular/common';
import { Component, computed, contentChildren, effect, input, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { DataTableCell } from './data-table-cell';

export interface DataTableColumn<T> {
  /** Property shown by default and key used by appDataTableCell templates. */
  key: keyof T & string;
  header: string;
  align?: 'start' | 'center' | 'end';
}

/**
 * Card-styled table with client-side pagination ("Mostrando 1–10 de 25 …").
 * Plain values are rendered as text; columns with an appDataTableCell template use it instead.
 */
@Component({
  selector: 'app-data-table',
  imports: [NgTemplateOutlet, MatButtonModule, MatIconModule],
  template: `
    <div class="scroll">
      <table>
        @if (caption()) {
          <caption class="re-visually-hidden">
            {{
              caption()
            }}
          </caption>
        }
        <thead>
          <tr>
            @for (column of columns(); track column.key) {
              <th scope="col" [class]="'align-' + (column.align ?? 'start')">
                {{ column.header }}
              </th>
            }
          </tr>
        </thead>
        <tbody>
          @for (row of pageRows(); track $index) {
            <tr>
              @for (column of columns(); track column.key) {
                <td [class]="'align-' + (column.align ?? 'start')">
                  @if (templates()[column.key]; as template) {
                    <ng-container
                      [ngTemplateOutlet]="template"
                      [ngTemplateOutletContext]="{ $implicit: row }"
                    />
                  } @else {
                    {{ row[column.key] }}
                  }
                </td>
              }
            </tr>
          } @empty {
            <tr>
              <td class="empty" [attr.colspan]="columns().length">{{ emptyMessage() }}</td>
            </tr>
          }
        </tbody>
      </table>
    </div>

    @if (rows().length > 0) {
      <div class="footer">
        <span class="range">
          Mostrando <strong>{{ rangeStart() }}–{{ rangeEnd() }}</strong> de
          <strong>{{ rows().length }}</strong> {{ itemLabel() }}
        </span>
        <div class="pager">
          <button
            mat-icon-button
            type="button"
            aria-label="Página anterior"
            [disabled]="pageIndex() === 0"
            (click)="goTo(pageIndex() - 1)"
          >
            <mat-icon>chevron_left</mat-icon>
          </button>
          <span class="page" aria-live="polite">{{ pageIndex() + 1 }} / {{ pageCount() }}</span>
          <button
            mat-icon-button
            type="button"
            aria-label="Página siguiente"
            [disabled]="pageIndex() >= pageCount() - 1"
            (click)="goTo(pageIndex() + 1)"
          >
            <mat-icon>chevron_right</mat-icon>
          </button>
        </div>
      </div>
    }
  `,
  styles: `
    :host {
      display: block;
      overflow: hidden;
      background: var(--re-card);
      border: 1px solid var(--re-gray-200);
      border-radius: var(--re-radius-lg);
      box-shadow: var(--re-shadow-sm);
    }
    .scroll {
      overflow-x: auto;
    }
    table {
      width: 100%;
      border-collapse: collapse;
      font-size: 13px;
    }
    th {
      padding: 12px 20px;
      color: var(--re-text-muted);
      background: var(--re-gray-50);
      border-bottom: 1px solid var(--re-gray-200);
      font-size: 11px;
      font-weight: 700;
      letter-spacing: 0.05em;
      text-transform: uppercase;
      white-space: nowrap;
    }
    td {
      padding: 14px 20px;
      color: var(--re-text);
      border-bottom: 1px solid var(--re-gray-100);
      vertical-align: middle;
    }
    tbody tr:last-child td {
      border-bottom: none;
    }
    tbody tr:hover td {
      background: var(--re-gray-50);
    }
    .align-start {
      text-align: left;
    }
    .align-center {
      text-align: center;
    }
    .align-end {
      text-align: right;
    }
    .empty {
      padding: 32px 20px;
      color: var(--re-text-muted);
      text-align: center;
    }
    .footer {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: 8px;
      padding: 8px 12px 8px 20px;
      border-top: 1px solid var(--re-gray-200);
    }
    .range {
      color: var(--re-text-muted);
      font-size: 13px;
    }
    .range strong {
      color: var(--re-text);
    }
    .pager {
      display: flex;
      align-items: center;
      gap: 4px;
    }
    .page {
      min-width: 48px;
      padding: 2px 8px;
      color: var(--re-text-teal);
      border: 1px solid var(--re-teal);
      border-radius: var(--re-radius-sm);
      font-size: 12px;
      font-weight: 700;
      text-align: center;
    }
  `,
})
export class DataTable<T extends object> {
  readonly columns = input.required<readonly DataTableColumn<T>[]>();
  readonly rows = input.required<readonly T[]>();
  readonly pageSize = input(10);
  readonly caption = input<string>();
  readonly itemLabel = input('registros');
  readonly emptyMessage = input('No hay registros para mostrar.');

  private readonly cells = contentChildren(DataTableCell);
  protected readonly templates = computed(() =>
    Object.fromEntries(this.cells().map((cell) => [cell.column(), cell.template])),
  );

  protected readonly pageIndex = signal(0);
  protected readonly pageCount = computed(() =>
    Math.max(1, Math.ceil(this.rows().length / this.pageSize())),
  );
  protected readonly pageRows = computed(() => {
    const start = this.pageIndex() * this.pageSize();
    return this.rows().slice(start, start + this.pageSize());
  });
  protected readonly rangeStart = computed(() => this.pageIndex() * this.pageSize() + 1);
  protected readonly rangeEnd = computed(() =>
    Math.min(this.rows().length, (this.pageIndex() + 1) * this.pageSize()),
  );

  constructor() {
    // Keep the current page valid when the data shrinks (e.g. after filtering)
    effect(() => {
      if (this.pageIndex() > this.pageCount() - 1) {
        this.pageIndex.set(this.pageCount() - 1);
      }
    });
  }

  protected goTo(index: number): void {
    this.pageIndex.set(Math.min(Math.max(index, 0), this.pageCount() - 1));
  }
}
