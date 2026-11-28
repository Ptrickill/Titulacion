import { BreakpointObserver, Breakpoints } from '@angular/cdk/layout';
import { DatePipe } from '@angular/common';
import { Component, computed, DestroyRef, effect, inject, viewChild } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatSidenavContent, MatSidenavModule } from '@angular/material/sidenav';
import { MatSnackBar } from '@angular/material/snack-bar';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter, map, timer } from 'rxjs';
import { AuthService } from '../../core/auth/auth-service';
import { IdleSession } from '../../core/auth/idle-session';
import { Role, ROLE_LABELS } from '../../core/auth/role';
import {
  NO_SECTION_ACCESS_MESSAGE,
  SectionAccessNotice,
} from '../../core/auth/section-access-notice';
import { APP_LOCALE } from '../../core/locale';
import { StatusBadge } from '../ui/status-badge';
import { IdleWarningChoice, IdleWarningDialog } from './idle-warning-dialog';
import { NAV_ITEMS, PANEL_LABELS, ROLE_TONES } from './nav-items';

/**
 * Authenticated layout: navy sidebar with the role menu, header with user, role and date,
 * and the centered content area where role pages are rendered. MEJ-06: it watches the inactivity of the session and
 * shows the warning before closing it ({@link IdleSession}).
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
  private readonly idle = inject(IdleSession);
  private readonly dialog = inject(MatDialog);
  private idleWarning: MatDialogRef<IdleWarningDialog, IdleWarningChoice> | null = null;
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
    // MEJ-06: close the session after a while without activity, warning before
    const destroyRef = inject(DestroyRef);
    this.idle.start(destroyRef);
    destroyRef.onDestroy(() => this.idleWarning?.close());
    effect(() => {
      if (this.idle.warning()) {
        this.openIdleWarning();
      } else {
        this.idleWarning?.close();
      }
    });

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
    this.idle.end();
    this.auth.logout();
    void this.router.navigate(['/login']);
  }

  private openIdleWarning(): void {
    if (this.idleWarning) {
      return;
    }
    this.idleWarning = this.dialog.open<IdleWarningDialog, void, IdleWarningChoice>(
      IdleWarningDialog,
      {
        disableClose: true,
        width: '440px',
        maxWidth: 'calc(100vw - 32px)',
        role: 'alertdialog',
      },
    );
    this.idleWarning.afterClosed().subscribe((choice) => {
      this.idleWarning = null;
      if (choice === 'stay') {
        this.idle.stayConnected();
      } else if (choice === 'logout') {
        this.logout();
      }
    });
  }

  private labelOf(role: Role | null, labels: Record<Role, string>): string {
    return role ? labels[role] : '';
  }
}
