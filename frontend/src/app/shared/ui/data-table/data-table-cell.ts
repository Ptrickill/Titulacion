import { Directive, inject, input, TemplateRef } from '@angular/core';

export interface DataTableCellContext<T> {
  $implicit: T;
}

/**
 * Custom cell template for one column of {@link DataTable}:
 *
 * <ng-template appDataTableCell="status" [appDataTableCellOf]="rows" let-row>
 *   <app-status-badge [label]="row.status" />
 * </ng-template>
 *
 * `appDataTableCellOf` is optional and only used for type checking: it gives `row` the row type.
 */
@Directive({ selector: 'ng-template[appDataTableCell]' })
// Without appDataTableCellOf the row stays untyped, as in templates written before the input existed
export class DataTableCell<T = any> {
  readonly column = input.required<string>({ alias: 'appDataTableCell' });
  readonly rows = input<readonly T[]>(undefined, { alias: 'appDataTableCellOf' });
  readonly template = inject<TemplateRef<DataTableCellContext<T>>>(TemplateRef);

  static ngTemplateContextGuard<T>(
    _directive: DataTableCell<T>,
    context: unknown,
  ): context is DataTableCellContext<T> {
    return true;
  }
}
