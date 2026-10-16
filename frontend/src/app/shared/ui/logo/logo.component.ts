import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Bank wordmark with its symbol; `inverse` is for use on dark backgrounds. */
@Component({
  selector: 'ui-logo',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span class="logo" [class.inverse]="inverse()">
      <svg class="mark" viewBox="0 0 32 32" aria-hidden="true" focusable="false">
        <rect width="32" height="32" rx="9" class="mark-bg" />
        <path d="M6 13.5c3-3.2 5.6-3.2 8.6 0s5.6 3.2 8.6 0c1.2-1.3 2.1-1.8 2.8-1.9" class="wave wave-top" />
        <path d="M6 20.5c3-3.2 5.6-3.2 8.6 0s5.6 3.2 8.6 0c1.2-1.3 2.1-1.8 2.8-1.9" class="wave wave-bottom" />
      </svg>
      <span class="word">Baltic<span class="word-light">Bank</span></span>
    </span>
  `,
  styleUrl: './logo.component.scss',
})
export class LogoComponent {
  readonly inverse = input(false);
}
