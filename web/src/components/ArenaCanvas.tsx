'use client';
import { useEffect, useRef } from 'react';
import { BOT_COLORS, GRID_BG, FLASH } from '../lib/colors';
import { trailAlpha, TRAIL } from '../lib/trail';
import type { DecodedMatch } from '../lib/replay';

/**
 * 턴 `t`(decoded.turns의 0-based 색인)에 확정된 칸들을 봇 색으로
 * 칠한다. `state.claimed[seat]`가 `null`이면 그 봇은 그 턴에 죽어서
 * 벽을 남기지 않았다는 뜻이므로 건너뛴다.
 */
function paintTurn(
  ctx: CanvasRenderingContext2D,
  decoded: DecodedMatch,
  t: number,
  cellSize: number,
  alpha: number,
) {
  const state = decoded.turns[t];
  if (!state) return;
  ctx.globalAlpha = alpha;
  for (let seat = 0; seat < 2; seat++) {
    const p = state.claimed[seat];
    if (!p) continue;
    ctx.fillStyle = BOT_COLORS[seat];
    ctx.fillRect(p.x * cellSize, p.y * cellSize, cellSize, cellSize);
  }
  ctx.globalAlpha = 1;
}

/**
 * 턴 `t`에 확정된 칸들을 `GRID_BG`로 불투명하게 덮는다. 트레일 창을
 * 다시 칠하기 전에 불러서 이전 프레임의 알파가 겹쳐 쌓이지 않게 한다.
 */
function clearTurn(ctx: CanvasRenderingContext2D, decoded: DecodedMatch, t: number, cellSize: number) {
  const state = decoded.turns[t];
  if (!state) return;
  ctx.fillStyle = GRID_BG;
  for (let seat = 0; seat < 2; seat++) {
    const p = state.claimed[seat];
    if (!p) continue;
    ctx.fillRect(p.x * cellSize, p.y * cellSize, cellSize, cellSize);
  }
}

/**
 * 시작 벽 4칸을 칠한다. 어느 칸이 시작 벽인지는 디코더가 이미 판정해
 * `decoded.startWalls`에 담아 뒀다 — "시작 칸과 그 뒤 칸" 규칙을
 * 여기서 다시 적으면 그것이 규칙의 여섯 번째 사본이 된다(다섯 번째는
 * `replay.ts` 자신).
 */
function paintStartWalls(ctx: CanvasRenderingContext2D, decoded: DecodedMatch, cellSize: number) {
  ctx.globalAlpha = trailAlpha(999, TRAIL); // 영구층과 같은, 가장 어두운 밝기
  for (const w of decoded.startWalls) {
    ctx.fillStyle = BOT_COLORS[w.seat];
    ctx.fillRect(w.point.x * cellSize, w.point.y * cellSize, cellSize, cellSize);
  }
  ctx.globalAlpha = 1;
}

export function ArenaCanvas({ decoded, turn, cellSize, dead }: {
  decoded: DecodedMatch; turn: number; cellSize: number; dead: boolean;
}) {
  const ref = useRef<HTMLCanvasElement>(null);
  // 마지막으로 그린 턴. 이것보다 앞으로 갔으면 그 사이 칸만 칠하고,
  // 뒤로 갔거나 리플레이가 바뀌었으면 처음부터 다시 그린다.
  const drawn = useRef(0);

  useEffect(() => {
    const canvas = ref.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const logicalWidth = decoded.width * cellSize;
    const logicalHeight = decoded.height * cellSize;

    const from = turn >= drawn.current ? drawn.current : 0;
    if (from === 0) {
      // devicePixelRatio 반영: 래스터 크기(canvas.width/height)에는
      // dpr를 곱하고, CSS 크기(style)는 논리 픽셀로 둔 뒤 컨텍스트를
      // dpr만큼 스케일한다. 안 하면 프로젝터에서 격자가 흐리게 나온다.
      // canvas.width/height를 대입하는 순간 캔버스는 지워지고 변환도
      // 항등으로 리셋되므로, 전체 다시 그리기(from === 0) 분기에서만
      // 건드린다 — 부분 갱신 분기는 이 스케일을 그대로 물려받는다.
      const dpr = window.devicePixelRatio || 1;
      canvas.width = logicalWidth * dpr;
      canvas.height = logicalHeight * dpr;
      canvas.style.width = `${logicalWidth}px`;
      canvas.style.height = `${logicalHeight}px`;
      ctx.scale(dpr, dpr);

      ctx.fillStyle = GRID_BG;
      ctx.fillRect(0, 0, logicalWidth, logicalHeight);
      paintStartWalls(ctx, decoded, cellSize);
    }

    // 층을 둘로 나눈다. 안 나누면 트레일 그라데이션과 누적 렌더링이
    // 양립하지 않는다: 칸을 확정할 때의 밝기로 한 번만 칠하면 모든 칸이
    // "가장 최근" 밝기로 굳어 트레일이 아예 안 보인다.
    //
    //  ① 영구층 — 확정된 벽을 가장 어두운 밝기로 한 번만 칠한다.
    //     트론은 벽이 영구적이라 여기는 지울 필요가 없다 (스펙 §9.3).
    //  ② 트레일 창 — 최근 TRAIL 턴만 매 프레임 다시 칠한다. 먼저
    //     배경색으로 덮어 이전 프레임의 알파가 겹쳐 쌓이지 않게 한다.
    //     매 프레임 손대는 칸은 봇당 TRAIL칸, 12패널을 합쳐도 수백 칸이다.
    for (let t = from; t < turn; t++) paintTurn(ctx, decoded, t, cellSize, trailAlpha(999, TRAIL));

    const windowStart = Math.max(0, turn - TRAIL);
    for (let t = windowStart; t < turn; t++) {
      clearTurn(ctx, decoded, t, cellSize);   // GRID_BG로 불투명하게 덮는다
      paintTurn(ctx, decoded, t, cellSize, trailAlpha(turn - 1 - t, TRAIL));
    }
    ctx.globalAlpha = 1;
    // 다음 프레임의 영구층은 트레일 창 앞까지만 새로 칠하면 된다.
    drawn.current = turn;

    // 사망 순간의 흰 플래시 한 프레임. 회색조는 여기서 칠하지 않는다 —
    // CSS filter로 걸어야 누적 렌더링 전제가 유지된다.
    if (dead && turn === decoded.turnCount) {
      ctx.fillStyle = FLASH;
      ctx.globalAlpha = 0.85;
      ctx.fillRect(0, 0, logicalWidth, logicalHeight);
      ctx.globalAlpha = 1;
      requestAnimationFrame(() => { drawn.current = 0; });
    }
  }, [decoded, turn, cellSize, dead]);

  return (
    <canvas
      ref={ref}
      // 격자 크기는 리플레이에서 읽는다. 30을 박지 않는다.
      width={decoded.width * cellSize}
      height={decoded.height * cellSize}
      style={{ filter: dead ? 'grayscale(1)' : 'none', transition: 'filter 400ms' }}
    />
  );
}
