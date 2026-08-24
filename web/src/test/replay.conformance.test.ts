import { describe, it, expect } from 'vitest';
import { loadBundle } from '../lib/bundle';
import { decodeReplay } from '../lib/replay';

describe('디코더 ↔ 엔진 대조', () => {
  const { gallery } = loadBundle();

  it('번들에 대조할 경기가 실제로 들어있다', () => {
    // 갤러리가 비면 아래 테스트들이 0번 돌고도 통과한다.
    expect(gallery.length).toBeGreaterThanOrEqual(12);
  });

  it.each(gallery.map((r) => [r.matchId, r] as const))(
    '%s — 승자·턴수·사망사유가 엔진과 같다',
    (_id, replay) => {
      const decoded = decodeReplay(replay);
      expect(decoded.turnCount).toBe(replay.result.turns);
      expect(decoded.winner).toBe(replay.result.winner);
      expect(decoded.reason).toBe(replay.result.reason);
    },
  );

  it.each(gallery.map((r) => [r.matchId, r] as const))(
    '%s — 벽은 턴마다 생존 봇 수만큼만 늘어난다',
    (_id, replay) => {
      // 스펙 §7.1의 벽 단조성. 이게 깨지면 디코더가 죽은 봇의 머리를
      // 벽으로 잡았거나 살아있는 봇의 머리를 빠뜨린 것이다.
      const decoded = decodeReplay(replay);
      decoded.turns.forEach((state) => {
        const claimedCount = state.claimed.filter((p) => p !== null).length;
        const aliveCount = state.alive.filter(Boolean).length;
        expect(claimedCount).toBe(aliveCount);
      });
    },
  );
});
