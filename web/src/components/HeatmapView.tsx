'use client';
import { useMemo, useState } from 'react';
import { cycles, overfitGap } from '@/lib/heatmap';
import type { GenerationStat, RoundRobinData } from '@/lib/schema';

/**
 * 화면 6(부록) — 라운드로빈 히트맵과 세대별 과적합 격차. `cycles`/
 * `overfitGap`(순수 함수, `loadBundle`을 부르지 않는다)이 이미 계산해
 * 낸 값을 그리기만 한다 — `app/heatmap/page.tsx`(서버 컴포넌트)가
 * `roundRobin`/`generations`를 그대로 넘기고, 여기서 두 함수를 부른다.
 * 화면 2(`CurveChart`)와 같은 서버/클라이언트 분리 관례다.
 *
 * ## 색 — dataviz 스킬의 diverging 처방
 *
 * 승률 행렬은 0~1이고 0.5가 "무승부 성향"이라는 자연스러운 중립점을
 * 가진다 — 이것이 dataviz 스킬의 diverging(극성) 정의 그 자체다("어느
 * 쪽 baseline에 있는가"). 스킬의 문서화된 diverging 짝은 파랑↔빨강 +
 * 회색 중립점이고(palette.md), 이 저장소의 청/주황(#38bdf8/#fb923c)은
 * 좌석 정체성 전용이라 겹치지 않는다 — 여기 쓰는 파랑(#3987e5)은
 * 카테고리 슬롯1의 다크 스텝으로, 좌석의 하늘색(#38bdf8)과는 다른
 * 정확한 헥스다(화면 2의 CurveChart가 같은 이유로 같은 슬롯1 파랑을
 * 쓴 전례를 따른다).
 *
 * 두 극을 다크 표면(#171717, 화면 2와 같은 표면)에 대해
 * `validate_palette.js`로 스팟체크했다 — CVD ΔE(all-pairs) 19.2(protan),
 * normal-vision floor 29.0(≥15 통과), 대비 파랑 4.93:1 · 빨강 5.55:1
 * (≥3:1 통과). 전체 그러데이션 램프에 카테고리 6원칙 validator를
 * 그대로 돌리면 램프가 밝기대역을 가로지르므로 설계상 FAIL이 나온다
 * (색-공식 문서가 명시적으로 경고하는 함정이다) — 그래서 두 극점만
 * 검증하고, 중간은 그 두 극 사이의 단조 보간이다(팔 길이 동일).
 * 중립 회색(#383835)은 표면 대비가 낮아(1.52:1) 모든 칸에 값 라벨을
 * 직접 찍어 대비 완화(secondary encoding)로 삼는다.
 *
 * ## 과적합 격차 막대 — 별도 스케일, 별도 색
 *
 * 격차는 0을 기준으로 방향이 다른 뜻을 가진다 — 양수(심사 > 홀드아웃)는
 * 진짜 나쁜 신호(과적합)이고 음수(홀드아웃 ≥ 심사)는 나쁘지 않다.
 * "어느 쪽이 문제냐"가 대칭적인 정체성이 아니라 비대칭적인 좋음/나쁨
 * 판정이므로, 색-공식의 충돌 규칙("의미가 좋음/나쁨이면 상태 토큰을
 * 입는다")에 따라 히트맵의 파랑/빨강이 아니라 상태 팔레트(good/critical,
 * palette.md)를 쓴다 — 좌석 색과도, 히트맵 diverging 색과도 겹치지
 * 않는 세 번째 스케일이다. 상태색 규칙대로 아이콘+라벨을 항상 같이
 * 찍는다(색만으로 뜻을 전달하지 않는다).
 */

const DIVERGING_LOW = '#e66767';  // 빨강(카테고리 슬롯8 다크) — i가 열세
const DIVERGING_MID = '#383835';  // 중립 회색 — 정확히 50%
const DIVERGING_HIGH = '#3987e5'; // 파랑(카테고리 슬롯1 다크) — i가 우세
const CELL_EMPTY = '#26262380';   // 대각선(자기 자신) — 빈 칸

const GAP_BAD = '#d03b3b';  // 상태 critical — 심사가 홀드아웃보다 높다(과적합 신호)
const GAP_GOOD = '#0ca30c'; // 상태 good — 홀드아웃이 심사와 같거나 높다
const GAP_NONE = '#52514e'; // 승격 기록 없음 — 색이 아니라 회색 텍스트로만

function hexToRgb(hex: string): [number, number, number] {
  const n = parseInt(hex.replace('#', ''), 16);
  return [(n >> 16) & 255, (n >> 8) & 255, n & 255];
}
function rgbToHex([r, g, b]: [number, number, number]): string {
  return '#' + [r, g, b].map((v) => Math.round(v).toString(16).padStart(2, '0')).join('');
}
function lerp(a: string, b: string, t: number): string {
  const [ar, ag, ab] = hexToRgb(a);
  const [br, bg, bb] = hexToRgb(b);
  return rgbToHex([ar + (br - ar) * t, ag + (bg - ag) * t, ab + (bb - ab) * t]);
}

