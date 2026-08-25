import { loadBundle } from '@/lib/bundle';
import { DataSourceBanner } from '@/components/DataSourceBanner';
import { CurveChart } from '@/components/CurveChart';

/**
 * 화면 2 — 개선 곡선. 서버 컴포넌트로 번들을 읽어 `GenerationStat[]`만
 * 클라이언트 컴포넌트(`CurveChart`)에 넘긴다 — `curveSeries`/`r3Ratio`/
 * `r3Passed`/`r3Threshold`(순수 함수, `loadBundle`을 부르지 않는다)는
 * `CurveChart` 안에서 계산한다. 화면 1(`app/gallery/page.tsx`)의
 * 서버/클라이언트 분리와 같은 이유 — 여기서 계산해 넘기면 R1과는
 * 무관하지만(재계산이 아니라 어디서 부르느냐의 문제), 상태 없는
 * 화면을 서버·클라이언트로 쪼개는 관례를 그대로 따른 것이다.
 */
export default function CurvePage() {
  const bundle = loadBundle();

  return (
    <>
      <DataSourceBanner demo={bundle.meta.demo} />
      <CurveChart stats={bundle.generations} />
    </>
  );
}
