import { Component } from '@angular/core';
import { PanelPlaceholder } from '../../shared/ui/panel-placeholder';

@Component({
  selector: 'app-patient-dashboard',
  imports: [PanelPlaceholder],
  template: `<app-panel-placeholder icon="person" heading="Bienvenido a tu portal" />`,
})
export class PatientDashboard {}
