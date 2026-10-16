import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Standard page shell: heading block (with optional actions), content area and footer. */
@Component({
  selector: 'ui-page',
  standalone: true,
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
        <span>BALTIC PAYMENTS MINI-GATEWAY</span>
        <span>DEMONSTRATION ONLY</span>
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
