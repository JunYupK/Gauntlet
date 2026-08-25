import type { MatchDiagnosis, MoveAnalysis } from './schema';

/**
 * 이 화면의 유일한 실질 위험은 턴 인덱스 규약이 섞이는 것이다.
 * `MatchDiagnosis.reach`/`.loss`는 `[bot][턴]` 배열이고 턴은 0-based다
 * (인덱스 0 = 턴 1) — `MoveAnalysis.turn`은 1-based다(Task 2의
 * `MatchDiagnosis` javadoc). 한 화면이 그래프(loss 배열 출신)와
 * "가장 나쁜 수" 목록(MoveAnalysis 출신)을 동시에 그리므로, 변환을
 * 여기 한 곳에만 두고 나머지 코드는 전부 1-based `turn`만 다루게 한다.
 * 어긋나면 그래프의 봉우리와 "가장 나쁜 수"가 한 칸씩 밀려, 이 화면이
 * 주장하는 것("기계가 이 턴을 짚었다")이 조용히 거짓이 된다.
 */

export interface LossPoint {
  turn: number; // 1-based
  loss: number;
}

/**
 * `diagnosis.loss[seat]`(0-based 배열)를 `{ turn, loss }` 점 배열로
 * 편다 — 배열 인덱스 `i`는 턴 `i + 1`이다. 이 파일 밖에서는 이 덧셈을
 * 다시 하지 않는다.
 */
export function lossSeries(diagnosis: MatchDiagnosis, seat: 0 | 1): LossPoint[] {
  return diagnosis.loss[seat].map((loss, i) => ({ turn: i + 1, loss }));
}

/**
 * 좌석별 "가장 나쁜 수" 목록. `MoveAnalysis.turn`은 이미 1-based이므로
 * 여기서는 변환하지 않고 배열만 고른다 — `worstMoves0`/`worstMoves1`을
 * 반환 시점에 재정렬하거나 자르지 않는다: 순서·개수는 백엔드
 * (`MoveAnalysis` 계산, Task 2)가 이미 정한 것을 그대로 옮긴다(R1).
 */
export function worstFor(diagnosis: MatchDiagnosis, seat: 0 | 1): MoveAnalysis[] {
  return seat === 0 ? diagnosis.worstMoves0 : diagnosis.worstMoves1;
}
