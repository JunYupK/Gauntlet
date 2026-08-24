import { test, expect } from '@playwright/test';

/**
 * Task 14 스모크. **시각 회귀 테스트가 아니다**(스펙 §14가 명시적으로
 * 금지한다) — 정적 export를 실제 브라우저로 열어 다음 세 가지만 본다:
 *
 *   1. 콘솔 오류 0건 (모든 화면)
 *   2. 갤러리 패널이 세대 수만큼(데모 번들 = 12) 그려진다
 *   3. 재생하면 생존 턴 카운터가 실제로 올라간다
 *
 * "무엇이 깨지면 이 테스트가 실패하는가"를 매 assertion마다 물었다 —
 * body가 존재하는지만 보거나 패널 수를 ">0"으로만 재는 스모크는 빈
 * 화면도 통과시킨다.
 */

const 화면들 = ['/', '/gallery', '/curve', '/loop', '/diff', '/match', '/heatmap'];

for (const path of 화면들) {
  test(`${path} — 콘솔 오류 없이 뜬다`, async ({ page }) => {
    const errors: string[] = [];
    page.on('console', (m) => {
      if (m.type() === 'error') errors.push(m.text());
    });
    page.on('pageerror', (e) => errors.push(e.message));

    await page.goto(path);
    await expect(page.locator('body')).toBeVisible();
    expect(errors).toEqual([]);
  });
}

test('/gallery — 패널이 세대 수만큼 그려진다', async ({ page }) => {
  await page.goto('/gallery');
  // 데모 번들은 12세대다. 패널이 그보다 적으면 배치나 디코딩이 죽은 것이다.
  await expect(page.locator('[data-panel]')).toHaveCount(12);
});

test('/gallery — 재생하면 생존 턴 카운터가 올라간다', async ({ page }) => {
  await page.goto('/gallery');
  const counter = page.locator('[data-turn-counter]').first();
  const before = await counter.textContent();
  await page.getByRole('button', { name: '재생' }).click();
  await expect(counter).not.toHaveText(before ?? '');
});
