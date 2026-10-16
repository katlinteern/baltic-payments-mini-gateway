import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Heading of a card section with an optional trailing slot (counts, chips, buttons). */
@Component({
  selector: 'ui-section-head',
  standalone: true,
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
