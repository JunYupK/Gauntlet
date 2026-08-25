import { describe, it, expect } from 'vitest';
import { diffAgainstPrevious } from '../lib/diff';

const sources = [
  { generation: 0, botName: 'A', available: true, file: 'x' },
  { generation: 1, botName: 'B', available: true, file: 'y' },
  { generation: 2, botName: 'C', available: false, file: null },
];

describe('세대 diff', () => {
  it('Gen 0은 비교 대상이 없어 전부 추가로 나온다', () => {
    const lines = diffAgainstPrevious(sources, { 0: 'a\nb\n' }, 0);
    expect(lines.every((l) => l.kind === 'add')).toBe(true);
  });

  it('바뀐 줄만 add/del로 표시된다', () => {
    const lines = diffAgainstPrevious(sources, { 0: 'a\nb\nc\n', 1: 'a\nX\nc\n' }, 1);
    expect(lines.filter((l) => l.kind === 'del').map((l) => l.text.trim())).toEqual(['b']);
    expect(lines.filter((l) => l.kind === 'add').map((l) => l.text.trim())).toEqual(['X']);
  });

  it('소스가 없는 세대는 빈 결과를 준다 — 던지지 않는다', () => {
    // 세대 루프가 아직 안 돈 세대에서 화면 전체가 죽으면 안 된다.
    expect(diffAgainstPrevious(sources, { 0: 'a\n', 1: 'b\n' }, 2)).toEqual([]);
  });

  it('직전 세대의 소스가 없으면 전부 추가로 나온다', () => {
    const lines = diffAgainstPrevious(sources, { 1: 'a\n' }, 1);
    expect(lines.every((l) => l.kind === 'add')).toBe(true);
  });

  it('여러 줄이 한꺼번에 추가/삭제되면 줄 단위로 각각 나뉜다', () => {
    // 이 테스트가 없으면 diffLines가 낸 여러 줄짜리 뭉치(value)를 하나의
    // DiffLine에 개행 채로 욱여넣고도(줄 단위로 쪼개지 않고도) 위의
    // "every line is add" 테스트를 통과해버린다 — 그 두 assert는 add
    // 뭉치가 몇 줄이든 상관하지 않기 때문이다. 여기서는 삭제 1줄 +
    // 추가 3줄을 넣어 각 줄이 독립된 DiffLine으로 나오는지, 순서가
    // 보존되는지를 직접 잰다.
    const lines = diffAgainstPrevious(sources, { 0: 'a\nb\n', 1: 'a\nc\nd\ne\n' }, 1);
    const dels = lines.filter((l) => l.kind === 'del').map((l) => l.text.trim());
    const adds = lines.filter((l) => l.kind === 'add').map((l) => l.text.trim());
    expect(dels).toEqual(['b']);
    expect(adds).toEqual(['c', 'd', 'e']);
  });
});
