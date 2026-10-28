import { TestBed } from '@angular/core/testing';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { MatDialog } from '@angular/material/dialog';
import { firstValueFrom } from 'rxjs';
import { openConfirmDialog } from './confirm-dialog';

describe('ConfirmDialog', () => {
  let dialog: MatDialog;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [{ provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } }],
    });
    dialog = TestBed.inject(MatDialog);
  });

  afterEach(() => dialog.closeAll());

  function overlayButton(text: string): HTMLButtonElement {
    const buttons = document.querySelectorAll<HTMLButtonElement>('.cdk-overlay-container button');
    return Array.from(buttons).find((button) => button.textContent?.trim() === text)!;
  }

  async function open(tone: 'primary' | 'danger' = 'primary') {
    const results: boolean[] = [];
    openConfirmDialog(dialog, {
      title: 'Guardar cambios',
      message: '¿Desea guardar los cambios?',
      confirmLabel: 'Guardar',
      tone,
    }).subscribe((value) => results.push(value));
    const ref = dialog.openDialogs[0];
    // Zoneless app: render the dialog content explicitly, there is no fixture to do it
    TestBed.tick();
    await firstValueFrom(ref.afterOpened());
    return { results, closed: firstValueFrom(ref.afterClosed()) };
  }

  it('shows title, message and the Spanish default cancel label', async () => {
    await open();
    const overlay = document.querySelector('.cdk-overlay-container')!;

    expect(overlay.querySelector('h2')?.textContent).toBe('Guardar cambios');
    expect(overlay.querySelector('.message')?.textContent).toBe('¿Desea guardar los cambios?');
    expect(overlayButton('Cancelar')).toBeTruthy();
  });

  it('emits true when the user confirms', async () => {
    const { results, closed } = await open();

    overlayButton('Guardar').click();
    await closed;

    expect(results).toEqual([true]);
  });

  it('emits false when the user cancels', async () => {
    const { results, closed } = await open();

    overlayButton('Cancelar').click();
    await closed;

    expect(results).toEqual([false]);
  });

  it('marks the confirm button as destructive for the danger tone', async () => {
    await open('danger');

    expect(overlayButton('Guardar').classList).toContain('danger');
  });
});
