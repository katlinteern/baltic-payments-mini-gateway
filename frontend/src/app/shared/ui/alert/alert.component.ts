import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

export type AlertKind = 'error' | 'success';

/** Inline feedback banner. Errors are announced assertively (role=alert), success politely (role=status). */
@Component({
  selector: 'ui-alert',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<div class="alert" [class]="kind()" [attr.role]="role()"><ng-content /></div>`,
  styleUrl: './alert.component.scss',
})
export class AlertComponent {
  readonly kind = input<AlertKind>('error');
  protected readonly role = computed(() => (this.kind() === 'error' ? 'alert' : 'status'));
}
