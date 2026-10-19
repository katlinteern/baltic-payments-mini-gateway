import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { afterEach, describe, expect, it } from 'vitest';
import {
  AlertComponent,
  EmptyStateComponent,
  PageComponent,
  SectionHeadComponent,
  StatusPillComponent,
  StepperComponent,
} from './index';

@Component({
  standalone: true,
  imports: [
    AlertComponent,
    EmptyStateComponent,
    PageComponent,
    SectionHeadComponent,
    StatusPillComponent,
    StepperComponent,
  ],
  template: `
    <ui-page heading="Test page">
      <a pageActions href="/somewhere">Action</a>
      <ui-alert>Something failed</ui-alert>
      <ui-alert kind="success">Saved</ui-alert>
      <ui-section-head heading="Section" [bordered]="true">
        <span class="trailing">3 ITEMS</span>
      </ui-section-head>
      <ui-empty-state>Nothing here</ui-empty-state>
      <ui-status-pill tone="danger">REJECTED</ui-status-pill>
      <ui-stepper [steps]="['Details', 'Review', 'Confirm']" [current]="1" label="Test progress" />
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

  it('ui-page renders heading, projected actions and content', () => {
    const element = render();

    expect(element.querySelector('main')).not.toBeNull();
    expect(element.querySelector('h1')?.textContent).toBe('Test page');
    expect(element.querySelector('.page-heading a')?.textContent).toBe('Action');
    expect(element.querySelector('footer')).toBeNull();
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

  it('ui-stepper marks the current step, completed steps and announces progress', () => {
    const element = render();
    const steps = Array.from(element.querySelectorAll('.step'));

    expect(element.querySelector('ol')?.getAttribute('aria-label')).toBe('Test progress');
    expect(steps.map((step) => step.querySelector('.label')?.textContent)).toEqual(['Details', 'Review', 'Confirm']);
    expect(steps.map((step) => step.getAttribute('aria-current'))).toEqual([null, 'step', null]);
    expect(steps[0].classList).toContain('done');
    expect(steps[0].textContent).toContain('(done)');
    expect(steps[2].classList).not.toContain('done');
  });
});
