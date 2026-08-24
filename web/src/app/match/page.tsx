import { loadBundle } from '@/lib/bundle';
import { DataSourceBanner } from '@/components/DataSourceBanner';
import { MatchDiagnosisView } from '@/components/MatchDiagnosisView';

/**
 * 화면 5 — 단일 경기 + 진단. 서버 컴포넌트로 번들을 읽어 `Replay[]`와
 * `MatchDiagnosis[]`만 클라이언트 컴포넌트(`MatchDiagnosisView`)에
 * 넘긴다 — `decodeReplay`도 `lossSeries`/`worstFor`(순수 함수,
 * `lib/worst.ts`, `loadBundle`을 부르지 않는다)도 클라이언트 쪽에서
 * 부른다. 화면 1(`app/gallery/page.tsx`)의 서버/클라이언트 분리와
 * 같은 이유 — 디코딩된 상태(턴 수백 개의 격자)를 여기서 만들어
 * 넘기면 이 페이지의 HTML이 직렬화된 상태로 수 MB가 된다.
 */
export default function MatchPage() {
  const bundle = loadBundle();

  return (
    <>
      <DataSourceBanner demo={bundle.meta.demo} />
      <MatchDiagnosisView gallery={bundle.gallery} diagnosis={bundle.diagnosis} />
    </>
  );
}
