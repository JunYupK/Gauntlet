'use client';
import { useState } from 'react';
import { timelineRows, attemptTone, gateColor, type AttemptTone } from '@/lib/timeline';
import type { AttemptRecord, GenerationStat } from '@/lib/schema';

/**
 * 화면 3 — 루프 타임라인. `timelineRows`/`attemptTone`/`gateColor`
 * (전부 `lib/timeline.ts`의 순수 함수)가 낸 값을 찍기만 한다 — 여기서
 * 정렬도 색 계산도 다시 하지 않는다(R1). `app/loop/page.tsx`(서버
 * 컴포넌트)가 `loadBundle().loopHistory`/`generations`를 그대로 넘긴다.
 *
 * 판정 톤(세 가지)과 반려 사유(일곱 가지)는 서로 다른 축이다 — 칸의
 * 배경은 톤(합격/승격/반려)을, 반려 칸 안의 작은 배지는 사유(G2..G7·
 * 챔피언전)를 나타낸다. 배지는 색만이 아니라 텍스트 코드도 함께
 * 찍는다 — dataviz 스킬의 "정체성은 색만으로 전달하지 않는다" 규칙.
 * 일곱 가지 사유 색은 격자 안에서 실제로 어느 두 칸이 이웃할지 미리
 * 알 수 없어(시도 순서가 사유 순서가 아니다) 인접쌍 검증만으로는 전체
 * 안전을 보장 못 하므로, 텍스트 코드 + 마우스오버 detail이 최종 식별
 * 수단이다.
 */
const TONE_COLOR: Record<AttemptTone, string> = {
  passed: '#0ca30c',   // status good — 관문을 넘었다
  promoted: '#eda100', // 승격 — 이 세대에서 유일하게 챔피언을 이긴 시도, 눈에 띄어야 한다
  rejected: '#d03b3b', // status critical — 이 화면의 핵심 신호
};

const TONE_LABEL: Record<AttemptTone, string> = {
  passed: '통과',
  promoted: '승격',
  rejected: '반려',
};

const TONE_ICON: Record<AttemptTone, string> = {
  passed: '✓',
  promoted: '★',
  rejected: '✕',
};

const GATES = ['G2', 'G3', 'G4', 'G5', 'G6', 'G7'] as const;

function reasonLabel(failedGate: string | null): string {
  return failedGate ?? '챔피언전';
}

function CellBadge({ attempt, hovered, onEnter, onLeave }: {
  attempt: AttemptRecord;
  hovered: boolean;
  onEnter: () => void;
  onLeave: () => void;
}) {
  const tone = attemptTone(attempt);
  const bg = TONE_COLOR[tone];
  const reason = tone === 'rejected' ? reasonLabel(attempt.failedGate) : null;
  const badge = tone === 'rejected' ? gateColor(attempt.failedGate) : null;

  return (
    <button
      type="button"
      onMouseEnter={onEnter}
      onFocus={onEnter}
      onMouseLeave={onLeave}
      onBlur={onLeave}
      title={`시도 ${attempt.attempt} · ${TONE_LABEL[tone]}${reason ? ` (${reason})` : ''} · ${attempt.detail}`}
      className="relative flex h-11 w-11 shrink-0 flex-col items-center justify-center rounded text-[10px] font-semibold leading-none text-black/85"
      style={{
        backgroundColor: bg,
        outline: hovered ? '2px solid #e6edf3' : '2px solid transparent',
        outlineOffset: 1,
      }}
    >
      <span aria-hidden className="text-sm">{TONE_ICON[tone]}</span>
      {reason && (
        <span
          aria-hidden
          className="mt-0.5 rounded-sm px-1 text-[9px] text-white"
          style={{ backgroundColor: badge! }}
        >
          {reason}
        </span>
      )}
      <span className="sr-only">
        세대 {attempt.generation} 시도 {attempt.attempt}: {TONE_LABEL[tone]}
        {reason ? ` — ${reason} 반려` : ''}. {attempt.detail}
      </span>
    </button>
  );
}

