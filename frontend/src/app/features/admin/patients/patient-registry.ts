import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router } from '@angular/router';
import { EmptyState } from '../../../shared/ui/empty-state';
import { PageHeader } from '../../../shared/ui/page-header';
import { openRegisterPatientDialog } from './register-patient-dialog';

/**
 * "Registro y Control de Pacientes" (prototype 10). HU-05 registers new patients and opens their record; the
 * patient search and edit list replaces the empty state in HU-06.
 */
@Component({
  selector: 'app-patient-registry',
  imports: [MatButtonModule, MatIconModule, PageHeader, EmptyState],
  template: `
    <app-page-header
      title="Registro y Control de Pacientes"
      subtitle="Admisión de pacientes, datos personales y actualización de expedientes"
    >
      <button mat-flat-button actions type="button" (click)="openRegisterDialog()">
        <mat-icon aria-hidden="true">add</mat-icon>
        Registrar Nuevo Paciente
      </button>
    </app-page-header>

    <app-empty-state
      icon="person_add"
      title="Admisión de pacientes"
      message="Registre los datos de admisión y personales de un paciente nuevo. Al guardar se abre su registro con el número de historia clínica y la edad calculada."
    >
      <button mat-stroked-button type="button" (click)="openRegisterDialog()">
        Registrar Nuevo Paciente
      </button>
    </app-empty-state>
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      gap: 20px;
    }
  `,
})
export class PatientRegistry {
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly router = inject(Router);

  protected openRegisterDialog(): void {
    openRegisterPatientDialog(this.dialog).subscribe((patient) => {
      if (patient) {
        this.snackBar.open('Paciente registrado correctamente.', 'Cerrar', { duration: 5000 });
        void this.router.navigate(['/admin/patients', patient.id]);
      }
    });
  }
}
