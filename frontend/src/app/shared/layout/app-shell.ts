import { BreakpointObserver, Breakpoints } from '@angular/cdk/layout';
import { DatePipe } from '@angular/common';
import { Component, computed, inject, isDevMode } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatSidenavModule } from '@angular/material/sidenav';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { map, timer } from 'rxjs';
import { AuthSession } from '../../core/auth/auth-session';
import { Role, ROLE_HOME, ROLE_LABELS, ROLES } from '../../core/auth/role';
import { APP_LOCALE } from '../../core/locale';
import { StatusBadge } from '../ui/status-badge';
import { NAV_ITEMS, PANEL_LABELS, ROLE_TONES } from './nav-items';

/**
 * Authenticated layout: navy sidebar with the role menu, header with user, role and date,
 * and the centered content area where role pages are rendered.
 */
@Component({
  selector: 'app-shell',
  imports: [
    DatePipe,
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatSidenavModule,
    MatIconModule,
    MatButtonModule,
    MatMenuModule,
    StatusBadge,
  ],
  templateUrl: './app-shell.html',
  styleUrl: './app-shell.scss',
})
export class AppShell {
  private readonly session = inject(AuthSession);
  private readonly router = inject(Router);

  protected readonly locale = APP_LOCALE;
  protected readonly isHandset = toSignal(
    inject(BreakpointObserver)
      .observe([Breakpoints.Handset, Breakpoints.TabletPortrait])
      .pipe(map((result) => result.matches)),
    { initialValue: false },
  );
  /** Refreshed every minute so the header date stays correct past midnight. */
  protected readonly today = toSignal(timer(0, 60_000).pipe(map(() => new Date())), {
    initialValue: new Date(),
  });

  protected readonly role = this.session.role;
  protected readonly userName = computed(() => this.session.session()?.fullName ?? '');
  protected readonly initials = computed(() =>
    this.userName()
      .split(/\s+/)
      .filter(Boolean)
      .slice(0, 2)
      .map((word) => word[0].toUpperCase())
      .join(''),
  );
  protected readonly roleLabel = computed(() => this.labelOf(this.role(), ROLE_LABELS));
  protected readonly panelLabel = computed(() => this.labelOf(this.role(), PANEL_LABELS));
  protected readonly roleTone = computed(() => {
    const role = this.role();
    return role ? ROLE_TONES[role] : 'neutral';
  });
  protected readonly navItems = computed(() => {
    const role = this.role();
    return role ? NAV_ITEMS[role] : [];
  });

  // TODO(HU-01): remove the preview role switcher together with the login preview access
  protected readonly showRoleSwitcher = isDevMode();
  protected readonly roles = ROLES;
  protected readonly roleLabels = ROLE_LABELS;

  protected switchRole(role: Role): void {
    this.session.startPreviewSession(role);
    void this.router.navigateByUrl(ROLE_HOME[role]);
  }

  protected logout(): void {
    this.session.logout();
    void this.router.navigate(['/login']);
  }

  private labelOf(role: Role | null, labels: Record<Role, string>): string {
    return role ? labels[role] : '';
  }
}
