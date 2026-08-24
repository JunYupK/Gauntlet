import { describe, it, expect } from 'vitest';
import { panelGrid } from '../lib/layout';

describe('패널 배치', () => {
  it('스펙이 든 두 예를 그대로 만족한다', () => {
    // 스펙 §9.1의 "12세대 → 3×4"는 행×열로 읽는다 (3행 4열).
    expect(panelGrid(12)).toEqual({ cols: 4, rows: 3 });
    expect(panelGrid(16)).toEqual({ cols: 4, rows: 4 });
  });

  it('모든 패널이 자리를 갖는다', () => {
    for (let n = 1; n <= 40; n++) {
      const { cols, rows } = panelGrid(n);
      expect(cols * rows).toBeGreaterThanOrEqual(n);
    }
  });

  it('빈 자리를 최소로 남긴다', () => {
    // 격자가 지나치게 크면 패널이 작아져 30초 안에 읽히지 않는다.
    for (let n = 1; n <= 40; n++) {
      const { cols, rows } = panelGrid(n);
      expect(cols * rows - n).toBeLessThan(cols);
    }
  });

  it('가로가 세로보다 길거나 같다 — 프로젝터는 가로가 넓다', () => {
    for (let n = 1; n <= 40; n++) {
      const { cols, rows } = panelGrid(n);
      expect(cols).toBeGreaterThanOrEqual(rows);
    }
  });

  // --- 아래는 브리프의 넷을 강화하려고 추가한 것이다. 위 넷은 손대지
  // 않는다.
  //
  // 위 네 단언은 모두 `panelGrid(n) = { cols: n, rows: 1 }` 같은 구현도
  // 통과시킨다 — cols*rows == n(빈 자리 0, 최소), cols >= rows(n >= 1일
  // 때 항상 참)를 자명하게 만족하기 때문이다. 이 구현이 12·16에서만 예외로
  // {4,3}·{4,4}를 하드코딩하고 나머지는 전부 한 줄로 반환해도 위 네
  // 테스트를 모두 통과한다. 그러면 11이나 13처럼 12·16 바로 옆의 흔한
  // 값에서 화면이 한 줄짜리 패널 11개를 가로로 늘어놓는 사고가 나도
  // 아무 테스트도 잡지 못한다.
  //
  // 처음엔 이 구멍을 12·16 옆 정수의 정확한 기대값(toEqual)으로 막았다.
  // 그런데 그 기대값 중 일부(n=15 → {4,4}, n=24 → {5,5})는 독립적으로
  // 유도한 목표가 아니라 구현이 실제로 내놓는 값을 그대로 베낀 것이었고,
  // 이름도 "빈 자리를 최소로 남긴다"로 붙여 과잉 약속을 했다 —
  // ceil(sqrt(n))은 빈 자리를 최소화하지 않는다(n=24는 {5,5}, 빈 자리
  // 1칸을 내지만 {6,4}는 정확히 24칸을 채운다). 이러면 나중에 누군가
  // panelGrid를 실제로 다른 배치로 고쳐도 이 테스트가 실패해 "되돌리라"고
  // 잘못 말한다 — 테스트가 자기가 잡으려던 바로 그 결함(특정 튜플에
  // 고정됨)을 스스로 저지르는 동어반복이었다.
  //
  // panelGrid의 진짜 설계 목표는 "빈 자리 최소화"가 아니라 "근사
  // 정사각형(cols와 rows 차이가 1 이하) + 낭비는 한 열 미만"이다 — 이게
  // 프로젝터 화면비에 맞는 목표다. 빈 자리 최소화를 목표로 삼으면 n=13
  // 같은 소수에서 {7,2}나 {13,1}처럼 프로젝터에 못 쓸 배치가 나온다.
  // 그래서 정확한 튜플을 못박는 대신 근사 정사각형 성질 자체를 단언한다
  // — {n,1}은 여전히 걸리지만(n>2면 cols-rows = n-1 > 1), panelGrid가
  // 미래에 같은 목표 아래 다른(더 정사각형에 가깝거나 같은) 튜플을
  // 내놓아도 이 테스트는 안 깨진다.
  it('12·16 주변 값도 근사 정사각형 + 낭비는 한 열 미만이다 — {n,1} 한 줄 배치를 잡되 특정 튜플에 고정하지 않는다', () => {
    for (const n of [1, 2, 9, 11, 13, 15, 20, 24, 36]) {
      const { cols, rows } = panelGrid(n);
      expect(cols * rows).toBeGreaterThanOrEqual(n);       // 모든 패널이 자리를 갖는다
      expect(cols * rows - n).toBeLessThan(cols);           // 낭비는 한 열 미만
      expect(cols).toBeGreaterThanOrEqual(rows);            // 가로가 세로보다 길거나 같다
      expect(cols - rows).toBeLessThanOrEqual(1);           // 근사 정사각형
    }
  });

  it('count가 1보다 작거나 정수가 아니면 던진다 — 갤러리가 빈 배열로 조용히 렌더되는 것을 막는다', () => {
    expect(() => panelGrid(0)).toThrow();
    expect(() => panelGrid(-1)).toThrow();
    expect(() => panelGrid(1.5)).toThrow();
  });
});
