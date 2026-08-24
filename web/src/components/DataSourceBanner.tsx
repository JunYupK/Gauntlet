/**
 * meta.demo가 참일 때만 화면 맨 위에 띠를 그린다. 발표장에서 데모
 * 번들이 실수로 열려 있으면 이 띠가 즉시 눈에 띄어야 하므로 색과
 * 크기를 아끼지 않는다 — 조용히 작은 글씨로 표시하면 놓치기 쉽다.
 */
export function DataSourceBanner({ demo }: { demo: boolean }) {
  if (!demo) return null;

  return (
    <div className="w-full bg-orange-500 text-black py-3 px-4 text-center font-bold text-lg tracking-wide border-b-4 border-orange-700">
      ⚠ 데모 번들 — 세대 루프가 만든 기록이 아니다 ⚠
    </div>
  );
}
