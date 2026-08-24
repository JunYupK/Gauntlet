import { loadBundle } from '@/lib/bundle';
import { DataSourceBanner } from '@/components/DataSourceBanner';

// 화면들은 Task 8부터 붙는다. 지금은 번들이 실제로 빌드 타임에
// 스키마를 통과해서 읽혔다는 것 자체를 보여주는 요약만 찍는다.
export default function Home() {
  const bundle = loadBundle();

  return (
    <>
      <DataSourceBanner demo={bundle.meta.demo} />
      <main className="max-w-2xl mx-auto p-8">
        <h1 className="text-2xl font-bold mb-6">Arena 번들 요약</h1>
        <dl className="grid grid-cols-2 gap-x-4 gap-y-2 text-lg">
          <dt className="text-neutral-400">세대 수</dt>
          <dd>{bundle.meta.generations}</dd>

          <dt className="text-neutral-400">데모 번들 여부</dt>
          <dd>{bundle.meta.demo ? '예' : '아니오'}</dd>

          <dt className="text-neutral-400">갤러리 경기 수</dt>
          <dd>{bundle.gallery.length}</dd>
        </dl>
      </main>
    </>
  );
}
