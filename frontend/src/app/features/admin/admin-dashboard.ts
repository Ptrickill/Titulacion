import { Component } from '@angular/core';
import { PanelPlaceholder } from '../../shared/ui/panel-placeholder';

@Component({
  selector: 'app-admin-dashboard',
  imports: [PanelPlaceholder],
  template: `<app-panel-placeholder
    icon="admin_panel_settings"
    heading="Bienvenido, administrador"
  />`,
})
export class AdminDashboard {}
