import { Component } from '@angular/core';
import { PanelPlaceholder } from '../../shared/ui/panel-placeholder';

@Component({
  selector: 'app-physio-dashboard',
  imports: [PanelPlaceholder],
  template: `<app-panel-placeholder
    icon="physical_therapy"
    heading="Bienvenido, fisioterapeuta"
  />`,
})
export class PhysioDashboard {}
