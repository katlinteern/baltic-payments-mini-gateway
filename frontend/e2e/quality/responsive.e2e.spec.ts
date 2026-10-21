import { expect, test } from '@playwright/test';
import { signIn } from '../common/auth';
import { BOB } from '../common/data';

test('the layout stays usable on a phone-sized screen', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await signIn(page, BOB);

  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
  expect(overflow).toBeLessThanOrEqual(1);
  await expect(page.getByText('Bob Tamm').first()).toBeVisible();
});
