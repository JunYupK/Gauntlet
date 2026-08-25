import type { AttemptRecord } from './schema';

/**
 * 화면 3 — 루프 타임라인의 순수 계산부. 세대 × 시도 격자를 만들고
 * (`timelineRows`), 판정 톤(`attemptTone`)과 반려 사유 색(`gateColor`)을
 * 낸다 — 컴포넌트는 이 세 함수가 낸 값을 찍기만 한다(R1).
 */

export interface TimelineRow {
  generation: number;
  attempts: AttemptRecord[];
}

/**
 * `generations`가 행의 목록을 정한다 — `history`의 키가 아니다. 시도가
 * 0건인 세대도 `generations`에 있으면 행을 갖는다(빈 attempts 배열로).
 * 반대로 `history`에만 있고 `generations`엔 없는 키는 행을 만들지
 * 않는다 — 번들의 세대 인덱스(`generations.json`)가 "이 세대가
 * 존재한다"의 유일한 근거이기 때문이다.
 *
 * `generations` 배열의 순서에 기대지 않고 `generation` 값으로 정렬한다
 * — curve.ts의 baseline/latest와 같은 이유(뒤섞인 입력에서도 옳아야
 * 한다). 각 행 안의 attempts도 배열 순서가 아니라 `attempt` 번호로
 * 정렬한다 — 브리프의 두 번째 테스트가 요구하는 것과 같다.
 */
export function timelineRows(
  history: Record<string, AttemptRecord[]>,
  generations: { generation: number }[],
): TimelineRow[] {
  return [...generations]
    .sort((a, b) => a.generation - b.generation)
    .map((g) => ({
      generation: g.generation,
      attempts: [...(history[String(g.generation)] ?? [])]
        .sort((a, b) => a.attempt - b.attempt),
    }));
}

export type AttemptTone = 'passed' | 'promoted' | 'rejected';

/** verdict을 세 톤으로 좁힌다 — 화면의 색 배정은 이 톤만 본다. */
export function attemptTone(record: AttemptRecord): AttemptTone {
  if (record.verdict === 'PROMOTED') return 'promoted';
  if (record.verdict === 'PASSED') return 'passed';
  return 'rejected';
}

/**
 * 반려 사유 색 — G2..G7과 챔피언전 반려(failedGate === null)를 합쳐
 * 일곱 개의 서로 다른 값을 낸다. dataviz 스킬의 범주형 팔레트(dark 스텝,
 * 8슬롯)에서 순서대로 여섯 자리를 골라 G2..G7에 고정 배정하고, 빨강
 * (슬롯8)은 건너뛴다 — 반려 칸 배경 자체가 이미 빨강 계열(critical)이라,
 * 사유 배지에도 빨강을 쓰면 "반려됐다"(칸 배경)와 "어느 관문인가"(배지)가
 * 같은 색으로 겹쳐 구분이 흐려진다. 챔피언전 반려는 그 여섯과도, 빨강과도
 * 다른 보라(슬롯7)를 쓴다 — G7과의 구분이 브리프 테스트가 직접 고정한
 * 요구사항이다.
 *
 * `validate_palette.js --mode dark --surface #171717`로 이 순서(슬롯
 * 1·2·3·4·5·6·7)를 검증했다 — CVD 인접쌍 최악 ΔE 8.4, 일반 시각 인접쌍
 * 최악 ΔE 19.3, 대비 전부 ≥3:1로 전부 통과. 격자에서는 인접 규칙이 실제
 * 화면 배치까지 보장하지 않으므로(사유가 시도 순서대로 나열되지, 색
 * 슬롯 순서대로 나열되지 않는다), 색만으로 식별하게 두지 않는다 — 화면은
 * 칸 안에 사유 코드 텍스트(G2..G7 또는 "챔피언전")를 함께 찍고, 마우스
 * 오버 시 `detail`을 그대로 보여준다.
 */
const GATE_COLORS: Record<string, string> = {
  G2: '#3987e5', // blue
  G3: '#d95926', // orange
  G4: '#199e70', // aqua
  G5: '#c98500', // yellow
  G6: '#d55181', // magenta
  G7: '#008300', // green
};

const CHAMPIONSHIP_COLOR = '#9085e9'; // violet — 관문이 아니라 챔피언전 승률 미달
const UNKNOWN_COLOR = '#898781'; // 스키마 밖 문자열이 들어온 경우의 안전망 — 실제로는 나오지 않는다

export function gateColor(failedGate: string | null): string {
  if (failedGate === null) return CHAMPIONSHIP_COLOR;
  return GATE_COLORS[failedGate] ?? UNKNOWN_COLOR;
}