/** 0~1 승률을 diverging 색으로. 0.5가 중립점, 양 팔의 보간 폭이 같다. */
function divergingColor(value: number): string {
  if (value <= 0.5) return lerp(DIVERGING_LOW, DIVERGING_MID, value / 0.5);
  return lerp(DIVERGING_MID, DIVERGING_HIGH, (value - 0.5) / 0.5);
}

const fmtPct = (v: number) => `${(v * 100).toFixed(1)}%`;
const fmtPctInt = (v: number) => `${Math.round(v * 100)}`;

interface GapPoint {
  generation: number;
  botName: string;
  gap: number | null;
  scoreRate: number;
  holdoutScoreRate: number;
}

export function HeatmapView({
  roundRobin,
  generations,
}: {
  roundRobin: RoundRobinData;
  generations: GenerationStat[];
}) {
  const [hoverCell, setHoverCell] = useState<{ i: number; j: number } | null>(null);
  const [hoverGap, setHoverGap] = useState<number | null>(null);

  const cyc = useMemo(() => cycles(roundRobin.matrix), [roundRobin]);

  const gapData: GapPoint[] = useMemo(
    () =>
      [...generations]
        .sort((a, b) => a.generation - b.generation)
        .map((s) => ({
          generation: s.generation,
          botName: s.botName,
          gap: overfitGap(s),
          scoreRate: s.scoreRate,
          holdoutScoreRate: s.holdoutScoreRate,
        })),
    [generations],
  );

  const maxAbsGap = Math.max(0.05, ...gapData.map((g) => (g.gap === null ? 0 : Math.abs(g.gap))));

  const BAR_W = 40;
  const BAR_GAP = 10;
  const CHART_H = 220;
  const ZERO_Y = CHART_H / 2;
  const chartWidth = gapData.length * (BAR_W + BAR_GAP) + BAR_GAP;

  const barY = (gap: number) => ZERO_Y - (gap / maxAbsGap) * (ZERO_Y - 12);

  const { bots, matrix } = roundRobin;
  const CELL = 38;

  return (
    <main className="flex flex-col gap-8 p-6 max-w-6xl mx-auto">
      <header>
        <h1 className="text-xl font-bold">히트맵과 과적합 격차 (부록)</h1>
        <p className="mt-1 text-sm text-neutral-400">
          라운드로빈 대전의 순환 우위, 세대별 심사·홀드아웃 승률 격차 — 시드 과적합 신호(스펙 §6).
        </p>
      </header>

      {/* ── 라운드로빈 히트맵 ─────────────────────────────────────────── */}
      <section className="rounded-lg border border-neutral-800 bg-neutral-900 p-4">
        <div className="mb-3 flex flex-wrap items-center justify-between gap-3">
          <h2 className="text-base font-semibold">라운드로빈 승점 승률</h2>
          <div className="flex items-center gap-2 text-xs text-neutral-400">
            <span>0%</span>
            <span
              className="h-3 w-32 rounded"
              style={{ background: `linear-gradient(90deg, ${DIVERGING_LOW}, ${DIVERGING_MID}, ${DIVERGING_HIGH})` }}
              aria-hidden
            />
            <span>100%</span>
            <span className="ml-2">행 봇이 열 봇을 상대로 낸 승점 승률 · 대각선은 자기 자신(빈 칸)</span>
          </div>
        </div>

        <div className="overflow-x-auto">
          <table className="border-separate" style={{ borderSpacing: 2 }}>
            <thead>
              <tr>
                <th className="w-32" />
                {bots.map((name, j) => (
                  <th key={name} style={{ width: CELL, height: 90 }} className="align-bottom p-0">
                    <div
                      className="origin-bottom-left whitespace-nowrap text-[10px] text-neutral-400"
                      style={{ transform: 'rotate(-55deg) translateX(6px)' }}
                    >
                      {name}
                    </div>
                    <span className="sr-only">{`열 ${j}: ${name}`}</span>
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {bots.map((rowName, i) => (
                <tr key={rowName}>
                  <th
                    scope="row"
                    className="pr-2 text-right text-xs font-normal text-neutral-400 whitespace-nowrap"
                  >
                    {rowName}
                  </th>
                  {bots.map((colName, j) => {
                    const v = matrix[i]?.[j] ?? null;
                    const isDiagonal = v === null;
                    const isHover = hoverCell?.i === i && hoverCell?.j === j;
                    return (
                      <td key={colName} className="p-0">
                        <button
                          type="button"
                          onMouseEnter={() => setHoverCell({ i, j })}
                          onFocus={() => setHoverCell({ i, j })}
                          onMouseLeave={() => setHoverCell(null)}
                          onBlur={() => setHoverCell(null)}
                          disabled={isDiagonal}
                          className="flex items-center justify-center text-[10px] font-semibold tabular-nums"
                          style={{
                            width: CELL,
                            height: CELL,
                            backgroundColor: isDiagonal ? CELL_EMPTY : divergingColor(v!),
                            color: isDiagonal ? '#52514e' : '#ffffff',
                            outline: isHover ? '2px solid #e6edf3' : 'none',
                            outlineOffset: -2,
                            cursor: isDiagonal ? 'default' : 'pointer',
                          }}
                          aria-label={
                            isDiagonal
                              ? `${rowName} 자기 자신`
                              : `${rowName} vs ${colName}: ${fmtPct(v!)}`
                          }
                        >
                          {isDiagonal ? '' : fmtPctInt(v!)}
                        </button>
                      </td>
                    );
                  })}
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <div className="mt-2 h-7 text-sm text-neutral-300">
          {hoverCell &&
            (() => {
              const v = matrix[hoverCell.i]?.[hoverCell.j] ?? null;
              if (v === null) return null;
              return (
                <span>
                  <strong className="font-mono">{bots[hoverCell.i]}</strong> vs{' '}
                  <strong className="font-mono">{bots[hoverCell.j]}</strong> ·{' '}
                  승점 승률 <strong className="font-mono">{fmtPct(v)}</strong>
                </span>
              );
            })()}
        </div>
      </section>

      {/* ── 순환 우위 ─────────────────────────────────────────────────── */}
      <section className="rounded-lg border border-neutral-800 bg-neutral-900 p-4">
        <h2 className="mb-2 text-base font-semibold">순환 우위 (A가 B를, B가 C를, C가 A를 이긴다)</h2>
        <p className="mb-3 text-sm text-neutral-400">
          {cyc.length === 0
            ? '이 라운드로빈에서는 순환 우위가 없다 — 전체 서열(total order)이 성립한다.'
            : `삼각형 ${cyc.length}개 발견 — "제일 센 봇" 하나로 전체 서열을 매길 수 없다는 뜻이다.`}
        </p>
        {cyc.length > 0 && (
          <ul className="max-h-56 overflow-y-auto grid grid-cols-1 sm:grid-cols-2 gap-x-6 gap-y-1 text-sm font-mono">
            {cyc.map(([a, b, c]) => (
              <li key={`${a}-${b}-${c}`} className="text-neutral-300">
                {bots[a]} → {bots[b]} → {bots[c]} → {bots[a]}
              </li>
            ))}
          </ul>
        )}
      </section>

      {/* ── 과적합 격차 ───────────────────────────────────────────────── */}
      <section className="rounded-lg border border-neutral-800 bg-neutral-900 p-4">
        <div className="mb-3 flex flex-wrap items-center gap-4">
          <h2 className="text-base font-semibold">세대별 과적합 격차 (심사 − 홀드아웃)</h2>
          <span className="flex items-center gap-1.5 text-xs text-neutral-400">
            <span className="inline-block h-2.5 w-2.5 rounded-full" style={{ backgroundColor: GAP_BAD }} />
            ▲ 과적합 신호(심사 &gt; 홀드아웃)
          </span>
          <span className="flex items-center gap-1.5 text-xs text-neutral-400">
            <span className="inline-block h-2.5 w-2.5 rounded-full" style={{ backgroundColor: GAP_GOOD }} />
            ✓ 정상(홀드아웃 ≥ 심사)
          </span>
          <span className="flex items-center gap-1.5 text-xs text-neutral-400">
            <span className="inline-block h-2.5 w-2.5 rounded-full" style={{ backgroundColor: GAP_NONE }} />
            승격 기록 없음
          </span>
        </div>

        <div className="overflow-x-auto">
          <svg
            viewBox={`0 0 ${chartWidth} ${CHART_H + 24}`}
            width={chartWidth}
            height={CHART_H + 24}
            role="img"
            aria-label="세대별 과적합 격차"
          >
            <line x1={0} x2={chartWidth} y1={ZERO_Y} y2={ZERO_Y} stroke="#52514e" strokeWidth={1} />

            {gapData.map((g, idx) => {
              const x = BAR_GAP + idx * (BAR_W + BAR_GAP);
              const isHover = hoverGap === g.generation;

              if (g.gap === null) {
                // 승격 기록 없음 — 0 높이 막대를 그리지 않는다("격차 없음"으로
                // 잘못 읽히므로). 기준선 위에 작은 X만 찍고 라벨은 아래 텍스트로.
                return (
                  <g
                    key={g.generation}
                    onMouseEnter={() => setHoverGap(g.generation)}
                    onMouseLeave={() => setHoverGap(null)}
                  >
                    <rect x={x} y={0} width={BAR_W} height={CHART_H} fill="transparent" />
                    <text
                      x={x + BAR_W / 2}
                      y={ZERO_Y}
                      textAnchor="middle"
                      dominantBaseline="middle"
                      fontSize={14}
                      fill={GAP_NONE}
                    >
                      ×
                    </text>
                    <text
                      x={x + BAR_W / 2}
                      y={ZERO_Y + 16}
                      textAnchor="middle"
                      fontSize={9}
                      fill={GAP_NONE}
                    >
                      기록없음
                    </text>
                    <text
                      x={x + BAR_W / 2}
                      y={CHART_H + 16}
                      textAnchor="middle"
                      fontSize={10}
                      fill="#898781"
                    >
                      Gen {g.generation}
                    </text>
                  </g>
                );
              }

              const y0 = barY(0);
              const y1 = barY(g.gap);
              const top = Math.min(y0, y1);
              const height = Math.max(1, Math.abs(y1 - y0));
              const color = g.gap > 0 ? GAP_BAD : GAP_GOOD;

              return (
                <g
                  key={g.generation}
                  onMouseEnter={() => setHoverGap(g.generation)}
                  onMouseLeave={() => setHoverGap(null)}
                >
                  <rect
                    x={x}
                    y={top}
                    width={BAR_W}
                    height={height}
                    fill={color}
                    opacity={isHover ? 1 : 0.85}
                    stroke={isHover ? '#e6edf3' : 'none'}
                    strokeWidth={1.5}
                  />
                  <text
                    x={x + BAR_W / 2}
                    y={g.gap > 0 ? top - 4 : top + height + 12}
                    textAnchor="middle"
                    fontSize={10}
                    fill="#e6edf3"
                  >
                    {g.gap >= 0 ? '+' : ''}
                    {fmtPctInt(g.gap)}%
                  </text>
                  <text
                    x={x + BAR_W / 2}
                    y={CHART_H + 16}
                    textAnchor="middle"
                    fontSize={10}
                    fill="#898781"
                  >
                    Gen {g.generation}
                  </text>
                </g>
              );
            })}
          </svg>
        </div>

        <div className="mt-2 h-7 text-sm text-neutral-300">
          {hoverGap !== null &&
            (() => {
              const g = gapData.find((d) => d.generation === hoverGap);
              if (!g) return null;
              if (g.gap === null) {
                return (
                  <span>
                    세대 {g.generation} ({g.botName}) — <strong>승격한 시도가 없다</strong>. 홀드아웃을
                    아직 못 재서 격차를 주장하지 않는다.
                  </span>
                );
              }
              return (
                <span>
                  세대 {g.generation} ({g.botName}) · 심사 <strong className="font-mono">{fmtPct(g.scoreRate)}</strong>{' '}
                  − 홀드아웃 <strong className="font-mono">{fmtPct(g.holdoutScoreRate)}</strong> ={' '}
                  <strong className="font-mono" style={{ color: g.gap > 0 ? GAP_BAD : GAP_GOOD }}>
                    {g.gap >= 0 ? '+' : ''}
                    {fmtPct(g.gap)}
                  </strong>
                </span>
              );
            })()}
        </div>

        <details className="mt-4">
          <summary className="cursor-pointer text-sm text-neutral-400">표로 보기 — 세대별 전체 값</summary>
          <div className="mt-3 overflow-x-auto">
            <table className="w-full text-sm" style={{ fontVariantNumeric: 'tabular-nums' }}>
              <thead>
                <tr className="text-left text-neutral-400">
                  <th className="pr-4 py-1">세대</th>
                  <th className="pr-4 py-1">봇</th>
                  <th className="pr-4 py-1">심사 승점 승률</th>
                  <th className="pr-4 py-1">홀드아웃 승점 승률</th>
                  <th className="pr-4 py-1">격차</th>
                </tr>
              </thead>
              <tbody>
                {gapData.map((g) => (
                  <tr key={g.generation} className="border-t border-neutral-800">
                    <td className="pr-4 py-1">{g.generation}</td>
                    <td className="pr-4 py-1">{g.botName}</td>
                    <td className="pr-4 py-1">{fmtPct(g.scoreRate)}</td>
                    <td className="pr-4 py-1">
                      {Number.isNaN(g.holdoutScoreRate) ? '—' : fmtPct(g.holdoutScoreRate)}
                    </td>
                    <td className="pr-4 py-1">
                      {g.gap === null ? (
                        <span className="text-neutral-500">승격 기록 없음</span>
                      ) : (
                        <span style={{ color: g.gap > 0 ? GAP_BAD : GAP_GOOD }}>
                          {g.gap >= 0 ? '+' : ''}
                          {fmtPct(g.gap)}
                        </span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </details>
      </section>
    </main>
  );
}
