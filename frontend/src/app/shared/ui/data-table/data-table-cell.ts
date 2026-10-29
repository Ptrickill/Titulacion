import { Directive, inject, input, TemplateRef } from '@angular/core';

export interface DataTableCellContext<T> {
  $implicit: T;
}

/**
 * Custom cell template for one column of {@link DataTable}:
 *
 * <ng-template appDataTableCell="status" let-row>
 *   <app-status-badge [label]="row.status" />
 * </ng-template>
 */
@Directive({ selector: 'ng-template[appDataTableCell]' })
export class DataTableCell<T = unknown> {
  readonly column = input.required<string>({ alias: 'appDataTableCell' });
  readonly template = inject<TemplateRef<DataTableCellContext<T>>>(TemplateRef);
}
