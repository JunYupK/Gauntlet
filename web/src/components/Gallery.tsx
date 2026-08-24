'use client';
import { useCallback, useEffect, useRef, useState } from 'react';
import { GalleryPanel } from './GalleryPanel';
import { PlaybackControls, type Speed } from './PlaybackControls';
import { panelGrid } from '../lib/layout';
import type { Replay } from '../lib/schema';

/** 배속 1×일 때 턴당 밀리초(초당 10턴). PlaybackControls의 배속 배율이
 * 이 기준값을 곱한다. */
const MS_PER_TURN = 100;

/**
 * 세대 갤러리의 클라이언트 오케스트레이터. `app/gallery/page.tsx`(서버
 * 컴포넌트)가 번들을 읽어 `Replay[]`만 여기 넘긴다 — 디코딩은
 * `GalleryPanel` 안에서 클라이언트가 한다.
 *
 * 턴 카운터는 여기 하나만 있다. `PlaybackControls`도 각 `GalleryPanel`도
 * 자기 시계를 돌리지 않는다 — 패널마다 따로 돌면 "같은 시점에 누가
 * 살아있나"라는 비교가 성립하지 않고, 그러면 이 화면이 R3의 증거가
 * 되지 못한다.
 */
export function Gallery({ gallery }: { gallery: Replay[] }) {
  const { cols, rows } = panelGrid(gallery.length);
  // 모든 패널이 끝나는 턴. 이보다 더 돌 이유가 없다 — 다 죽은 뒤에도
  // rAF를 계속 돌리는 건 낭비다.
  const maxTurnRef = useRef(Math.max(0, ...gallery.map((r) => r.result.turns)));

  const [turn, setTurn] = useState(0);
  // 자동재생하지 않는다: 발표자가 이 화면에 들어온 순간 곧바로 초 단위로
  // 흘러가 버리면 "재생" 버튼을 찾아 멈출 새도 없다 — Task 14의 스모크가
  // "재생" 버튼을 눌러 카운터가 올라가는지를 보므로(그 버튼은 정지
  // 상태에서만 "재생"이라는 라벨을 갖는다), 이 화면도 화면 5
  // (`MatchDiagnosisView`)와 같은 관례로 멈춘 채 시작한다.
  const [playing, setPlaying] = useState(false);
  const [speed, setSpeed] = useState<Speed>(1);

  // 경과 시간을 턴으로 바꾸는 누적값. 매 프레임 바뀌지만 렌더를 유발할
  // 필요가 없어(정수 턴만 화면에 영향을 준다) ref에 둔다.
  const turnFloatRef = useRef(0);
  const lastTsRef = useRef<number | null>(null);
  const speedRef = useRef<Speed>(speed);
  speedRef.current = speed;
  const rafRef = useRef<number | null>(null);

  useEffect(() => {
    if (!playing) {
      lastTsRef.current = null;
      return;
    }

    const tick = (ts: number) => {
      if (lastTsRef.current === null) lastTsRef.current = ts;
      const elapsedMs = ts - lastTsRef.current;
      lastTsRef.current = ts;

      const maxTurn = maxTurnRef.current;
      turnFloatRef.current = Math.min(
        maxTurn,
        turnFloatRef.current + (elapsedMs / MS_PER_TURN) * speedRef.current,
      );

      const next = Math.floor(turnFloatRef.current);
      setTurn((prev) => (prev !== next ? next : prev));

      if (turnFloatRef.current >= maxTurn) {
        setPlaying(false); // 모든 패널이 이미 죽었다 — 루프를 접는다
        return;
      }
      rafRef.current = requestAnimationFrame(tick);
    };

    rafRef.current = requestAnimationFrame(tick);
    return () => {
      if (rafRef.current !== null) cancelAnimationFrame(rafRef.current);
      rafRef.current = null;
    };
  }, [playing]);

  const handleReset = useCallback(() => {
    turnFloatRef.current = 0;
    lastTsRef.current = null;
    setTurn(0);
  }, []);

  return (
    <main className="flex flex-col gap-4 p-4">
      <PlaybackControls
        playing={playing}
        speed={speed}
        onTogglePlay={() => setPlaying((p) => !p)}
        onSpeedChange={setSpeed}
        onReset={handleReset}
      />
      <div
        className="grid gap-3"
        style={{ gridTemplateColumns: `repeat(${cols}, minmax(0, max-content))` }}
      >
        {gallery.map((replay, generation) => (
          // 매치 정체성에 고정된 key: 캔버스는 턴이 뒤로 갈 때만 전체
          // 다시 그리고 decoded prop이 바뀐 것 자체는 못 본다(ArenaCanvas의
          // drawn ref는 리플레이 교체를 감지하지 않는다) — 배열 인덱스를
          // 암묵적 key로 쓰면, 이 배열이 나중에 정렬/필터링되는 순간 같은
          // 자리의 캔버스 인스턴스가 재사용되며 이전 매치의 픽셀 위에
          // 새 매치를 덧칠하게 된다. matchId를 key로 못박아 매치가
          // 바뀌면 항상 새 캔버스로 마운트되게 한다.
          <GalleryPanel key={replay.matchId} replay={replay} generation={generation} turn={turn} />
        ))}
      </div>
      <p className="text-xs text-neutral-500">
        격자 {cols}×{rows} · {gallery.length}세대 · 배속 {speed}×
      </p>
    </main>
  );
}
