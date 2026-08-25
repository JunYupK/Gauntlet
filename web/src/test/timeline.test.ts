import { describe, it, expect } from 'vitest';
import { timelineRows, attemptTone, gateColor } from '../lib/timeline';
import { loadBundle } from '../lib/bundle';
import type { AttemptRecord } from '../lib/schema';

describe('루프 타임라인', () => {
  it('시도가 없는 세대도 행을 갖는다', () => {
    // 행이 사라지면 "이 세대는 한 번에 통과했다"와 "이 세대가 없다"가
    // 같은 그림이 된다.
    const rows = timelineRows({ '0': [], '1': [] },
      [{ generation: 0 }, { generation: 1 }] as never);
    expect(rows.length).toBe(2);
    expect(rows[0].attempts.length).toBe(0);
  });

  it('시도는 번호 순으로 늘어선다', () => {
    const history = { '0': [
      { generation: 0, attempt: 2, verdict: 'PROMOTED', stage: 'CHAMPIONSHIP', failedGate: null, detail: '' },
      { generation: 0, attempt: 1, verdict: 'REJECTED', stage: 'GATE', failedGate: 'G4', detail: '' },
    ] };
    const rows = timelineRows(history as never, [{ generation: 0 }] as never);
    expect(rows[0].attempts.map((a) => a.attempt)).toEqual([1, 2]);
  });

  it('반려 사유마다 다른 색을 준다', () => {
    const colors = ['G2', 'G3', 'G4', 'G5', 'G6', 'G7'].map(gateColor);
    expect(new Set(colors).size).toBe(6);
  });

  it('챔피언전 반려도 관문 반려와 구분된다', () => {
    // failedGate가 null인 반려는 챔피언전에서 승률이 모자란 것이다.
    // 관문 반려와 같은 색이면 C2의 이야기가 뭉개진다.
    expect(gateColor(null)).not.toBe(gateColor('G7'));
  });

  it('세 판정이 서로 다른 톤을 갖는다', () => {
    const tones = new Set([
      attemptTone({ verdict: 'PASSED' } as never),
      attemptTone({ verdict: 'PROMOTED' } as never),
      attemptTone({ verdict: 'REJECTED' } as never),
    ]);
    expect(tones.size).toBe(3);
  });

  it('데모 번들에 반려가 실제로 들어있다', () => {
    // 반려가 하나도 없으면 이 화면이 증명할 것이 없다.
    const { loopHistory } = loadBundle();
    const rejected = Object.values(loopHistory).flat()
      .filter((a) => a.verdict === 'REJECTED');
    expect(rejected.length).toBeGreaterThan(0);
  });

  // 아래는 브리프에 없는 추가 케이스 — "이 단언을 통과하는 잘못된
  // 구현은 무엇인가"를 물어서 찾은 구멍을 막는다.

  it('gateColor(null)은 실제 색 배정에서 G2..G7 어느 것과도 다르다', () => {
    // 브리프는 G7과의 구분만 고정했다 — null 하나가 G7과만 다르고
    // 나머지 다섯과 우연히 같은 색이어도 그 테스트는 통과한다.
    // 관문 반려 여섯 색 + 챔피언전 색을 합쳐 정말 일곱 개가 다 다른지
    // 여기서 마저 잠근다.
    const all = ['G2', 'G3', 'G4', 'G5', 'G6', 'G7'].map(gateColor).concat(gateColor(null));
    expect(new Set(all).size).toBe(7);
  });

  it('행은 generations 배열이 정하지, history의 키가 정하지 않는다', () => {
    // history에만 있고 generations에는 없는 세대는 존재하지 않는
    // 세대로 취급한다 — 번들의 세대 인덱스가 유일한 근거다.
    const history = {
      '0': [{ generation: 0, attempt: 1, verdict: 'PROMOTED', stage: 'CHAMPIONSHIP', failedGate: null, detail: '' }],
      '5': [{ generation: 5, attempt: 1, verdict: 'PROMOTED', stage: 'CHAMPIONSHIP', failedGate: null, detail: '' }],
    };
    const rows = timelineRows(history as never, [{ generation: 0 }] as never);
    expect(rows.length).toBe(1);
    expect(rows[0].generation).toBe(0);
  });

  it('generations 배열이 뒤섞여 있어도 세대 오름차순으로 행을 낸다', () => {
    // 배열 위치에 기댄 구현(예: 인덱스로 정렬 판단)은 뒤섞인 입력에서
    // 조용히 순서를 어긴다 — Task 9가 겪은 바로 그 함정.
    const rows = timelineRows(
      { '0': [], '1': [], '2': [] },
      [{ generation: 2 }, { generation: 0 }, { generation: 1 }] as never,
    );
    expect(rows.map((r) => r.generation)).toEqual([0, 1, 2]);
  });

  it('detail 문자열을 그대로 옮긴다 — 화면에서 다시 조립하지 않는다', () => {
    // R1: timelineRows는 detail을 손대지 않고 그대로 통과시켜야 한다.
    const detail = '승점 승률 0.48 (기준 0.60)';
    const record: AttemptRecord = {
      generation: 0, attempt: 1, verdict: 'REJECTED', stage: 'CHAMPIONSHIP',
      failedGate: null, detail,
    };
    const rows = timelineRows({ '0': [record] }, [{ generation: 0 }] as never);
    expect(rows[0].attempts[0].detail).toBe(detail);
  });
});
