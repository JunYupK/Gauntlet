import { describe, it, expect } from 'vitest';
import { decodeReplay, owner } from '../lib/replay';
import type { Replay } from '../lib/schema';

/** 첫 턴에 봇0이 뒤로 가고 봇1은 직진하는 최소 경기. */
const 후진_경기: Replay = {
  schema: 1, matchId: 'test-back', width: 30, height: 30, seed: 1, swapped: false,
  bot0Id: 'a', start0: { x: 10, y: 10 }, dir0: 'RIGHT',
  bot1Id: 'b', start1: { x: 20, y: 20 }, dir1: 'LEFT',
  moves: 'LL',
  result: { winner: 1, turns: 1, reason: 'P0_HIT_OWN_WALL' },
  hash: 'sha256:테스트픽스처',
};

describe('시작 격자', () => {
  it('시작 칸 뒤 칸도 벽이다 — 첫 턴 후진이 자기 벽 충돌로 죽는다', () => {
    // 봇0은 RIGHT로 시작하므로 (9,10)이 처음부터 벽이다. LEFT를 내면
    // 거기 박는다. 뒤 칸을 안 잡으면 이 경기는 죽지 않고 계속된다.
    const decoded = decodeReplay(후진_경기);
    expect(decoded.turnCount).toBe(1);
    expect(decoded.winner).toBe(1);
    expect(decoded.reason).toBe('P0_HIT_OWN_WALL');
  });

  it('턴 1의 격자에 벽이 정확히 4칸 있다', () => {
    const decoded = decodeReplay(후진_경기);
    let walls = 0;
    for (let x = 0; x < 30; x++) {
      for (let y = 0; y < 30; y++) if (owner(decoded, 1, x, y) !== null) walls++;
    }
    expect(walls).toBe(4);
  });
});

describe('동시 판정', () => {
  it('같은 칸에 동시 진입하면 무승부다', () => {
    const 정면충돌: Replay = {
      schema: 1, matchId: 'test-headon', width: 30, height: 30, seed: 1, swapped: false,
      bot0Id: 'a', start0: { x: 10, y: 10 }, dir0: 'RIGHT',
      bot1Id: 'b', start1: { x: 12, y: 10 }, dir1: 'LEFT',
      moves: 'RL',
      result: { winner: -1, turns: 1, reason: 'HEAD_ON_COLLISION' },
      hash: 'sha256:테스트픽스처',
    };
    const decoded = decodeReplay(정면충돌);
    expect(decoded.winner).toBe(-1);
    expect(decoded.reason).toBe('HEAD_ON_COLLISION');
  });

  it('죽은 봇의 머리는 벽이 되지 않는다', () => {
    // 봇0만 죽는 턴에 확정되는 벽은 봇1의 머리 하나뿐이다 (스펙 §7.1).
    const decoded = decodeReplay(후진_경기);
    const last = decoded.turns[decoded.turns.length - 1];
    expect(last.claimed[0]).toBeNull();
    expect(last.claimed[1]).not.toBeNull();
  });
});
