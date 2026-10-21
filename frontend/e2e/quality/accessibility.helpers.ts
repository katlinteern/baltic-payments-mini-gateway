import AxeBuilder from '@axe-core/playwright';
import { expect, Page } from '@playwright/test';

export async function expectNoAccessibilityViolations(page: Page): Promise<void> {
  await page.evaluate(() =>
    Promise.all(
      document
        .getAnimations()
        .filter((animation) => animation.effect?.getComputedTiming().iterations !== Infinity)
        .map((animation) => animation.finished.catch(() => undefined)),
    ),
  );
  const results = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
  expect(
    results.violations.map(
      (violation) => `${violation.id}: ${violation.help} ${violation.nodes.map((node) => node.target).join(' ')}`,
    ),
  ).toEqual([]);
}
