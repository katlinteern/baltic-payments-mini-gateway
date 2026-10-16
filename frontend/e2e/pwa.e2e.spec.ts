import { expect, test } from '@playwright/test';

test.describe('installable web app (PWA)', () => {
  test('serves a valid manifest with reachable icons', async ({ page, request }) => {
    await page.goto('/login');
    const href = await page.locator('link[rel="manifest"]').getAttribute('href');
    expect(href).toBeTruthy();

    const response = await request.get(`/${href}`);
    expect(response.ok()).toBe(true);
    const manifest = await response.json();
    expect(manifest).toMatchObject({ display: 'standalone', start_url: '/accounts', scope: '/' });

    const sizes = manifest.icons.map((icon: { sizes: string }) => icon.sizes);
    expect(sizes).toEqual(expect.arrayContaining(['192x192', '512x512']));
    expect(manifest.icons.some((icon: { purpose: string }) => icon.purpose === 'maskable')).toBe(true);
    for (const icon of manifest.icons as { src: string }[]) {
      const iconResponse = await request.get(`/${icon.src}`);
      expect(iconResponse.ok(), icon.src).toBe(true);
      expect(iconResponse.headers()['content-type']).toContain('image/png');
    }
  });

  test('registers a service worker and never lets it cache the worker script or API', async ({ page, request }) => {
    await page.goto('/login');
    await expect
      .poll(async () => page.evaluate(async () => (await navigator.serviceWorker.getRegistration()) !== undefined), {
        timeout: 20_000,
      })
      .toBe(true);

    for (const path of ['/ngsw-worker.js', '/ngsw.json']) {
      const response = await request.get(path);
      expect(response.ok(), path).toBe(true);
      expect(response.headers()['cache-control'], path).toBe('no-cache');
    }
    const config = await (await request.get('/ngsw.json')).json();
    const handledByWorker = (url: string) => {
      const rules = config.navigationUrls as { positive: boolean; regex: string }[];
      const matches = (rule: { regex: string }) => new RegExp(rule.regex).test(url);
      return (
        rules.filter((rule) => rule.positive).some(matches) && !rules.filter((rule) => !rule.positive).some(matches)
      );
    };
    expect(handledByWorker('/accounts')).toBe(true);
    expect(handledByWorker('/api/accounts')).toBe(false);
    expect(handledByWorker('/actuator/health')).toBe(false);
    expect(config.dataGroups ?? []).toEqual([]);
  });
});