export function LoopTimeline({ history, generations }: {
  history: Record<string, AttemptRecord[]>;
  generations: GenerationStat[];
}) {
  const rows = timelineRows(history, generations);
  const [hoveredKey, setHoveredKey] = useState<string | null>(null);

  const hovered = hoveredKey
    ? rows.flatMap((r) => r.attempts).find((a) => `${a.generation}:${a.attempt}` === hoveredKey) ?? null
    : null;

  return (
    <main className="flex flex-col gap-6 p-6 max-w-5xl mx-auto">
      <header>
        <h1 className="text-xl font-bold">루프 타임라인 — 세대별 시도</h1>
        <p className="mt-1 text-sm text-neutral-400">
          세대마다 시도를 번호 순으로 늘어놓는다. 빨간 칸이 반려다 — 칸 안의 작은
          배지가 어느 관문에서 막혔는지(G2..G7), 또는 챔피언전에서 승률이
          모자랐는지를 가른다. 칸에 마우스를 올리면 반려 사유 전문이 뜬다.
        </p>
      </header>

      <section className="flex flex-wrap items-center gap-x-5 gap-y-2 rounded-lg border border-neutral-800 bg-neutral-900 p-3 text-xs">
        <span className="text-neutral-500">판정:</span>
        {(['passed', 'promoted', 'rejected'] as const).map((tone) => (
          <span key={tone} className="flex items-center gap-1.5">
            <span
              className="inline-flex h-4 w-4 items-center justify-center rounded text-[9px] text-black/85"
              style={{ backgroundColor: TONE_COLOR[tone] }}
              aria-hidden
            >
              {TONE_ICON[tone]}
            </span>
            {TONE_LABEL[tone]}
          </span>
        ))}
        <span className="mx-1 h-4 w-px bg-neutral-700" aria-hidden />
        <span className="text-neutral-500">반려 사유:</span>
        {GATES.map((g) => (
          <span key={g} className="flex items-center gap-1.5">
            <span className="inline-block h-3 w-3 rounded-sm" style={{ backgroundColor: gateColor(g) }} aria-hidden />
            {g}
          </span>
        ))}
        <span className="flex items-center gap-1.5">
          <span className="inline-block h-3 w-3 rounded-sm" style={{ backgroundColor: gateColor(null) }} aria-hidden />
          챔피언전
        </span>
      </section>

      <section className="flex flex-col gap-2 overflow-x-auto rounded-lg border border-neutral-800 bg-neutral-900 p-4">
        {rows.map((row) => (
          <div key={row.generation} className="flex items-center gap-3">
            <span className="w-14 shrink-0 text-right text-sm font-mono text-neutral-400">
              세대 {row.generation}
            </span>
            <div className="flex gap-1.5">
              {row.attempts.length === 0 ? (
                <span className="flex h-11 items-center rounded border border-dashed border-neutral-700 px-3 text-xs text-neutral-600">
                  기록 없음
                </span>
              ) : (
                row.attempts.map((a) => {
                  const key = `${a.generation}:${a.attempt}`;
                  return (
                    <CellBadge
                      key={key}
                      attempt={a}
                      hovered={hoveredKey === key}
                      onEnter={() => setHoveredKey(key)}
                      onLeave={() => setHoveredKey((k) => (k === key ? null : k))}
                    />
                  );
                })
              )}
            </div>
          </div>
        ))}
      </section>

      <section
        className="min-h-[4.5rem] rounded-lg border border-neutral-800 bg-neutral-950 p-4 text-sm"
        aria-live="polite"
      >
        {hovered ? (
          <dl className="grid grid-cols-[auto_1fr] gap-x-3 gap-y-1">
            <dt className="text-neutral-500">세대 · 시도</dt>
            <dd className="font-mono">{hovered.generation} · #{hovered.attempt}</dd>
            <dt className="text-neutral-500">판정</dt>
            <dd>
              {TONE_LABEL[attemptTone(hovered)]}
              {attemptTone(hovered) === 'rejected' && ` — ${reasonLabel(hovered.failedGate)}`}
            </dd>
            <dt className="text-neutral-500">사유</dt>
            <dd>{hovered.detail || '—'}</dd>
          </dl>
        ) : (
          <p className="text-neutral-600">칸에 마우스를 올리면 반려 사유가 여기 그대로 뜬다.</p>
        )}
      </section>
    </main>
  );
}
