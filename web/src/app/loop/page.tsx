import { loadBundle } from '@/lib/bundle';
import { DataSourceBanner } from '@/components/DataSourceBanner';
import { LoopTimeline } from '@/components/LoopTimeline';

/**
 * 화면 3 — 루프 타임라인. 스펙 §9.2가 "발표의 진짜 주인공"이라고 못박은
 * 화면이다: 반려된 시도가 세대마다 빨간 칸으로 늘어선 것이 "루프가
 * 돌았다"(C2)의 가장 직접적인 증거다.
 *
 * 서버 컴포넌트로 번들을 읽어 `loopHistory`/`generations`만 클라이언트
 * 컴포넌트(`LoopTimeline`)에 넘긴다 — `timelineRows`/`attemptTone`/
 * `gateColor`(순수 함수, `loadBundle`을 부르지 않는다)는 `LoopTimeline`
 * 안에서 계산한다. 화면 1(`app/gallery/page.tsx`), 화면 2(`app/curve/page.tsx`)의
 * 서버/클라이언트 분리와 같은 관례다.
 */
export default function LoopPage() {
  const bundle = loadBundle();

  return (
    <>
      <DataSourceBanner demo={bundle.meta.demo} />
      <LoopTimeline history={bundle.loopHistory} generations={bundle.generations} />
    </>
  );
}
