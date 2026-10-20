import { ChangeDetectionStrategy, Component, input } from '@angular/core';

@Component({
  selector: 'ui-section-head',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="section-head" [class.bordered]="bordered()">
      <h2>{{ heading() }}</h2>
      <ng-content />
    </div>
  `,
  styleUrl: './section-head.component.scss',
})
export class SectionHeadComponent {
  readonly heading = input.required<string>();
  readonly bordered = input(false);
}
