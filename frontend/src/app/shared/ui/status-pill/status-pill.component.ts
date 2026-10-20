import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Small status badge; the text is always rendered so colour is never the only signal. */
@Component({
  selector: 'ui-status-pill',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<span class="pill" [class]="tone()"><ng-content /></span>`,
  styleUrl: './status-pill.component.scss',
})
export class StatusPillComponent {
  readonly tone = input<'info' | 'success' | 'danger'>('info');
}
