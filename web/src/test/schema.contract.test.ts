import { describe, it, expect } from 'vitest';
import { loadBundle } from '../lib/bundle';

describe('번들 스키마 계약', () => {
  it('데모 번들 전체가 스키마를 통과한다', () => {
    // loadBundle이 Zod로 parse 하므로, 통과한다는 것 자체가 계약이
    // 지켜졌다는 뜻이다. 백엔드가 필드를 바꾸면 여기서 죽는다.
    const bundle = loadBundle();
    expect(bundle.generations.length).toBeGreaterThan(0);
  });

  it('갤러리와 진단이 matchId로 짝지어진다', () => {
    const { gallery, diagnosis } = loadBundle();
    expect(diagnosis.length).toBe(gallery.length);
    gallery.forEach((replay, i) => {
      expect(diagnosis[i].matchId).toBe(replay.matchId);
    });
  });

  it('라운드로빈 행렬은 정사각이고 대각선이 null이다', () => {
    const { roundRobin } = loadBundle();
    const n = roundRobin.bots.length;
    expect(roundRobin.matrix.length).toBe(n);
    roundRobin.matrix.forEach((row, i) => {
      expect(row.length).toBe(n);
      expect(row[i]).toBeNull();
    });
  });

  it('루프 이력의 키가 세대 번호를 빠짐없이 덮는다', () => {
    const { generations, loopHistory } = loadBundle();
    generations.forEach((g) => {
      expect(loopHistory[String(g.generation)]).toBeDefined();
    });
  });

  it('소스 인덱스가 세대와 같은 길이다', () => {
    const { generations, sources } = loadBundle();
    expect(sources.length).toBe(generations.length);
  });

  it('알 수 없는 필드가 들어오면 거부한다', () => {
    // 스키마가 strict가 아니면 백엔드가 필드 이름을 바꿔도 조용히
    // undefined가 흘러 화면이 빈 값을 그린다. 그건 계약 테스트가 아니다.
    const { GenerationStatSchema } = require('../lib/schema');
    expect(() =>
      GenerationStatSchema.parse({
        generation: 0, botName: 'X', avgSurvivalTurns: 1, occupancy: 0,
        suicideRate: 0, scoreRate: 0, holdoutScoreRate: 0, attempts: 0,
        오타필드: 1,
      }),
    ).toThrow();
  });
});
