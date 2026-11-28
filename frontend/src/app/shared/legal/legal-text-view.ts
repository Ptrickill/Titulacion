import { Component, computed, input } from '@angular/core';
import { LegalText } from './legal-api';
import { parseLegalMarkdown } from './legal-markdown';

/**
 * A legal text (MEJ-06): its Markdown rendered as headings, paragraphs, notices and lists, with its version. Used by
 * the privacy notice, the registration of a patient and the portal.
 */
@Component({
  selector: 'app-legal-text-view',
  template: `
    @for (block of blocks(); track $index) {
      @switch (block.kind) {
        @case ('heading') {
          <h3 class="heading">{{ block.text }}</h3>
        }
        @case ('notice') {
          <p class="notice">
            @for (part of block.inlines; track $index) {
              @if (part.bold) {
                <strong>{{ part.text }}</strong>
              } @else {
                {{ part.text }}
              }
            }
          </p>
        }
        @case ('list') {
          <ul class="list">
            @for (item of block.items; track $index) {
              <li>
                @for (part of item; track $index) {
                  @if (part.bold) {
                    <strong>{{ part.text }}</strong>
                  } @else {
                    {{ part.text }}
                  }
                }
              </li>
            }
          </ul>
        }
        @default {
          <p class="paragraph">
            @for (part of block.inlines; track $index) {
              @if (part.bold) {
                <strong>{{ part.text }}</strong>
              } @else {
                {{ part.text }}
              }
            }
          </p>
        }
      }
    }
    @if (showVersion()) {
      <p class="version">Versión {{ text().version }}</p>
    }
  `,
  styles: `
    :host {
      display: block;
      color: var(--re-text);
      font-size: 0.95rem;
      line-height: 1.55;
      overflow-wrap: anywhere;
    }
    .heading {
      margin: var(--re-space-5) 0 var(--re-space-2);
      font-size: 1rem;
      color: var(--re-navy);
    }
    .paragraph,
    .list {
      margin: 0 0 var(--re-space-3);
    }
    .list {
      padding-left: var(--re-space-5);
    }
    .notice {
      margin: 0 0 var(--re-space-4);
      padding: var(--re-space-3) var(--re-space-4);
      border: 1px solid var(--re-warning-border);
      border-radius: var(--re-radius-sm);
      background: var(--re-warning-bg);
      color: var(--re-warning);
    }
    .version {
      margin: var(--re-space-4) 0 0;
      color: var(--re-text-muted);
      font-size: 0.85rem;
    }
  `,
})
export class LegalTextView {
  readonly text = input.required<LegalText>();
  readonly showVersion = input(true);

  protected readonly blocks = computed(() => parseLegalMarkdown(this.text().content));
}
