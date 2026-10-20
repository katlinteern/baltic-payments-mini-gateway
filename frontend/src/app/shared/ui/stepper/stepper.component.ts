import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

/** Shows where the user is in a short, linear flow. Steps before {@code current} are done. */
@Component({
  selector: 'ui-stepper',
  imports: [MatIconModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './stepper.component.html',
  styleUrl: './stepper.component.scss',
})
export class StepperComponent {
  readonly steps = input.required<readonly string[]>();
  /** Zero-based index of the step the user is on. */
  readonly current = input.required<number>();
  readonly label = input('Progress');
}
