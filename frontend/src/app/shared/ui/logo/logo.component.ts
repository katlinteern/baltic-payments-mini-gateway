import { ChangeDetectionStrategy, Component, input } from '@angular/core';

@Component({
  selector: 'ui-logo',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './logo.component.html',
  styleUrl: './logo.component.scss',
})
export class LogoComponent {
  readonly inverse = input(false);
}
