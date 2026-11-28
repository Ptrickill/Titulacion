import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { fakeDataRequest } from '../../../shared/legal/legal-testing';
import { ErasureDialog, ErasureDialogData } from './erasure-dialog';

/** MEJ-06: accepting is irreversible, so it needs the patient's national ID typed again. */
describe('ErasureDialog', () => {
  let fixture: ComponentFixture<ErasureDialog>;
  let element: HTMLElement;
  const ref = { close: vi.fn() };

  async function open(decision: 'accept' | 'reject'): Promise<void> {
    const data: ErasureDialogData = {
      decision,
      erasure: {
        request: fakeDataRequest(9, { type: 'ERASURE' }),
        patientId: 25,
        patientName: 'Rosa Elena Ortiz Vega',
        nationalId: '1710034065',
      },
    };
    TestBed.configureTestingModule({
      providers: [
        { provide: MatDialogRef, useValue: ref },
        { provide: MAT_DIALOG_DATA, useValue: data },
      ],
    });
    fixture = TestBed.createComponent(ErasureDialog);
    element = fixture.nativeElement as HTMLElement;
    await fixture.whenStable();
  }

  async function type(selector: string, value: string): Promise<void> {
    const input = element.querySelector<HTMLInputElement | HTMLTextAreaElement>(selector)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    await fixture.whenStable();
  }

  const confirm = () =>
    Array.from(element.querySelectorAll<HTMLButtonElement>('mat-dialog-actions button')).at(-1)!;

  beforeEach(() => ref.close.mockReset());

  it('enables the acceptance only with the same national ID and closes with the decision', async () => {
    await open('accept');
    expect(element.textContent).toContain('Es irreversible');
    expect(element.textContent).toContain('La historia clínica se conserva');
    expect(confirm().disabled).toBe(true);

    await type('#erasure-national-id', '1710034066');
    expect(confirm().disabled).toBe(true);
    await type('#erasure-national-id', '1710034065');
    await type('#erasure-response', '  Se bloquearon sus datos.  ');
    expect(confirm().disabled).toBe(false);
    confirm().click();

    expect(ref.close).toHaveBeenCalledWith({
      decision: 'accept',
      nationalId: '1710034065',
      response: 'Se bloquearon sus datos.',
    });
  });

  it('rejects with the response and without the national ID', async () => {
    await open('reject');
    expect(element.querySelector('#erasure-national-id')).toBeNull();

    confirm().click();
    expect(ref.close).not.toHaveBeenCalled();
    await type('#erasure-response', 'Obligación legal de conservar.');
    confirm().click();

    expect(ref.close).toHaveBeenCalledWith({
      decision: 'reject',
      response: 'Obligación legal de conservar.',
    });
  });
});
