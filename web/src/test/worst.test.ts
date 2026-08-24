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

  it('봉우리가 배열 끝쪽(턴 4)에 있어도 lossSeries가 실제로 호출된 결과로 worstFor와 같은 턴을 가리킨다', () => {
    // 리뷰 지적(round 1): 이전 버전은 "잘못된" lossSeries를 이 테스트
    // 안에서 직접 흉내 내 계산만 했을 뿐 실제 lossSeries를 한 번도
    // 호출하지 않았다 — lossSeries를 완전히 다른 값을 내도록 바꿔도
    // 이 테스트는 계속 초록이었다(구현을 전혀 감시하지 않는 테스트).
    // 아래는 실제 lossSeries(diagnosis2, 0)를 호출해 그 반환값에서
    // 봉우리를 찾고, worstFor(diagnosis2, 0)의 turn과 비교한다 — 브리프
    // 테스트 2와 같은 성질을 검사하지만, 봉우리 위치가 다른(턴 2가 아닌
    // 턴 4) 별도 픽스처를 써서 문자 그대로 중복이 되지 않게 했다.
    const diagnosis2 = {
      matchId: 'm2', reach: [[9, 8, 7, 6], [9, 8, 7, 6]], loss: [[0, 1, 2, 9], [0, 0, 0, 0]],
      occupancy: [0, 0], suicideRate: [0, 0],
      worstMoves0: [{ turn: 4, chose: 'DOWN', best: 'LEFT', reachAfterChosen: 6,
        reachAfterBest: 15, loss: 9, suicide: false, fatal: false }],
      worstMoves1: [],
    } as never;

    const peak = lossSeries(diagnosis2, 0).reduce((a, b) => (b.loss > a.loss ? b : a));
    expect(peak.turn).toBe(4); // 참값. off-by-one이면 3이 나온다(아래 주입 검증).
    expect(worstFor(diagnosis2, 0)[0].turn).toBe(peak.turn);
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
