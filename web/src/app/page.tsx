import Link from 'next/link';
import { loadBundle } from '@/lib/bundle';
import { DataSourceBanner } from '@/components/DataSourceBanner';
import { SCREENS } from '@/lib/screens';

/**
 * `/` — 발표의 목차이자 시작점(Task 14). 순서는 `lib/screens.ts`의
 * `SCREENS` 하나에서만 나온다(`Deck`도 같은 배열을 쓴다) — 스펙 §9.2의
 * 발표 서사 순서(결과 먼저, 과정 나중)를 그대로 따르고, 이 파일에서
 * 다시 나열하지 않는다.
 */
export default function Home() {
  const bundle = loadBundle();

  return (
    <>
      <DataSourceBanner demo={bundle.meta.demo} />
      <main className="mx-auto max-w-2xl p-8">
        <h1 className="mb-2 text-2xl font-bold">Arena — 결정론적 트론 봇 아레나</h1>
        <p className="mb-8 text-sm text-neutral-400">
          {bundle.meta.generations}세대 · {bundle.meta.demo ? '데모 번들' : '실제 번들'} ·
          결과를 먼저 보여주고 과정을 나중에 밝힌다. ←/→로 화면을 넘기고
          Space로 재생/정지한다.
        </p>

        <ol className="flex flex-col gap-2">
          {SCREENS.map((screen, i) => (
            <li key={screen.path}>
              <Link
                href={screen.path}
                className="flex items-baseline gap-3 rounded-lg border border-neutral-800 bg-neutral-900 px-4 py-3 transition-colors hover:border-neutral-600 hover:bg-neutral-800"
              >
                <span className="font-mono text-neutral-500">{i + 1}</span>
                <span className="font-medium text-neutral-100">{screen.title}</span>
                <span className="ml-auto font-mono text-xs text-neutral-500">{screen.evidence}</span>
              </Link>
            </li>
          ))}
        </ol>
      </main>
    </>
  );
}
