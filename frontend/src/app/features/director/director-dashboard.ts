import { Component } from '@angular/core';
import { PanelPlaceholder } from '../../shared/ui/panel-placeholder';

@Component({
  selector: 'app-director-dashboard',
  imports: [PanelPlaceholder],
  template: `<app-panel-placeholder
    icon="monitoring"
    heading="Bienvenido a dirección operativa"
  />`,
})
export class DirectorDashboard {}
