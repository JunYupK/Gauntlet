import { defineConfig } from '@playwright/test';

/**
 * Task 14 스모크. 시각 회귀가 아니다(스펙 §14가 금지한다) — 정적
 * export(`out/`)를 실제 브라우저로 열어 콘솔 오류 0건과 필수 요소의
 * 존재만 본다(`e2e/smoke.spec.ts`).
 *
 * `webServer`가 `npx serve out`을 띄운다. `serve`는 devDependency로
 * 고정해서 `npx`가 매번 레지스트리에서 버전을 다시 고르지 않게 한다.
 *
 * 브라우저는 이 환경에 이미 설치돼 있다(`/opt/pw-browsers`,
 * `PLAYWRIGHT_BROWSERS_PATH`) — `playwright install`을 여기서도, CI에서도
 * 돌리지 않는다. `PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD`를 존중하는 것은
 * `@playwright/test`의 npm 설치 단계(postinstall)이고, 이 설정 파일
 * 자체는 그 값을 몰라도 된다 — `PLAYWRIGHT_BROWSERS_PATH`만 있으면
 * Playwright가 그 경로에서 바이너리를 찾는다.
 *
 * `package.json`의 `@playwright/test`가 캐럿(`^`) 없이 정확히
 * `1.56.0`으로 고정된 이유가 이것과 맞물린다 — Playwright는 버전마다
 * 특정 Chromium 리비전을 기대하고(`playwright-core/browsers.json`),
 * `/opt/pw-browsers`엔 리비전 1194 하나만 있다. 1.56.0이 정확히 그
 * 리비전을 기대하는 버전이다(확인: `playwright-core@1.55.1`→1193,
 * `@1.56.0`→1194). 캐럿을 허용하면 다음 `npm install`이 조용히 더 새
 * `@playwright/test`를 골라 다른 리비전을 찾다가 "executable doesn't
 * exist"로 죽는다 — 이 사고를 버전 고정으로 막는다.
 */
export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: 0,
  reporter: 'list',
  use: {
    baseURL: 'http://127.0.0.1:4173',
  },
  webServer: {
    command: 'npx serve out -l 4173',
    url: 'http://127.0.0.1:4173',
    reuseExistingServer: !process.env.CI,
    timeout: 30_000,
  },
  projects: [
    {
      name: 'chromium',
      use: { browserName: 'chromium' },
    },
  ],
});
