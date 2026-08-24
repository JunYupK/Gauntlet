import { describe, it, expect } from 'vitest';
import { r3Ratio, r3Passed, r3Threshold, curveSeries } from '../lib/curve';
import { loadBundle } from '../lib/bundle';
import type { GenerationStat } from '../lib/schema';

const stat = (generation: number, avgSurvivalTurns: number): GenerationStat => ({
  generation, botName: `Gen${generation}`, avgSurvivalTurns,
  occupancy: 0, suicideRate: 0, scoreRate: 0, holdoutScoreRate: NaN, attempts: 1,
});

describe('R3 판정', () => {
  it('마지막 세대 ÷ Gen 0 이 배율이다', () => {
    expect(r3Ratio([stat(0, 10), stat(1, 50), stat(2, 150)])).toBeCloseTo(15);
  });

  it('합격선은 10배다 — 스펙 §13', () => {
    expect(r3Passed([stat(0, 10), stat(1, 99)])).toBe(false);
    expect(r3Passed([stat(0, 10), stat(1, 100)])).toBe(true);  // 경계값 포함
  });

  it('Gen 0의 생존 턴이 0이면 배율을 주장하지 않는다', () => {
    // 0으로 나눈 Infinity를 "무한히 개선됐다"로 그리면 거짓말이 된다.
    expect(Number.isFinite(r3Ratio([stat(0, 0), stat(1, 100)]))).toBe(false);
    expect(r3Passed([stat(0, 0), stat(1, 100)])).toBe(false);
  });

  it('데모 번들이 실제로 R3을 넘는다', () => {
    expect(r3Passed(loadBundle().generations)).toBe(true);
  });

  // 아래는 브리프에 없는 추가 케이스 — "이 단언을 통과하는 잘못된
  // 구현은 무엇인가"를 물어서 찾은 구멍을 막는다.

  it('배열 순서가 아니라 generation 값으로 Gen 0과 마지막 세대를 찾는다', () => {
    // stats[0]/stats[stats.length-1]에 기댄 구현은 정렬된 입력에서는
    // 위 테스트들을 전부 통과하지만, 뒤섞인 입력에서는 틀린 기준점을
    // 골라 조용히 다른 배율을 낸다.
    const shuffled = [stat(2, 150), stat(0, 10), stat(1, 50)];
    expect(r3Ratio(shuffled)).toBeCloseTo(15);
    expect(r3Passed(shuffled)).toBe(true);
  });

  it('경계값 바로 아래(9.999...)는 여전히 불합격이다', () => {
    // >= 대신 > 10을 쓴 구현이나 부동소수점 오차로 10을 근사하는
    // 구현은 이 경계 바로 밑에서 흔들린다.
    expect(r3Passed([stat(0, 10), stat(1, 99.99)])).toBe(false);
  });

  it('세대가 하나뿐이면(Gen 0만 있으면) 배율을 주장하지 않는다', () => {
    // baseline과 latest가 같은 원소를 가리켜 1을 내는 구현은 "그대로다"를
    // "개선됐다"로 왜곡하지 않지만, 그래도 배율 1은 무의미하다 —
    // 최소한 두 세대가 있어야 배율을 말할 자격이 있다는 걸 확인한다.
    // (avgSurvivalTurns가 0이 아닌 한 이 값은 1이 나오는 게 맞다 —
    // 여기서 확인하는 건 그게 10 미만이라 불합격으로 이어진다는 것.)
    expect(r3Passed([stat(0, 10)])).toBe(false);
  });

  it('빈 배열은 예외를 던지지 않고 배율을 주장하지 않는다', () => {
    expect(Number.isFinite(r3Ratio([]))).toBe(false);
    expect(r3Passed([])).toBe(false);
  });
});

describe('r3Threshold', () => {
  it('Gen 0 평균 생존 턴 × 10이다', () => {
    expect(r3Threshold([stat(0, 16), stat(1, 300)])).toBeCloseTo(160);
  });

  it('데모 번들의 문턱값은 Gen 0 평균의 10배와 같고, 마지막 세대가 이를 넘는다', () => {
    const stats = loadBundle().generations;
    const threshold = r3Threshold(stats);
    const last = [...stats].sort((a, b) => a.generation - b.generation).at(-1)!;
    expect(last.avgSurvivalTurns).toBeGreaterThanOrEqual(threshold);
  });
});

describe('curveSeries', () => {
  it('네 계열을 세대 오름차순 점 배열로 편다 — 재계산 없이 번들 값을 그대로 옮긴다', () => {
    const stats = [stat(1, 50), stat(0, 10), stat(2, 150)];
    stats[0].scoreRate = 0.5; stats[1].scoreRate = 0.1; stats[2].scoreRate = 0.9;

    const series = curveSeries(stats);
    expect(series.map((s) => s.key)).toEqual(
      ['avgSurvivalTurns', 'scoreRate', 'occupancy', 'suicideRate'],
    );

    const survival = series.find((s) => s.key === 'avgSurvivalTurns')!;
    expect(survival.points).toEqual([
      { x: 0, y: 10 }, { x: 1, y: 50 }, { x: 2, y: 150 },
    ]);

    const score = series.find((s) => s.key === 'scoreRate')!;
    expect(score.points).toEqual([
      { x: 0, y: 0.1 }, { x: 1, y: 0.5 }, { x: 2, y: 0.9 },
    ]);
  });

  it('데모 번들 12세대 전부가 avgSurvivalTurns 계열에 실린다', () => {
    const series = curveSeries(loadBundle().generations);
    const survival = series.find((s) => s.key === 'avgSurvivalTurns')!;
    expect(survival.points).toHaveLength(12);
    expect(survival.points.map((p) => p.x)).toEqual(
      Array.from({ length: 12 }, (_, i) => i),
    );
  });
});
