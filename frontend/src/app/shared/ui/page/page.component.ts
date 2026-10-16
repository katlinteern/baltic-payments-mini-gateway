import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

/** Standard page shell: heading block (with optional actions), content area and footer. */
@Component({
  selector: 'ui-page',
  standalone: true,
  imports: [MatIconModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <main class="workspace">
      <header class="page-heading">
        <div>
          <span class="eyebrow">{{ eyebrow() }}</span>
          <h1>{{ heading() }}</h1>
          @if (description()) {
            <p>{{ description() }}</p>
          }
        </div>
        <ng-content select="[pageActions]" />
      </header>
      <ng-content />
      <footer class="workspace-footer">
        <span>MINI-GATEWAY · SECURE PAYMENTS</span>
        <span>SESSION ENCRYPTED <mat-icon>lock</mat-icon></span>
      </footer>
    </main>
  `,
  styleUrl: './page.component.scss',
})
export class PageComponent {
  readonly eyebrow = input.required<string>();
  readonly heading = input.required<string>();
  readonly description = input<string>();
}
