'use client';
import { useMemo, useState, type MouseEvent } from 'react';
import { curveSeries, r3Ratio, r3Passed, r3Threshold } from '@/lib/curve';
import type { GenerationStat } from '@/lib/schema';

/**
 * 화면 2 — 개선 곡선. `curve.ts`가 이미 계산해 낸 배율·합격 여부·문턱값·
 * 계열 점을 그리기만 한다 — 이 컴포넌트 안에는 나눗셈도 비교도 없다
 * (R1). `app/curve/page.tsx`(서버 컴포넌트)가 `loadBundle().generations`를
 * 그대로 넘기고, 여기서 `curveSeries`/`r3Ratio`/`r3Passed`/`r3Threshold`를
 * 부른다 — `Gallery`가 `decodeReplay`를 클라이언트에서 부르는 것과 같은
 * 이유다.
 *
 * 색은 dataviz 스킬의 참조 팔레트(dark 스텝)에서 골랐다 — 이 저장소의
 * 청/주황(#38bdf8/#fb923c)은 좌석(봇0/봇1) 정체성 전용이라 여기 세
 * 지표(생존 턴 축)+세 보조 지표에는 다른 계열 슬롯을 쓴다. 순서는
 * blue → aqua → yellow → magenta(팔레트 슬롯 1·3·4·5)이고, 이 순서로
 * `validate_palette.js --mode dark --surface #171717`를 돌려 인접 쌍
 * CVD·명도대·대비를 전부 통과시켰다(worst adjacent CVD ΔE 8.4, normal
 * -vision floor 19.3, 대비 전부 ≥3:1).
 */
const SERIES_COLOR: Record<string, string> = {
  avgSurvivalTurns: '#3987e5', // blue — 주 지표
  scoreRate: '#199e70',        // aqua
  occupancy: '#c98500',        // yellow
  suicideRate: '#d55181',      // magenta
};

const THRESHOLD_COLOR = '#94a3b8'; // 중립 회색 — 어떤 계열 슬롯도 쓰지 않는다
const GOOD = '#0ca30c';
const CRITICAL = '#d03b3b';

const SECONDARY_KEYS = ['scoreRate', 'occupancy', 'suicideRate'] as const;
type SecondaryKey = (typeof SECONDARY_KEYS)[number];

const WIDTH = 760;
const HEIGHT_A = 300;
const HEIGHT_B = 200;
const MARGIN = { top: 16, right: 20, bottom: 28, left: 56 };

function scaleX(x: number, minX: number, maxX: number, innerW: number): number {
  if (maxX === minX) return MARGIN.left + innerW / 2;
  return MARGIN.left + ((x - minX) / (maxX - minX)) * innerW;
}

function scaleY(y: number, maxY: number, innerH: number): number {
  if (maxY === 0) return MARGIN.top + innerH;
  return MARGIN.top + innerH - (y / maxY) * innerH;
}

function pathFor(
  points: { x: number; y: number }[],
  minX: number, maxX: number, maxY: number, innerW: number, innerH: number,
): string {
  return points
    .map((p, i) => `${i === 0 ? 'M' : 'L'}${scaleX(p.x, minX, maxX, innerW).toFixed(2)},${scaleY(p.y, maxY, innerH).toFixed(2)}`)
    .join(' ');
}

const fmtTurns = (v: number) => Math.round(v).toLocaleString('ko-KR');
const fmtRate = (v: number) => `${(v * 100).toFixed(1)}%`;

