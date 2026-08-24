import { describe, it, expect } from 'vitest';
import { lossSeries, worstFor } from '../lib/worst';
import { loadBundle } from '../lib/bundle';

const diagnosis = {
  matchId: 'm', reach: [[9, 8, 7], [9, 8, 7]], loss: [[0, 5, 0], [0, 0, 3]],
  occupancy: [0, 0], suicideRate: [0, 0],
  worstMoves0: [{ turn: 2, chose: 'UP', best: 'DOWN', reachAfterChosen: 3,
    reachAfterBest: 8, loss: 5, suicide: false, fatal: false }],
  worstMoves1: [],
} as never;

describe('진단 인덱스 규약', () => {
  it('loss 계열의 turn은 1-based로 나온다', () => {
    // 배열은 0-based, 화면과 MoveAnalysis는 1-based다. 여기서 변환한다.
    expect(lossSeries(diagnosis, 0)).toEqual([
      { turn: 1, loss: 0 }, { turn: 2, loss: 5 }, { turn: 3, loss: 0 },
    ]);
  });

  it('가장 나쁜 수의 turn이 loss 계열의 봉우리와 같은 턴을 가리킨다', () => {
    const peak = lossSeries(diagnosis, 0).reduce((a, b) => (b.loss > a.loss ? b : a));
    expect(worstFor(diagnosis, 0)[0].turn).toBe(peak.turn);
  });

  it('좌석 1은 좌석 1의 배열을 읽는다', () => {
    expect(lossSeries(diagnosis, 1).find((p) => p.loss === 3)?.turn).toBe(3);
  });

  it('데모 번들의 모든 경기에서 worstMoves의 turn이 경기 턴 수 안에 있다', () => {
    const { gallery, diagnosis: all } = loadBundle();
    all.forEach((d, i) => {
      [...d.worstMoves0, ...d.worstMoves1].forEach((m) => {
        expect(m.turn).toBeGreaterThanOrEqual(1);
        expect(m.turn).toBeLessThanOrEqual(gallery[i].result.turns);
      });
    });
  });

  // 아래는 브리프에 없는 추가 케이스 — "이 단언을 통과하는 잘못된 구현은
  // 무엇인가"를 물어서 찾은 구멍을 막는다.

  it('off-by-one 구현(0-based turn을 그대로 씀)이면 봉우리 일치 테스트가 실패한다', () => {
    // 검증 방법: 여기서 "잘못된" lossSeries를 직접 흉내 내 turn을
    // i(0-based)로 채우면, worstFor가 돌려주는 1-based turn(=2)과
    // 어긋나는 걸 직접 확인한다 — 즉 브리프의 "봉우리 일치" 테스트가
    // 잡아내는 실패 모드가 정확히 이것이다.
    const brokenPeak = diagnosis.loss[0]
      .map((loss: number, i: number) => ({ turn: i, loss })) // 버그: i + 1이 아니라 i
      .reduce((a: { turn: number; loss: number }, b: { turn: number; loss: number }) =>
        (b.loss > a.loss ? b : a));
    expect(worstFor(diagnosis, 0)[0].turn).not.toBe(brokenPeak.turn);
    expect(brokenPeak.turn).toBe(1); // 버그가 내놓는 틀린 턴(진짜는 2)
  });

  it('loss가 전부 0이어도 turn 목록은 1-based 그대로 나온다(자멸 없는 경기도 인덱싱은 동일)', () => {
    const noLoss = { ...diagnosis, loss: [[0, 0, 0], [0, 0, 0]] } as never;
    expect(lossSeries(noLoss, 0).map((p: { turn: number }) => p.turn)).toEqual([1, 2, 3]);
  });

  it('worstFor는 목록을 재정렬·재계산하지 않고 백엔드가 준 순서·개수를 그대로 옮긴다', () => {
    const multi = {
      ...diagnosis,
      worstMoves0: [
        { turn: 2, chose: 'UP', best: 'DOWN', reachAfterChosen: 3, reachAfterBest: 8, loss: 5, suicide: false, fatal: false },
        { turn: 1, chose: 'LEFT', best: 'LEFT', reachAfterChosen: 9, reachAfterBest: 9, loss: 0, suicide: false, fatal: false },
      ],
    } as never;
    expect(worstFor(multi, 0)).toBe(multi.worstMoves0);
  });

  it('fatal:true지만 loss:0인 수도 worstFor에 그대로 실린다 — 정면 충돌은 자멸이 아니지만 fatal로 실린다', () => {
    const headOn = {
      ...diagnosis,
      worstMoves0: [{ turn: 3, chose: 'RIGHT', best: 'RIGHT', reachAfterChosen: 0,
        reachAfterBest: 0, loss: 0, suicide: false, fatal: true }],
    } as never;
    const move = worstFor(headOn, 0)[0];
    expect(move.fatal).toBe(true);
    expect(move.loss).toBe(0);
  });
});
