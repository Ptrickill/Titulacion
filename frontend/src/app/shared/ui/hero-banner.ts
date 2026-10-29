import { Component, input } from '@angular/core';

/** Navy-to-teal welcome banner shown at the top of the role home pages. */
@Component({
  selector: 'app-hero-banner',
  template: `
    @if (eyebrow()) {
      <span class="eyebrow">{{ eyebrow() }}</span>
    }
    <h1 class="title">{{ title() }}</h1>
    @if (message()) {
      <p class="message">{{ message() }}</p>
    }
  `,
  styles: `
    :host {
      display: block;
      margin-bottom: 24px;
      padding: 28px 28px 32px;
      color: #ffffff;
      background: linear-gradient(110deg, var(--re-navy) 55%, #064e46);
      border-radius: var(--re-radius-lg);
      box-shadow: var(--re-shadow-md);
    }
    .eyebrow {
      display: inline-block;
      padding: 2px 10px;
      color: var(--re-teal);
      background: rgb(20 184 166 / 15%);
      border: 1px solid rgb(20 184 166 / 35%);
      border-radius: var(--re-radius-pill);
      font-size: 11px;
      font-weight: 700;
      letter-spacing: 0.05em;
      text-transform: uppercase;
    }
    .title {
      margin: 8px 0 6px;
      font-size: 26px;
      font-weight: 800;
      line-height: 1.2;
    }
    .message {
      margin: 0;
      color: var(--re-gray-300);
      font-size: 14px;
    }
  `,
})
export class HeroBanner {
  readonly eyebrow = input<string>();
  readonly title = input.required<string>();
  readonly message = input<string>();
}
