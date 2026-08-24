import { loadBundle } from '@/lib/bundle';
import { DataSourceBanner } from '@/components/DataSourceBanner';
import { HeatmapView } from '@/components/HeatmapView';

/**
 * 화면 6(부록) — 히트맵과 과적합 격차. 서버 컴포넌트로 번들을 읽어
 * `roundRobin`/`generations`만 클라이언트 컴포넌트(`HeatmapView`)에
 * 넘긴다 — `cycles`/`overfitGap`(순수 함수, `loadBundle`을 부르지
 * 않는다)은 `HeatmapView` 안에서 계산한다. 화면 2·4와 같은 서버/
 * 클라이언트 분리 관례다.
 */
export default function HeatmapPage() {
  const bundle = loadBundle();

  return (
    <>
      <DataSourceBanner demo={bundle.meta.demo} />
      <HeatmapView roundRobin={bundle.roundRobin} generations={bundle.generations} />
    </>
  );
}
