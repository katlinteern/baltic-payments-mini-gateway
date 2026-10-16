import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { afterEach, describe, expect, it } from 'vitest';
import { AlertComponent, EmptyStateComponent, PageComponent, SectionHeadComponent, StatusPillComponent } from './index';

@Component({
  standalone: true,
  imports: [AlertComponent, EmptyStateComponent, PageComponent, SectionHeadComponent, StatusPillComponent],
  template: `
    <ui-page eyebrow="Home / Test" heading="Test page" description="Page description">
      <a pageActions href="/somewhere">Action</a>
      <ui-alert>Something failed</ui-alert>
      <ui-alert kind="success">Saved</ui-alert>
      <ui-section-head heading="Section" description="Section description" [bordered]="true">
        <span class="trailing">3 ITEMS</span>
      </ui-section-head>
      <ui-empty-state>Nothing here</ui-empty-state>
      <ui-status-pill tone="danger">REJECTED</ui-status-pill>
    </ui-page>
  `,
})
class HostComponent {}

describe('shared UI components', () => {
  afterEach(() => TestBed.resetTestingModule());

  function render(): HTMLElement {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('ui-page renders heading, description, projected actions, content and footer', () => {
    const element = render();

    expect(element.querySelector('main')).not.toBeNull();
    expect(element.querySelector('h1')?.textContent).toBe('Test page');
    expect(element.querySelector('.eyebrow')?.textContent).toBe('Home / Test');
    expect(element.querySelector('.page-heading a')?.textContent).toBe('Action');
    expect(element.querySelector('footer')?.textContent).toContain('Demonstration only');
  });

  it('ui-alert announces errors assertively and success politely', () => {
    const element = render();

    expect(element.querySelector('[role="alert"]')?.textContent).toBe('Something failed');
    expect(element.querySelector('[role="status"]')?.textContent).toBe('Saved');
  });

  it('ui-section-head shows text, projected trailing content and the optional border', () => {
    const element = render();

    expect(element.querySelector('h2')?.textContent).toBe('Section');
    expect(element.querySelector('.section-head.bordered .trailing')?.textContent).toBe('3 ITEMS');
  });

  it('ui-empty-state and ui-status-pill render their content', () => {
    const element = render();

    expect(element.querySelector('.empty-state')?.textContent).toBe('Nothing here');
    expect(element.querySelector('.pill.danger')?.textContent).toBe('REJECTED');
  });
});
