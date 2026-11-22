import { DOCUMENT, Location } from '@angular/common';
import { afterNextRender, DestroyRef, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

/**
 * Protects the token of an emailed link (MEJ-01) while its page is open:
 * - Referrer-Policy "no-referrer" (a meta element in the head, removed when the page closes), so no request
 *   sends the address of the page in the Referer header;
 * - reads the token from the query string once and removes it from the address bar with history.replaceState
 *   (through Angular's Location), so it stays only in memory: not in the history, a bookmark or a shared screen.
 *
 * Call it from an injection context (a field initializer of the page). Returns the token, or null if the link
 * had none.
 */
export function takeLinkToken(): string | null {
  const document = inject(DOCUMENT);
  const meta = document.createElement('meta');
  meta.name = 'referrer';
  meta.content = 'no-referrer';
  document.head.appendChild(meta);
  inject(DestroyRef).onDestroy(() => meta.remove());

  const route = inject(ActivatedRoute);
  const token = route.snapshot.queryParamMap.get('token');
  if (route.snapshot.queryParamMap.keys.length > 0) {
    const location = inject(Location);
    // After the first render the navigation has finished: the router no longer writes the URL with the token
    afterNextRender(() => location.replaceState(location.path().split('?')[0]));
  }
  return token?.trim() || null;
}
