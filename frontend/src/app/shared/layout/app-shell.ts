import { BreakpointObserver, Breakpoints } from '@angular/cdk/layout';
import { DatePipe } from '@angular/common';
import { Component, computed, effect, inject, viewChild } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSidenavContent, MatSidenavModule } from '@angular/material/sidenav';
import { MatSnackBar } from '@angular/material/snack-bar';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter, map, timer } from 'rxjs';
import { AuthService } from '../../core/auth/auth-service';
import { Role, ROLE_LABELS } from '../../core/auth/role';
import {
  NO_SECTION_ACCESS_MESSAGE,
  SectionAccessNotice,
} from '../../core/auth/section-access-notice';
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
    StatusBadge,
  ],
  templateUrl: './app-shell.html',
  styleUrl: './app-shell.scss',
})
export class AppShell {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  /** The scrolling area of the pages (not the window), see the constructor. */
  private readonly content = viewChild(MatSidenavContent);

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

  protected readonly role = this.auth.role;
  protected readonly userName = computed(() => this.auth.user()?.fullName ?? '');
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

  constructor() {
    // A role guard sent the user back to their own panel (HU-04): tell them why
    const notice = inject(SectionAccessNotice);
    const snackBar = inject(MatSnackBar);
    effect(() => {
      if (notice.isPending() && notice.consume()) {
        snackBar.open(NO_SECTION_ACCESS_MESSAGE, 'Cerrar', { duration: 5000 });
      }
    });

    // Pages scroll inside the sidenav content, which the router does not reset: a new page starts at the top
    this.router.events
      .pipe(
        filter((event) => event instanceof NavigationEnd),
        takeUntilDestroyed(),
      )
      .subscribe(() => {
        const content = this.content()?.getElementRef().nativeElement;
        if (content) {
          content.scrollTop = 0;
        }
      });
  }

  protected logout(): void {
    this.auth.logout();
    void this.router.navigate(['/login']);
  }

  private labelOf(role: Role | null, labels: Record<Role, string>): string {
    return role ? labels[role] : '';
  }
}
