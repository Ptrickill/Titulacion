import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DataTable, DataTableColumn } from './data-table';
import { DataTableCell } from './data-table-cell';

interface Row {
  name: string;
  status: string;
}

@Component({
  imports: [DataTable, DataTableCell],
  template: `
    <app-data-table [columns]="columns" [rows]="rows()" [pageSize]="2" itemLabel="pacientes">
      <ng-template appDataTableCell="status" let-row>
        <span class="custom">[{{ row.status }}]</span>
      </ng-template>
    </app-data-table>
  `,
})
class Host {
  readonly columns: DataTableColumn<Row>[] = [
    { key: 'name', header: 'Paciente' },
    { key: 'status', header: 'Estado', align: 'center' },
  ];
  readonly rows = signal<Row[]>(
    ['A', 'B', 'C', 'D', 'E'].map((letter) => ({ name: `Paciente ${letter}`, status: 'Activo' })),
  );
}

describe('DataTable', () => {
  let fixture: ComponentFixture<Host>;
  let element: HTMLElement;

  const cellTexts = () =>
    Array.from(element.querySelectorAll('tbody tr td:first-child')).map((td) =>
      td.textContent?.trim(),
    );
  const button = (label: string) =>
    element.querySelector<HTMLButtonElement>(`button[aria-label="${label}"]`)!;
  const range = () => element.querySelector('.range')?.textContent?.replace(/\s+/g, ' ').trim();

  beforeEach(async () => {
    fixture = TestBed.createComponent(Host);
    element = fixture.nativeElement as HTMLElement;
    await fixture.whenStable();
  });

  it('renders headers, the first page and the range summary', () => {
    expect(Array.from(element.querySelectorAll('th')).map((th) => th.textContent?.trim())).toEqual([
      'Paciente',
      'Estado',
    ]);
    expect(cellTexts()).toEqual(['Paciente A', 'Paciente B']);
    expect(range()).toBe('Mostrando 1–2 de 5 pacientes');
    expect(button('Página anterior').disabled).toBe(true);
  });

  it('uses the custom cell template for its column', () => {
    expect(element.querySelector('tbody td.align-center .custom')?.textContent).toBe('[Activo]');
  });

  it('moves between pages', async () => {
    button('Página siguiente').click();
    await fixture.whenStable();
    expect(cellTexts()).toEqual(['Paciente C', 'Paciente D']);

    button('Página siguiente').click();
    await fixture.whenStable();
    expect(cellTexts()).toEqual(['Paciente E']);
    expect(range()).toBe('Mostrando 5–5 de 5 pacientes');
    expect(button('Página siguiente').disabled).toBe(true);
  });

  it('returns to a valid page when the data shrinks', async () => {
    button('Página siguiente').click();
    button('Página siguiente').click();
    await fixture.whenStable();

    fixture.componentInstance.rows.set([{ name: 'Paciente Z', status: 'Inactivo' }]);
    await fixture.whenStable();

    expect(cellTexts()).toEqual(['Paciente Z']);
  });

  it('shows the empty message and hides the pager without rows', async () => {
    fixture.componentInstance.rows.set([]);
    await fixture.whenStable();

    expect(element.querySelector('td.empty')?.textContent?.trim()).toBe(
      'No hay registros para mostrar.',
    );
    expect(element.querySelector('.footer')).toBeNull();
  });
});

@Component({
  imports: [DataTable],
  template: `
    <app-data-table
      [columns]="columns"
      [rows]="rows()"
      [pageSize]="2"
      [totalItems]="5"
      [(pageIndex)]="page"
      itemLabel="pacientes"
    />
  `,
})
class ServerHost {
  readonly columns: DataTableColumn<Row>[] = [{ key: 'name', header: 'Paciente' }];
  /** The server already sent only the current page. */
  readonly rows = signal<Row[]>([
    { name: 'Paciente A', status: 'Activo' },
    { name: 'Paciente B', status: 'Activo' },
  ]);
  readonly page = signal(0);
}

describe('DataTable with server-side pagination', () => {
  it('shows the rows as they come, counts the total and asks for other pages', async () => {
    const fixture = TestBed.createComponent(ServerHost);
    const element = fixture.nativeElement as HTMLElement;
    await fixture.whenStable();

    const rows = () =>
      Array.from(element.querySelectorAll('tbody td')).map((td) => td.textContent?.trim());
    expect(rows()).toEqual(['Paciente A', 'Paciente B']);
    expect(element.querySelector('.range')?.textContent?.replace(/\s+/g, ' ').trim()).toBe(
      'Mostrando 1–2 de 5 pacientes',
    );

    element.querySelector<HTMLButtonElement>('button[aria-label="Página siguiente"]')!.click();
    await fixture.whenStable();
    expect(fixture.componentInstance.page()).toBe(1);
    // Until the owner loads the new page, the table shows what it was given (never slices it)
    expect(rows()).toEqual(['Paciente A', 'Paciente B']);
    expect(element.querySelector('.page')?.textContent?.trim()).toBe('2 / 3');
  });
});
