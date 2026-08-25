import { describe, it, expect } from 'vitest';
import { trailAlpha, TRAIL } from '../lib/trail';

describe('트레일 밝기', () => {
  it('가장 최근 칸이 가장 밝다', () => {
    expect(trailAlpha(0, 20)).toBeGreaterThan(trailAlpha(5, 20));
    expect(trailAlpha(5, 20)).toBeGreaterThan(trailAlpha(19, 20));
  });

  it('오래된 칸도 완전히 사라지지는 않는다', () => {
    // 벽은 영구적이다 — 안 보이면 화면이 "이 봇이 얼마나 채웠나"를
    // 못 보여준다. 갤러리가 순위표가 되는 근거가 사라진다.
    expect(trailAlpha(999, 20)).toBeGreaterThan(0.15);
  });

  it('밝기는 0과 1 사이다', () => {
    for (const age of [0, 1, 7, 20, 500]) {
      expect(trailAlpha(age, 20)).toBeGreaterThan(0);
      expect(trailAlpha(age, 20)).toBeLessThanOrEqual(1);
    }
  });

  it('진행 방향이 읽히려면 최근 구간의 기울기가 충분해야 한다', () => {
    // "정지 화면에서도 방향이 읽힌다"를 판정 가능한 형태로 바꾼 것:
    // 머리와 20칸 뒤의 밝기 차가 0.3 이상이어야 눈에 띈다.
    expect(trailAlpha(0, 20) - trailAlpha(20, 20)).toBeGreaterThanOrEqual(0.3);
  });

  // --- 아래는 브리프의 넷을 강화하려고 추가한 것들이다. 위 넷은 손대지
  // 않는다. 점 몇 개(0, 5, 19)만 비교하는 첫 단언은 그 사이에서 밝기가
  // 튀는(비단조) 구현도 통과시킨다 — 예를 들어 나이 3에서만 반짝 밝아지는
  // 구현도 0<5<19 세 점만 보면 걸리지 않는다. 전 구간을 단조 비증가로
  // 훑어야 그런 구현을 잡는다.
  it('나이가 늘수록 밝기는 어디서도 튀지 않고 단조 비증가한다', () => {
    for (let age = 0; age < 100; age++) {
      expect(trailAlpha(age, TRAIL)).toBeGreaterThanOrEqual(trailAlpha(age + 1, TRAIL));
    }
  });

  // 상수 함수는 위 단조성 검사도 통과한다(같은 값이 계속 나오면
  // "감소하지 않는다"는 참이니까) — 이 단언이 상수 함수를 따로 잡는다.
  it('상수 함수가 아니다 — 실제로 감소한다', () => {
    expect(trailAlpha(0, TRAIL)).not.toBe(trailAlpha(10, TRAIL));
  });

  it('TRAIL은 20이다 — Task 8·12가 같은 상수를 쓴다', () => {
    expect(TRAIL).toBe(20);
  });
});
