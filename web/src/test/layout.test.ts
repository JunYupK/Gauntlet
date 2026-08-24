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

  // --- 아래는 브리프의 넷을 강화하려고 추가한 것들이다. 위 넷은 손대지
  // 않는다.
  //
  // 위 네 단언은 모두 `panelGrid(n) = { cols: n, rows: 1 }` 같은 구현도
  // 통과시킨다 — cols*rows == n(빈 자리 0, 최소), cols >= rows(n >= 1일
  // 때 항상 참)를 자명하게 만족하기 때문이다. 이 구현이 12·16에서만 예외로
  // {4,3}·{4,4}를 하드코딩하고 나머지는 전부 한 줄로 반환해도 위 네
  // 테스트를 모두 통과한다. 그러면 11이나 13처럼 12·16 바로 옆의 흔한
  // 값에서 화면이 한 줄짜리 패널 11개를 가로로 늘어놓는 사고가 나도
  // 아무 테스트도 잡지 못한다. 12·16 옆의 정수를 정사각형에 가까운
  // 배치의 정확한 기대값과 함께 걸어서 그 구멍을 막는다.
  it('12·16 주변 값도 정사각형에 가까운 배치를 낸다 — 그 둘만 하드코딩하는 구현을 잡는다', () => {
    expect(panelGrid(1)).toEqual({ cols: 1, rows: 1 });
    expect(panelGrid(2)).toEqual({ cols: 2, rows: 1 });
    expect(panelGrid(9)).toEqual({ cols: 3, rows: 3 });
    expect(panelGrid(11)).toEqual({ cols: 4, rows: 3 });
    expect(panelGrid(13)).toEqual({ cols: 4, rows: 4 });
    expect(panelGrid(15)).toEqual({ cols: 4, rows: 4 });
    expect(panelGrid(20)).toEqual({ cols: 5, rows: 4 });
    expect(panelGrid(24)).toEqual({ cols: 5, rows: 5 });
    expect(panelGrid(36)).toEqual({ cols: 6, rows: 6 });
  });

  it('count가 1보다 작거나 정수가 아니면 던진다 — 갤러리가 빈 배열로 조용히 렌더되는 것을 막는다', () => {
    expect(() => panelGrid(0)).toThrow();
    expect(() => panelGrid(-1)).toThrow();
    expect(() => panelGrid(1.5)).toThrow();
  });
});
