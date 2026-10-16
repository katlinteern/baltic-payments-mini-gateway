import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'ui-empty-state',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<div class="empty-state"><ng-content /></div>`,
  styleUrl: './empty-state.component.scss',
})
export class EmptyStateComponent {}
