import { diffLines } from 'diff';
import type { SourceIndexEntry } from './schema';

export interface DiffLine {
  kind: 'add' | 'del' | 'ctx';
  text: string;
}

/**
 * 세대 N의 소스를 직전 세대 N-1과 비교해 줄 단위 diff를 낸다.
 *
 * diff 계산 자체는 검증된 'diff' 패키지(diffLines — Myers 알고리즘 기반)에
 * 맡긴다. Task 3의 판정이 이것이다: 백엔드는 diff를 만들지 않고 소스
 * 텍스트만 싣고, 프론트가 빌드 타임에 계산한다 — LCS를 직접 짜 넣지
 * 않는다. `diffLines`가 낸 조각(Change)은 여러 줄을 한 뭉치(value)로
 * 묶어서 낼 수 있으므로, 화면이 줄 단위로 색을 칠 수 있도록 여기서
 * 개별 줄로 쪼갠다 — 쪼개지 않으면 "여러 줄이 한꺼번에 추가됐다"가
 * 하나의 DiffLine에 개행이 섞인 텍스트로 뭉쳐 나가, 브리프의
 * every-line 테스트는 통과하면서도 화면은 줄 단위 diff가 아니게 된다.
 *
 * "소스가 없다"는 두 자리에서 따로 다루고, 어느 쪽도 던지지 않는다:
 *  - 이번 세대(`generation`) 자체가 `sources`에 없거나 available이
 *    아니거나 텍스트가 없으면 보여줄 것이 없다 — 빈 배열.
 *  - 직전 세대(`generation - 1`)의 텍스트가 없으면 빈 문자열과
 *    비교한다 — 결과는 전부 추가다. 세대 0이 정확히 이 경우고(비교
 *    대상 자체가 없다), 세대 루프가 아직 안 돌아 중간 세대 소스가
 *    빠진 경우도 같은 경로로 처리된다.
 */
export function diffAgainstPrevious(
  sources: SourceIndexEntry[],
  sourceText: Record<number, string>,
  generation: number,
): DiffLine[] {
  const entry = sources.find((s) => s.generation === generation);
  if (!entry || !entry.available) return [];

  const current = sourceText[generation];
  if (current === undefined) return [];

  const previous = sourceText[generation - 1] ?? '';

  return diffLines(previous, current).flatMap((part) => {
    const kind: DiffLine['kind'] = part.added ? 'add' : part.removed ? 'del' : 'ctx';
    const lines = part.value.split('\n');
    // split은 마지막 개행 뒤에 빈 문자열을 하나 더 낸다 — 파일이 보통
    // 개행으로 끝나므로, 그 인공적인 빈 줄을 결과에 섞지 않는다.
    if (lines.length > 0 && lines[lines.length - 1] === '') lines.pop();
    return lines.map((text) => ({ kind, text }));
  });
}
