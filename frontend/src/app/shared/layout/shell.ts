import { BreakpointObserver, Breakpoints } from '@angular/cdk/layout';
import { Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import {
  ActivatedRouteSnapshot,
  NavigationEnd,
  Router,
  RouterLink,
  RouterLinkActive,
  RouterOutlet,
} from '@angular/router';
import { filter, map } from 'rxjs';
import { AuthSession } from '../../core/auth/auth-session';
import { ROLE_LABELS } from '../../core/auth/role';
import { NAV_ITEMS } from './nav-items';

/** Authenticated layout: navy sidebar with the role's navigation, page header and content area. */
@Component({
  selector: 'app-shell',
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatSidenavModule,
    MatToolbarModule,
    MatListModule,
    MatIconModule,
    MatButtonModule,
  ],
  templateUrl: './shell.html',
  styleUrl: './shell.scss',
})
export class Shell {
  private readonly session = inject(AuthSession);
  private readonly router = inject(Router);

  protected readonly isHandset = toSignal(
    inject(BreakpointObserver)
      .observe(Breakpoints.Handset)
      .pipe(map((result) => result.matches)),
    { initialValue: false },
  );

  protected readonly userName = computed(() => this.session.session()?.fullName ?? '');
  protected readonly roleLabel = computed(() => {
    const role = this.session.role();
    return role ? ROLE_LABELS[role] : '';
  });
  protected readonly navItems = computed(() => {
    const role = this.session.role();
    return role ? NAV_ITEMS[role] : [];
  });

  protected readonly pageTitle = toSignal(
    this.router.events.pipe(
      filter((event) => event instanceof NavigationEnd),
      map(() => deepestTitle(this.router.routerState.snapshot.root)),
    ),
    { initialValue: deepestTitle(this.router.routerState.snapshot.root) },
  );

  protected logout(): void {
    this.session.logout();
    void this.router.navigate(['/login']);
  }
}

function deepestTitle(route: ActivatedRouteSnapshot): string {
  let current = route;
  while (current.firstChild) {
    current = current.firstChild;
  }
  return current.title ?? '';
}
