import { describe, it, expect } from 'vitest';
import { cycles, overfitGap } from '../lib/heatmap';

describe('순환 우위', () => {
  it('A>B>C>A를 찾아낸다', () => {
    // matrix[i][j] = i가 j를 상대로 낸 승점 승률. 0.5 초과면 우위.
    const m = [
      [null, 0.8, 0.2],
      [0.2, null, 0.8],
      [0.8, 0.2, null],
    ];
    expect(cycles(m)).toEqual([[0, 1, 2]]);
  });

  it('일관된 서열에는 순환이 없다', () => {
    const m = [
      [null, 0.8, 0.9],
      [0.2, null, 0.8],
      [0.1, 0.2, null],
    ];
    expect(cycles(m)).toEqual([]);
  });

  it('대각선 null에서 터지지 않는다', () => {
    expect(() => cycles([[null]])).not.toThrow();
    // not.toThrow()만으로는 "잘못된 값을 냈지만 안 터졌다"도 통과한다.
    // 실제로 빈 배열을 냈는지까지 별도로 확인한다.
    expect(cycles([[null]])).toEqual([]);
  });

  it('반대 방향 순환(i→k→j→i)도 정준 형태로 한 번만 보고한다', () => {
    // 0이 2를, 2가 1을, 1이 0을 이기는 순환 — 위 첫 테스트와 반대 방향.
    const m = [
      [null, 0.2, 0.8],
      [0.8, null, 0.2],
      [0.2, 0.8, null],
    ];
    expect(cycles(m)).toEqual([[0, 2, 1]]);
  });

  it('무승부(정확히 0.5)는 어느 쪽 우위도 아니다 — 순환에 안 낀다', () => {
    const m = [
      [null, 0.5, 0.8],
      [0.5, null, 0.8],
      [0.2, 0.2, null],
    ];
    expect(cycles(m)).toEqual([]);
  });

  it('4개 봇 중 3개짜리 순환 하나만 있으면 그 삼각형만 낸다', () => {
    // {1,2,3}은 1>2>3>1 순환. 0은 전부에게 진다 — 어떤 삼각형에도 안 낀다.
    const m = [
      [null, 0.1, 0.1, 0.1],
      [0.9, null, 0.8, 0.2],
      [0.9, 0.2, null, 0.8],
      [0.9, 0.8, 0.2, null],
    ];
    expect(cycles(m)).toEqual([[1, 2, 3]]);
  });
});

describe('과적합 격차', () => {
  const stat = (scoreRate: number, holdoutScoreRate: number) =>
    ({ generation: 0, botName: 'X', avgSurvivalTurns: 0, occupancy: 0,
       suicideRate: 0, scoreRate, holdoutScoreRate, attempts: 1 });

  it('심사 − 홀드아웃이다', () => {
    expect(overfitGap(stat(0.70, 0.58))).toBeCloseTo(0.12);
  });

  it('홀드아웃이 NaN이면 격차를 주장하지 않는다', () => {
    // 승격한 시도가 없는 세대다. 0으로 그리면 "격차 없음"으로 읽혀
    // 과적합이 없다는 거짓 주장이 된다.
    const result = overfitGap(stat(0.70, NaN));
    // toBeNull()은 Object.is(value, null)과 같다 — falsy 검사가 아니라
    // 정확히 null인지를 본다. 0을 반환하는 잘못된 구현은 이 assertion에
    // 걸린다(0은 null이 아니다).
    expect(result).toBeNull();
    expect(result).toBe(null);
  });

  it('홀드아웃이 심사보다 높으면 음수 격차를 그대로 낸다(과적합 아님, 거짓 낙관 아님)', () => {
    expect(overfitGap(stat(0.25, 0.6))).toBeCloseTo(-0.35);
  });

  it('심사와 홀드아웃이 같으면 정확히 0이다 — null과 구별된다', () => {
    const result = overfitGap(stat(0.5, 0.5));
    expect(result).toBe(0);
    expect(result).not.toBeNull();
  });
});
