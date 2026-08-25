import type { GenerationStat } from './schema';

/**
 * 화면 6(부록) — 라운드로빈 히트맵의 순환 우위, 세대별 과적합 격차를
 * 순수 함수로 뽑아낸다. 화면(`app/heatmap`)은 색을 입히고 그리기만
 * 하고, "순환이 있다"/"격차가 얼마다"라는 주장은 전부 여기서 계산된
 * 값을 그대로 찍는다(R1).
 */

/**
 * `matrix[i][j]`가 i가 j를 상대로 낸 승점 승률이다. 0.5 초과면 i가 j
 * 위에 있다("우위") — 정확히 0.5(무승부 성향)는 우위로 세지 않는다.
 * 대각선(자기 자신과의 대전)은 스펙상 `null`이다.
 */
function dominates(matrix: (number | null)[][], i: number, j: number): boolean {
  const v = matrix[i]?.[j];
  return v !== null && v !== undefined && v > 0.5;
}

/**
 * 길이-3 순환 우위(A가 B를, B가 C를, C가 A를 이기는 관계)를 전부
 * 찾는다. 이런 삼각형이 하나라도 있으면 "누가 제일 세다"는 전체
 * 순서(total order)가 봇 집단에 존재하지 않는다는 뜻이다 — 스펙 §6이
 * 요구하는 바로 그 증거.
 *
 * 세 인덱스를 오름차순 조합 (i<j<k)으로 한 번씩만 훑고, 그 삼각형이
 * 두 순환 방향(i→j→k→i 또는 i→k→j→i) 중 하나를 이루는지 본다 —
 * 그래서 같은 삼각형이 회전·반사로 중복 보고되지 않는다. 대각선의
 * `null`은 `dominates`가 안전하게 false로 처리하므로(i<j<k라 자기
 * 자신 칸은 애초에 조회되지 않지만, 방어적으로도 안전하다) 여기서
 * 따로 널 검사를 하지 않아도 터지지 않는다.
 */
export function cycles(matrix: (number | null)[][]): [number, number, number][] {
  const n = matrix.length;
  const found: [number, number, number][] = [];

  for (let i = 0; i < n; i++) {
    for (let j = i + 1; j < n; j++) {
      for (let k = j + 1; k < n; k++) {
        if (dominates(matrix, i, j) && dominates(matrix, j, k) && dominates(matrix, k, i)) {
          found.push([i, j, k]);
        } else if (dominates(matrix, i, k) && dominates(matrix, k, j) && dominates(matrix, j, i)) {
          found.push([i, k, j]);
        }
      }
    }
  }

  return found;
}

/**
 * 심사 승점 승률(`scoreRate`) − 홀드아웃 승점 승률(`holdoutScoreRate`).
 * 양수면 심사 시드에서 유독 잘한 것 — 시드 과적합 신호(스펙 §6).
 *
 * `holdoutScoreRate`가 `NaN`이면(승격한 시도가 없는 세대 — Task 1이
 * 그렇게 내보낸다) 격차를 계산하지 않고 `null`을 낸다. `0`을 내면
 * "격차가 없다"(=과적합이 없다)로 읽히지만 진실은 "비교할 홀드아웃이
 * 아예 없다"이므로, 이 갈래는 값을 아예 주장하지 않는다. 화면은
 * `overfitGap`이 `null`인 세대에 막대를 그리지 않고 "승격 기록
 * 없음"이라고 쓴다.
 */
export function overfitGap(stat: GenerationStat): number | null {
  if (Number.isNaN(stat.holdoutScoreRate)) return null;
  return stat.scoreRate - stat.holdoutScoreRate;
}
