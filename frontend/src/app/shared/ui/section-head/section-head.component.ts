import { ChangeDetectionStrategy, Component, input } from '@angular/core';

@Component({
  selector: 'ui-section-head',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './section-head.component.html',
  styleUrl: './section-head.component.scss',
})
export class SectionHeadComponent {
  readonly heading = input.required<string>();
  readonly bordered = input(false);
}
