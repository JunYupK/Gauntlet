import { loadBundle } from '@/lib/bundle';
import { DataSourceBanner } from '@/components/DataSourceBanner';
import { DiffViewer } from '@/components/DiffViewer';

/**
 * 화면 4 — 세대별 코드 diff. "이번 세대는 무엇을 배웠나"를 코드로
 * 보여준다(C1, BRIEF §6 — 개선은 파라미터 튜닝이 아니라 코드 재작성이다).
 *
 * 서버 컴포넌트로 번들을 읽어 `sources`/`sourceText`만 클라이언트
 * 컴포넌트(`DiffViewer`)에 넘긴다 — `diffAgainstPrevious`(순수 함수,
 * `loadBundle`을 부르지 않는다)는 `DiffViewer` 안에서, 세대 선택이
 * 바뀔 때마다 계산한다. 화면 1·2·3과 같은 서버/클라이언트 분리 관례다.
 */
export default function DiffPage() {
  const bundle = loadBundle();

  return (
    <>
      <DataSourceBanner demo={bundle.meta.demo} />
      <DiffViewer sources={bundle.sources} sourceText={bundle.sourceText} />
    </>
  );
}
