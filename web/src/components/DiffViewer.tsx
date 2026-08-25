'use client';
import { useMemo, useState } from 'react';
import { diffAgainstPrevious, type DiffLine } from '@/lib/diff';
import type { SourceIndexEntry } from '@/lib/schema';

/**
 * 화면 4 — 세대별 코드 diff. `diffAgainstPrevious`(순수 함수,
 * `lib/diff.ts`)가 낸 값을 찍기만 한다 — 여기서 diff를 다시 계산하지
 * 않는다(R1). `app/diff/page.tsx`(서버 컴포넌트)가
 * `loadBundle().sources`/`sourceText`를 그대로 넘긴다.
 *
 * 추가 줄은 초록, 삭제 줄은 빨강 — dataviz 스킬의 status 팔레트
 * good(#0ca30c)/critical(#d03b3b)을 그대로 쓴다. 이 두 값은 이미
 * `LoopTimeline`에서 검증돼 쓰이고 있는 상태색이고, 봇 좌석색
 * (`BOT_COLORS` = 청 #38bdf8 / 주황 #fb923c)과는 다른 값이다 — 좌석을
 * 뜻하기로 정한 색을 여기서 재사용하면 "봇 0/1"과 "추가/삭제"가 같은
 * 색으로 겹쳐 의미가 흐려진다.
 */
const ADD_COLOR = '#0ca30c';
const DEL_COLOR = '#d03b3b';

function lineStyle(kind: DiffLine['kind']): { bg: string; fg: string; prefix: string } {
  if (kind === 'add') return { bg: 'rgba(12, 163, 12, 0.16)', fg: ADD_COLOR, prefix: '+' };
  if (kind === 'del') return { bg: 'rgba(208, 59, 59, 0.16)', fg: DEL_COLOR, prefix: '-' };
  return { bg: 'transparent', fg: '#8b9099', prefix: ' ' };
}

function DiffLineRow({ line, index }: { line: DiffLine; index: number }) {
  const { bg, fg, prefix } = lineStyle(line.kind);
  return (
    <div
      className="flex gap-3 whitespace-pre px-3 font-mono text-xs leading-5"
      style={{ backgroundColor: bg }}
    >
      <span className="w-10 shrink-0 select-none text-right text-neutral-600">{index + 1}</span>
      <span className="w-3 shrink-0 select-none font-bold" style={{ color: fg }}>
        {prefix}
      </span>
      <span style={{ color: line.kind === 'ctx' ? '#c9cdd3' : fg }}>{line.text || ' '}</span>
    </div>
  );
}

export function DiffViewer({ sources, sourceText }: {
  sources: SourceIndexEntry[];
  sourceText: Record<number, string>;
}) {
  const sorted = useMemo(() => [...sources].sort((a, b) => a.generation - b.generation), [sources]);

  const defaultGeneration = useMemo(() => {
    const available = sorted.filter((s) => s.available);
    if (available.length === 0) return sorted[0]?.generation ?? 0;
    return available[available.length - 1].generation;
  }, [sorted]);

  const [generation, setGeneration] = useState<number>(defaultGeneration);

  const entry = sorted.find((s) => s.generation === generation) ?? null;
  const lines = useMemo(
    () => diffAgainstPrevious(sources, sourceText, generation),
    [sources, sourceText, generation],
  );

  const added = lines.filter((l) => l.kind === 'add').length;
  const deleted = lines.filter((l) => l.kind === 'del').length;
  const previousEntry = sorted.find((s) => s.generation === generation - 1) ?? null;

  return (
    <main className="flex flex-col gap-6 p-6 max-w-5xl mx-auto">
      <header>
        <h1 className="text-xl font-bold">세대별 코드 diff — 이번 세대는 무엇을 배웠나</h1>
        <p className="mt-1 text-sm text-neutral-400">
          개선은 파라미터 튜닝이 아니라 코드 재작성이다(C1). 세대를 고르면 직전
          세대에서 채택된 소스와의 줄 단위 차이를 보여준다.
        </p>
      </header>

      <section className="flex flex-wrap gap-1.5" role="tablist" aria-label="세대 선택">
        {sorted.map((s) => (
          <button
            key={s.generation}
            type="button"
            role="tab"
            aria-selected={s.generation === generation}
            disabled={!s.available}
            onClick={() => setGeneration(s.generation)}
            title={s.available ? s.botName : `세대 ${s.generation} — 소스 없음`}
            className="rounded px-2.5 py-1.5 text-xs font-mono transition-colors disabled:cursor-not-allowed disabled:opacity-30"
            style={{
              backgroundColor: s.generation === generation ? '#2a2f38' : '#171a1f',
              color: s.generation === generation ? '#f2f4f7' : '#8b9099',
              border: s.generation === generation ? '1px solid #4a5160' : '1px solid #262a31',
            }}
          >
            세대 {s.generation}
          </button>
        ))}
      </section>

      <section className="flex flex-wrap items-center gap-x-5 gap-y-2 rounded-lg border border-neutral-800 bg-neutral-900 p-3 text-xs">
        <span className="text-neutral-500">범례:</span>
        <span className="flex items-center gap-1.5">
          <span className="inline-block h-3 w-3 rounded-sm" style={{ backgroundColor: ADD_COLOR }} aria-hidden />
          추가 ({added}줄)
        </span>
        <span className="flex items-center gap-1.5">
          <span className="inline-block h-3 w-3 rounded-sm" style={{ backgroundColor: DEL_COLOR }} aria-hidden />
          삭제 ({deleted}줄)
        </span>
        <span className="mx-1 h-4 w-px bg-neutral-700" aria-hidden />
        <span className="text-neutral-500">
          {entry?.available
            ? `${entry.botName} (세대 ${generation})`
            : `세대 ${generation} — 소스 없음`}
          {' vs '}
          {previousEntry?.available ? `${previousEntry.botName} (세대 ${generation - 1})` : '없음(비교 대상 없음 → 전부 추가)'}
        </span>
      </section>

      <section className="overflow-x-auto rounded-lg border border-neutral-800 bg-neutral-950 py-3">
        {!entry || !entry.available ? (
          <p className="px-3 py-6 text-center text-sm text-neutral-600">
            세대 {generation}의 소스가 아직 없다 — 세대 루프가 이 세대까지 돌지
            않았거나 채택된 시도가 없다.
          </p>
        ) : lines.length === 0 ? (
          <p className="px-3 py-6 text-center text-sm text-neutral-600">
            직전 세대와 동일한 소스다 — 표시할 차이가 없다.
          </p>
        ) : (
          <div>
            {lines.map((line, i) => (
              <DiffLineRow key={i} line={line} index={i} />
            ))}
          </div>
        )}
      </section>
    </main>
  );
}
