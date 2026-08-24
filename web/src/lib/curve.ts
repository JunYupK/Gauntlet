import type { GenerationStat } from './schema';

/**
 * R3 판정과 곡선 계열을 순수 함수로 뽑아낸다 — 화면(`app/curve`)은 여기
 * 나온 숫자를 찍기만 하고 다시 계산하지 않는다(R1). 화면의 유일한 주장
 * "R3을 넘었다"가 이 파일의 두 함수 `r3Ratio`/`r3Passed`로 기계 판정
 * 가능해진다.
 */

/**
 * `stats[0]`이 아니라 `generation === 0`인 원소를 찾는다. 번들이 세대
 * 순서로 정렬돼 있다는 보장은 인터페이스 계약에 없다 — 배열 위치에
 * 기댄 구현은 입력이 뒤섞이는 순간 조용히 틀린 기준점을 고른다.
 */
function baseline(stats: GenerationStat[]): GenerationStat | undefined {
  return stats.find((s) => s.generation === 0);
}

/** 배열의 마지막 원소가 아니라 `generation` 값이 가장 큰 원소. 이유는 baseline과 같다. */
function latest(stats: GenerationStat[]): GenerationStat | undefined {
  return stats.reduce<GenerationStat | undefined>(
    (max, s) => (max === undefined || s.generation > max.generation ? s : max),
    undefined,
  );
}

/**
 * 마지막 세대의 평균 생존 턴 ÷ Gen 0의 평균 생존 턴. Gen 0이 0이면(또는
 * 세대가 없으면) `NaN`을 낸다 — `Infinity`를 "무한히 개선됐다"로 그리면
 * 거짓말이 되므로, 그 갈래는 배율을 아예 주장하지 않는다.
 */
export function r3Ratio(stats: GenerationStat[]): number {
  const base = baseline(stats);
  const last = latest(stats);
  if (!base || !last || base.avgSurvivalTurns === 0) return NaN;
  return last.avgSurvivalTurns / base.avgSurvivalTurns;
}

/**
 * R3 합격선은 정확히 10배다(스펙 §13) — 경계값 10.0 포함(>=). `r3Ratio`가
 * NaN이면 `NaN >= 10`은 항상 false이므로 0으로 나눈 갈래는 자동으로
 * 불합격이 된다.
 */
export function r3Passed(stats: GenerationStat[]): boolean {
  return r3Ratio(stats) >= 10;
}

/**
 * 차트에 그릴 가로선의 y값 — Gen 0 평균 생존 턴 × 10. `r3Ratio`와 같은
 * 기준점(baseline)을 쓴다. Gen 0을 못 찾으면 NaN — 화면은 이 값이
 * finite일 때만 선을 그린다.
 */
export function r3Threshold(stats: GenerationStat[]): number {
  const base = baseline(stats);
  return base ? base.avgSurvivalTurns * 10 : NaN;
}

export interface CurvePoint {
  x: number;
  y: number;
}

export interface CurveSeriesData {
  key: string;
  label: string;
  points: CurvePoint[];
}

/**
 * 화면에 그릴 수 있는 계열 정의. `avgSurvivalTurns`가 주 지표(스펙 §13),
 * 나머지 셋은 토글 대상 보조 지표다. 순서가 곧 범례·색 슬롯 순서다.
 */
const SERIES_DEFS: { key: 'avgSurvivalTurns' | 'scoreRate' | 'occupancy' | 'suicideRate'; label: string }[] = [
  { key: 'avgSurvivalTurns', label: '평균 생존 턴' },
  { key: 'scoreRate', label: '승점 승률' },
  { key: 'occupancy', label: '점유율' },
  { key: 'suicideRate', label: '자멸률' },
];

/**
 * 세대 순으로 정렬한 뒤 각 지표를 {x: generation, y: value} 점 배열로
 * 편다. 입력 배열의 순서에 기대지 않는다 — baseline/latest와 같은 이유.
 */
export function curveSeries(stats: GenerationStat[]): CurveSeriesData[] {
  const sorted = [...stats].sort((a, b) => a.generation - b.generation);
  return SERIES_DEFS.map(({ key, label }) => ({
    key,
    label,
    points: sorted.map((s) => ({ x: s.generation, y: s[key] })),
  }));
}
