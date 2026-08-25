import { loadBundle } from '@/lib/bundle';
import { DataSourceBanner } from '@/components/DataSourceBanner';
import { Gallery } from '@/components/Gallery';

/**
 * 화면 1 — 세대 갤러리. 서버 컴포넌트로 번들을 읽어 `Replay[]`만
 * 클라이언트 컴포넌트(`Gallery`)에 넘긴다. 디코딩된 상태(턴 수백 개의
 * 격자)를 여기서 만들어 넘기지 않는다 — 그러면 이 페이지의 HTML이
 * 직렬화된 상태로 수 MB가 된다. `decodeReplay`는 클라이언트에서
 * `GalleryPanel`이 부른다.
 */
export default function GalleryPage() {
  const bundle = loadBundle();

  return (
    <>
      <DataSourceBanner demo={bundle.meta.demo} />
      <Gallery gallery={bundle.gallery} />
    </>
  );
}