export function CurveChart({ stats }: { stats: GenerationStat[] }) {
  const [visible, setVisible] = useState<Set<SecondaryKey>>(new Set());
  const [hoverIdx, setHoverIdx] = useState<number | null>(null);

  const series = useMemo(() => curveSeries(stats), [stats]);
  const ratio = useMemo(() => r3Ratio(stats), [stats]);
  const passed = useMemo(() => r3Passed(stats), [stats]);
  const threshold = useMemo(() => r3Threshold(stats), [stats]);

  const survival = series.find((s) => s.key === 'avgSurvivalTurns')!;
  const generations = survival.points.map((p) => p.x);
  const minX = Math.min(...generations);
  const maxX = Math.max(...generations);
  const innerW = WIDTH - MARGIN.left - MARGIN.right;
  const innerHA = HEIGHT_A - MARGIN.top - MARGIN.bottom;
  const innerHB = HEIGHT_B - MARGIN.top - MARGIN.bottom;

  const maxYA = Math.max(
    ...survival.points.map((p) => p.y),
    Number.isFinite(threshold) ? threshold : 0,
  ) * 1.08 || 1;

  const yTicksA = [0, 0.25, 0.5, 0.75, 1].map((f) => f * maxYA);
  const yTicksB = [0, 0.25, 0.5, 0.75, 1];

  const toggle = (key: SecondaryKey) => {
    setVisible((prev) => {
      const next = new Set(prev);
      if (next.has(key)) next.delete(key); else next.add(key);
      return next;
    });
  };

  const handleMove = (e: MouseEvent<SVGSVGElement>) => {
    const rect = e.currentTarget.getBoundingClientRect();
    const px = ((e.clientX - rect.left) / rect.width) * WIDTH;
    const frac = (px - MARGIN.left) / innerW;
    const idx = Math.round(frac * (generations.length - 1));
    setHoverIdx(Math.max(0, Math.min(generations.length - 1, idx)));
  };

  const hoverGen = hoverIdx !== null ? generations[hoverIdx] : null;

  return (
    <main className="flex flex-col gap-6 p-6 max-w-4xl mx-auto">
      <header className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-xl font-bold">개선 곡선 — 세대별 평균 생존 턴</h1>
        <div
          className="flex items-center gap-2 rounded-full border px-3 py-1 text-sm font-semibold"
          style={{
            borderColor: passed ? GOOD : CRITICAL,
            color: passed ? GOOD : CRITICAL,
            backgroundColor: passed ? 'rgba(12,163,12,0.12)' : 'rgba(208,59,59,0.12)',
          }}
        >
          <span aria-hidden>{passed ? '✓' : '✕'}</span>
          <span>
            R3 배율 {Number.isFinite(ratio) ? `${ratio.toFixed(2)}×` : 'N/A'} ·{' '}
            {passed ? '합격' : '미달'}
          </span>
        </div>
      </header>

      <section className="rounded-lg border border-neutral-800 bg-neutral-900 p-4">
        <p className="mb-2 text-sm text-neutral-400">
          평균 생존 턴 (좌석 색과 무관 — 세대별 지표 색이다) · 점선은 R3 합격선
          (Gen 0 × 10{Number.isFinite(threshold) ? ` ≈ ${fmtTurns(threshold)}턴` : ''})
        </p>
        <svg
          viewBox={`0 0 ${WIDTH} ${HEIGHT_A}`}
          className="w-full"
          role="img"
          aria-label="세대별 평균 생존 턴과 R3 합격선"
          onMouseMove={handleMove}
          onMouseLeave={() => setHoverIdx(null)}
        >
          {yTicksA.map((t) => (
            <g key={t}>
              <line
                x1={MARGIN.left} x2={WIDTH - MARGIN.right}
                y1={scaleY(t, maxYA, innerHA)} y2={scaleY(t, maxYA, innerHA)}
                stroke="#2c2c2a" strokeWidth={1}
              />
              <text
                x={MARGIN.left - 8} y={scaleY(t, maxYA, innerHA)}
                textAnchor="end" dominantBaseline="middle"
                fontSize={11} fill="#898781"
              >
                {fmtTurns(t)}
              </text>
            </g>
          ))}

          {generations.map((g) => (
            <text
              key={g}
              x={scaleX(g, minX, maxX, innerW)} y={HEIGHT_A - 8}
              textAnchor="middle" fontSize={10} fill="#898781"
            >
              {g}
            </text>
          ))}

          {Number.isFinite(threshold) && (
            <>
              <line
                x1={MARGIN.left} x2={WIDTH - MARGIN.right}
                y1={scaleY(threshold, maxYA, innerHA)} y2={scaleY(threshold, maxYA, innerHA)}
                stroke={THRESHOLD_COLOR} strokeWidth={1.5} strokeDasharray="6 4"
              />
              <text
                x={WIDTH - MARGIN.right} y={scaleY(threshold, maxYA, innerHA) - 6}
                textAnchor="end" fontSize={11} fill={THRESHOLD_COLOR}
              >
                R3 합격선
              </text>
            </>
          )}

          <path
            d={pathFor(survival.points, minX, maxX, maxYA, innerW, innerHA)}
            fill="none" stroke={SERIES_COLOR.avgSurvivalTurns} strokeWidth={2}
            strokeLinejoin="round" strokeLinecap="round"
          />

          {survival.points.map((p, i) => (
            <circle
              key={p.x}
              cx={scaleX(p.x, minX, maxX, innerW)} cy={scaleY(p.y, maxYA, innerHA)}
              r={hoverIdx === i ? 5 : 3}
              fill={SERIES_COLOR.avgSurvivalTurns} stroke="#171717" strokeWidth={2}
            />
          ))}

          <text
            x={scaleX(survival.points.at(-1)!.x, minX, maxX, innerW) - 4}
            y={scaleY(survival.points.at(-1)!.y, maxYA, innerHA) - 10}
            textAnchor="end" fontSize={12} fontWeight={600} fill="#e6edf3"
          >
            {fmtTurns(survival.points.at(-1)!.y)}턴
          </text>

          {hoverIdx !== null && (
            <line
              x1={scaleX(hoverGen!, minX, maxX, innerW)} x2={scaleX(hoverGen!, minX, maxX, innerW)}
              y1={MARGIN.top} y2={HEIGHT_A - MARGIN.bottom}
              stroke="#e6edf3" strokeOpacity={0.25} strokeWidth={1}
            />
          )}
        </svg>

        {hoverIdx !== null && (
          <div className="mt-2 flex items-center gap-3 rounded border border-neutral-800 bg-neutral-950 px-3 py-1.5 text-sm">
            <span className="text-neutral-400">세대 {hoverGen}</span>
            <span className="flex items-center gap-1">
              <span className="inline-block h-0.5 w-3" style={{ backgroundColor: SERIES_COLOR.avgSurvivalTurns }} />
              평균 생존 턴 <strong className="font-mono">{fmtTurns(survival.points[hoverIdx].y)}</strong>
            </span>
          </div>
        )}
      </section>

      <section className="rounded-lg border border-neutral-800 bg-neutral-900 p-4">
        <div className="mb-3 flex flex-wrap items-center gap-2">
          <span className="text-sm text-neutral-400 mr-1">보조 지표 (0~100%, 토글):</span>
          {SECONDARY_KEYS.map((key) => {
            const s = series.find((x) => x.key === key)!;
            const on = visible.has(key);
            return (
              <button
                key={key}
                type="button"
                aria-pressed={on}
                onClick={() => toggle(key)}
                className={`flex items-center gap-1.5 rounded px-2 py-1 text-xs font-medium ${
                  on ? 'bg-neutral-700 text-neutral-100' : 'bg-neutral-800 text-neutral-500 hover:text-neutral-300'
                }`}
              >
                <span
                  className="inline-block h-0.5 w-3"
                  style={{ backgroundColor: on ? SERIES_COLOR[key] : '#52514e' }}
                />
                {s.label}
              </button>
            );
          })}
        </div>

        {visible.size === 0 ? (
          <p className="text-sm text-neutral-500 py-8 text-center">
            위 토글을 눌러 승점 승률·점유율·자멸률을 겹쳐 본다.
          </p>
        ) : (
          <svg viewBox={`0 0 ${WIDTH} ${HEIGHT_B}`} className="w-full" role="img" aria-label="보조 지표">
            {yTicksB.map((t) => (
              <g key={t}>
                <line
                  x1={MARGIN.left} x2={WIDTH - MARGIN.right}
                  y1={scaleY(t, 1, innerHB)} y2={scaleY(t, 1, innerHB)}
                  stroke="#2c2c2a" strokeWidth={1}
                />
                <text
                  x={MARGIN.left - 8} y={scaleY(t, 1, innerHB)}
                  textAnchor="end" dominantBaseline="middle" fontSize={11} fill="#898781"
                >
                  {fmtRate(t)}
                </text>
              </g>
            ))}

            {generations.map((g) => (
              <text
                key={g}
                x={scaleX(g, minX, maxX, innerW)} y={HEIGHT_B - 8}
                textAnchor="middle" fontSize={10} fill="#898781"
              >
                {g}
              </text>
            ))}

            {SECONDARY_KEYS.filter((k) => visible.has(k)).map((key) => {
              const s = series.find((x) => x.key === key)!;
              return (
                <g key={key}>
                  <path
                    d={pathFor(s.points, minX, maxX, 1, innerW, innerHB)}
                    fill="none" stroke={SERIES_COLOR[key]} strokeWidth={2}
                    strokeLinejoin="round" strokeLinecap="round"
                  />
                  <circle
                    cx={scaleX(s.points.at(-1)!.x, minX, maxX, innerW)}
                    cy={scaleY(s.points.at(-1)!.y, 1, innerHB)}
                    r={4} fill={SERIES_COLOR[key]} stroke="#171717" strokeWidth={2}
                  />
                  <text
                    x={scaleX(s.points.at(-1)!.x, minX, maxX, innerW) - 4}
                    y={scaleY(s.points.at(-1)!.y, 1, innerHB) - 8}
                    textAnchor="end" fontSize={11} fill="#e6edf3"
                  >
                    {fmtRate(s.points.at(-1)!.y)}
                  </text>
                </g>
              );
            })}
          </svg>
        )}
      </section>

      <details className="rounded-lg border border-neutral-800 bg-neutral-900 p-4">
        <summary className="cursor-pointer text-sm text-neutral-400">표로 보기 — 세대별 전체 값</summary>
        <div className="mt-3 overflow-x-auto">
          <table className="w-full text-sm" style={{ fontVariantNumeric: 'tabular-nums' }}>
            <thead>
              <tr className="text-left text-neutral-400">
                <th className="pr-4 py-1">세대</th>
                <th className="pr-4 py-1">평균 생존 턴</th>
                <th className="pr-4 py-1">승점 승률</th>
                <th className="pr-4 py-1">점유율</th>
                <th className="pr-4 py-1">자멸률</th>
              </tr>
            </thead>
            <tbody>
              {survival.points.map((p, i) => (
                <tr key={p.x} className="border-t border-neutral-800">
                  <td className="pr-4 py-1">{p.x}</td>
                  <td className="pr-4 py-1">{fmtTurns(p.y)}</td>
                  <td className="pr-4 py-1">{fmtRate(series.find((s) => s.key === 'scoreRate')!.points[i].y)}</td>
                  <td className="pr-4 py-1">{fmtRate(series.find((s) => s.key === 'occupancy')!.points[i].y)}</td>
                  <td className="pr-4 py-1">{fmtRate(series.find((s) => s.key === 'suicideRate')!.points[i].y)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </details>
    </main>
  );
}
