'use client';
import { useMemo } from 'react';
import { ArenaCanvas } from './ArenaCanvas';
import { decodeReplay } from '../lib/replay';
import type { Replay } from '../lib/schema';

/** 패널당 목표 캔버스 폭(논리 픽셀). 격자 칸 수는 리플레이가 정하므로
 * (30을 박지 않는다) 셀 크기는 이 예산을 격자 폭으로 나눠 구한다. */
const PANEL_WIDTH_BUDGET_PX = 220;

export function GalleryPanel({ replay, generation, turn }: {
  replay: Replay;
  /** 갤러리 배열에서의 위치 = 세대 번호(BundleBuilder.buildGallery가
   * generations 리스트를 순서대로 훑어 gallery.json을 쓰므로 인덱스와
   * 세대 번호가 같다). */
  generation: number;
  /** 모든 패널이 공유하는 턴 카운터. 패널마다 따로 세지 않는다 —
   * "같은 시점에 누가 살아있나"가 이 화면의 존재 이유다. */
  turn: number;
}) {
  const decoded = useMemo(() => decodeReplay(replay), [replay]);
  const cellSize = Math.max(1, Math.floor(PANEL_WIDTH_BUDGET_PX / decoded.width));

  // R1: 화면에 찍는 생존 턴 수는 replay.result.turns(엔진이 이미 판정한
  // 번들 원본)에서 읽는다. decoded.turnCount는 이 화면의 디코더가 moves를
  // 다시 읽어 얻은 값이고 conformance 테스트가 둘의 일치를 보증하긴
  // 하지만, 화면에 찍는 숫자 자체는 재계산이 아니라 번들 값이어야 한다.
  const finalTurn = replay.result.turns;
  const dead = turn >= finalTurn;
  const survived = Math.min(turn, finalTurn);
  // ArenaCanvas 인덱싱용 턴은 decoded 쪽 카운트로 클램프한다 — 디코더
  // 자신이 만든 turns 배열 밖을 read하지 않기 위해서다(둘은 같은 값이
  // 되도록 보증돼 있으므로 clamp 대상이 달라도 실질 차이는 없다).
  const canvasTurn = Math.min(turn, decoded.turnCount);

  return (
    // data-panel: Task 14 스모크가 "패널이 세대 수만큼 그려진다"를 셀
    // 때 쓰는 훅이다 — 클래스명이 아니라 이 속성으로 세는 이유는
    // Tailwind 클래스는 리팩터로 바뀔 수 있어도 이 속성은 "패널 하나"
    // 라는 의미 자체를 이름으로 갖기 때문이다.
    <div data-panel className="flex flex-col items-center gap-1 rounded-lg border border-neutral-800 bg-neutral-900 p-2">
      <div className="w-full truncate text-center text-sm font-medium text-neutral-200">
        세대 {generation} · {replay.bot0Id}
      </div>
      <ArenaCanvas decoded={decoded} turn={canvasTurn} cellSize={cellSize} dead={dead} />
      {/* data-turn-counter: 스모크가 재생 후 이 텍스트가 바뀌는지 본다
          (같은 훅 이유 — 텍스트 문구가 바뀌어도 속성은 남는다). */}
      <div data-turn-counter className="font-mono text-xs tabular-nums text-neutral-400">
        생존 {survived}턴{dead ? ' · 종료' : ''}
      </div>
    </div>
  );
}
