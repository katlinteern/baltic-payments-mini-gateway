import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Standard page shell: heading (with optional actions), content area and footer. */
@Component({
  selector: 'ui-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <main class="workspace">
      @if (heading()) {
        <header class="page-heading">
          <h1>{{ heading() }}</h1>
          <ng-content select="[pageActions]" />
        </header>
      }
      <ng-content />
      <footer class="workspace-footer">
        <span>Demo environment · no real money is moved</span>
      </footer>
    </main>
  `,
  styleUrl: './page.component.scss',
})
export class PageComponent {
  readonly heading = input<string>('');
}
