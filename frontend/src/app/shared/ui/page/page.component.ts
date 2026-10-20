import { ChangeDetectionStrategy, Component, input } from '@angular/core';

@Component({
  selector: 'ui-page',
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
    </main>
  `,
  styleUrl: './page.component.scss',
})
export class PageComponent {
  readonly heading = input<string>('');
}
