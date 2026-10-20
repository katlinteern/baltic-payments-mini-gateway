import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Product wordmark with its symbol; `inverse` is for use on dark backgrounds. */
@Component({
  selector: 'ui-logo',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span class="logo" [class.inverse]="inverse()">
      <svg class="mark" viewBox="0 0 32 32" aria-hidden="true" focusable="false">
        <rect width="32" height="32" rx="9" class="mark-bg" />
        <path d="M11.5 9v14" class="stroke stroke-top" />
        <path d="M11.5 9h5a3.5 3.5 0 0 1 0 7h-5" class="stroke stroke-top" />
        <path d="M11.5 16h5.8a3.5 3.5 0 0 1 0 7h-5.8" class="stroke stroke-bottom" />
      </svg>
      <span class="word">baltic<span class="word-light">pay</span></span>
    </span>
  `,
  styleUrl: './logo.component.scss',
})
export class LogoComponent {
  readonly inverse = input(false);
}
