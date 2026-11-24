import { Component, computed, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RouterLink } from '@angular/router';
import { StatusBadge } from '../../../shared/ui/status-badge';
import { PersonAccount, PersonType } from './people-api';
import { ACCOUNT_STATUS_BADGES } from './people-labels';

/**
 * The account of a person in its record (MEJ-02). A patient without one offers "Crear acceso al portal": the account
 * takes the record's email and names, nothing is typed. With an account it shows its status and, while it waits for
 * its owner, "Reenviar invitación" (activation) or "Reenviar verificación" (a new email to confirm). Activating or
 * deactivating stays in "Gestión de Usuarios".
 */
@Component({
  selector: 'app-person-account-card',
  imports: [RouterLink, MatButtonModule, MatIconModule, MatProgressSpinnerModule, StatusBadge],
  template: `
    <section class="account" [attr.aria-labelledby]="headingId">
      <header>
        <mat-icon aria-hidden="true">{{
          type() === 'PATIENT' ? 'devices' : 'manage_accounts'
        }}</mat-icon>
        <h2 [id]="headingId">
          {{ type() === 'PATIENT' ? 'Acceso al portal' : 'Cuenta de acceso' }}
        </h2>
        @if (account(); as current) {
          <app-status-badge class="status" [label]="badge().label" [tone]="badge().tone" />
        }
      </header>

      @if (account(); as current) {
        <p class="text">
          Correo de la cuenta: <strong>{{ email() }}</strong>
        </p>
        @if (current.emailChangePending) {
          <p class="note" role="status">
            <mat-icon aria-hidden="true">mark_email_unread</mat-icon>
            Cambio de correo por confirmar: hasta que su titular abra el enlace, la cuenta sigue
            ingresando con el correo anterior.
          </p>
        }
        <div class="actions">
          @if (current.status === 'PENDING_ACTIVATION') {
            <button
              mat-stroked-button
              type="button"
              class="resend"
              [disabled]="busy()"
              (click)="resend.emit()"
            >
              @if (busy()) {
                <mat-spinner diameter="16" aria-hidden="true" />
              } @else {
                <mat-icon aria-hidden="true">forward_to_inbox</mat-icon>
              }
              Reenviar invitación
            </button>
          } @else if (current.emailChangePending && current.status === 'ACTIVE') {
            <button
              mat-stroked-button
              type="button"
              class="resend"
              [disabled]="busy()"
              (click)="resend.emit()"
            >
              @if (busy()) {
                <mat-spinner diameter="16" aria-hidden="true" />
              } @else {
                <mat-icon aria-hidden="true">forward_to_inbox</mat-icon>
              }
              Reenviar verificación
            </button>
          }
          <a mat-button class="manage" routerLink="/admin/users">Gestión de Usuarios</a>
        </div>
      } @else if (type() === 'PATIENT') {
        <p class="text">
          Aún no tiene acceso al portal. Se creará con su correo <strong>{{ email() }}</strong> y
          sus nombres del registro, sin escribirlos, y recibirá el enlace para activarlo.
        </p>
        <div class="actions">
          <button
            mat-flat-button
            type="button"
            class="create"
            [disabled]="busy()"
            (click)="create.emit()"
          >
            @if (busy()) {
              <mat-spinner diameter="16" aria-hidden="true" />
            } @else {
              <mat-icon aria-hidden="true">person_add</mat-icon>
            }
            Crear acceso al portal
          </button>
        </div>
      }
    </section>
  `,
  styles: `
    .account {
      display: flex;
      flex-direction: column;
      gap: 10px;
      padding: 20px 24px;
      background: var(--re-card);
      border: 1px solid var(--re-gray-200);
      border-radius: var(--re-radius-lg);
      box-shadow: var(--re-shadow-sm);
    }

    header {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 8px 10px;

      mat-icon {
        color: var(--re-text-teal);
      }
    }

    h2 {
      margin: 0;
      color: var(--re-navy);
      font-size: 15px;
      font-weight: 700;
    }

    .text {
      margin: 0;
      color: var(--re-text);
      font-size: 14px;
      overflow-wrap: anywhere;
    }

    .note {
      display: flex;
      gap: 8px;
      margin: 0;
      padding: 10px 12px;
      color: var(--re-info);
      background: var(--re-info-bg);
      border: 1px solid var(--re-info-border);
      border-radius: var(--re-radius-sm);
      font-size: 13px;

      mat-icon {
        flex-shrink: 0;
      }
    }

    .actions {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
    }

    .resend {
      color: var(--re-text-teal);
      border-color: var(--re-teal-100);
      background: var(--re-teal-50);
    }
  `,
})
export class PersonAccountCard {
  private static nextId = 0;

  readonly type = input.required<PersonType>();
  readonly account = input<PersonAccount | null>(null);
  /** The record's email: the account's, or the one a new account will take. */
  readonly email = input.required<string>();
  readonly busy = input(false);
  readonly create = output<void>();
  readonly resend = output<void>();

  protected readonly headingId = `person-account-${PersonAccountCard.nextId++}`;
  protected readonly badge = computed(
    () => ACCOUNT_STATUS_BADGES[this.account()?.status ?? 'NONE'],
  );
}
