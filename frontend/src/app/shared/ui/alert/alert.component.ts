import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

export type AlertKind = 'error' | 'warning' | 'info' | 'success';

const ICONS: Record<AlertKind, string> = {
  error: 'error_outline',
  warning: 'warning_amber',
  info: 'info_outline',
  success: 'check_circle_outline',
};

@Component({
  selector: 'ui-alert',
  imports: [MatIconModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './alert.component.html',
  styleUrl: './alert.component.scss',
})
export class AlertComponent {
  readonly kind = input<AlertKind>('error');
  protected readonly role = computed(() => (this.kind() === 'error' ? 'alert' : 'status'));
  protected readonly icon = computed(() => ICONS[this.kind()]);
}
