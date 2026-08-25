/**
 * 이것은 `Match`(arena-core, Java)의 보드 재구성 규칙을 타입스크립트로
 * 옮긴 사본이다. `replay.conformance.test.ts`가 그 일치를 매 빌드마다
 * 검사한다 — 모든 리플레이가 엔진이 판정한 winner·turns·reason을 이미
 * 들고 있으므로, 이 디코더가 moves만 보고 독립적으로 같은 셋을 내놓는지
 * 대조하면 골든 파일도 자바 호출도 없이 규칙이 일치한다는 걸 증명할 수
 * 있다.
 *
 * 규칙을 요약하거나 최적화하지 않는다. 이 파일의 목적은 빠른 것이
 * 아니라 엔진과 같은 것이다.
 */
import type { Replay, Direction } from './schema';

export interface Point {
  x: number;
  y: number;
}

export type DeathReason =
  | 'P0_OUT_OF_BOUNDS' | 'P0_HIT_OWN_WALL' | 'P0_HIT_OPPONENT_WALL'
  | 'P1_OUT_OF_BOUNDS' | 'P1_HIT_OWN_WALL' | 'P1_HIT_OPPONENT_WALL'
  | 'HEAD_ON_COLLISION' | 'BOTH_DIED' | 'MAX_TURNS';

export interface TurnState {
  turn: number;
  heads: [Point, Point];
  dirs: [Direction, Direction];
  claimed: (Point | null)[];
  alive: [boolean, boolean];
}

export interface DecodedMatch {
  width: number;
  height: number;
  turns: TurnState[];
  winner: number;
  turnCount: number;
  reason: DeathReason;
  startWalls: { point: Point; seat: 0 | 1 }[];
  /** @internal owner()가 조회하는 누적 격자. -1 = 빈 칸. */
  _grids: Int8Array[];
}

function opposite(d: Direction): Direction {
  switch (d) {
    case 'UP': return 'DOWN';
    case 'DOWN': return 'UP';
    case 'LEFT': return 'RIGHT';
    case 'RIGHT': return 'LEFT';
  }
}

function step(p: Point, d: Direction): Point {
  switch (d) {
    case 'UP': return { x: p.x, y: p.y - 1 };
    case 'DOWN': return { x: p.x, y: p.y + 1 };
    case 'LEFT': return { x: p.x - 1, y: p.y };
    case 'RIGHT': return { x: p.x + 1, y: p.y };
  }
}

function inBounds(p: Point, width: number, height: number): boolean {
  return p.x >= 0 && p.x < width && p.y >= 0 && p.y < height;
}

function idx(width: number, p: Point): number {
  return p.y * width + p.x;
}

/**
 * moves 문자열을 턴별 격자 상태로 되돌린다. `Match.playInternal`·
 * `initialGrid`·`resolve`를 그대로 옮긴 7단계.
 */
export function decodeReplay(replay: Replay): DecodedMatch {
  const { width, height, start0, dir0, start1, dir1, moves } = replay;

  // --- 1. 시작 격자는 4칸이다: start0, start1, 그리고 각자의 바로 뒤 칸. ---
  const grid = new Int8Array(width * height).fill(-1);
  const behind0 = step(start0, opposite(dir0));
  const behind1 = step(start1, opposite(dir1));

  const startWalls: { point: Point; seat: 0 | 1 }[] = [
    { point: start0, seat: 0 },
    { point: behind0, seat: 0 },
    { point: start1, seat: 1 },
    { point: behind1, seat: 1 },
  ];
  for (const w of startWalls) {
    if (inBounds(w.point, width, height)) {
      grid[idx(width, w.point)] = w.seat;
    }
  }

  // grids[i]는 "i턴이 지난 뒤"의 누적 상태다 — grids[0]은 게임 시작 전
  // (= W(1), 턴 1을 판정할 때 쓰는 벽 집합), grids[k]는 턴 k까지 처리한
  // 뒤의 상태(= W(k+1), 턴 k+1을 판정할 때 쓰는 벽 집합이자 동시에 "턴
  // k가 끝난 직후의 최종 보드"). owner()는 이 색인을 그대로 쓴다.
  const grids: Int8Array[] = [grid.slice()];
  const turns: TurnState[] = [];

  let head0: Point = start0;
  let head1: Point = start1;
  let curDir0: Direction = dir0;
  let curDir1: Direction = dir1;

  let winner = -1;
  let reason: DeathReason = 'MAX_TURNS';
  let turnCount = 0;

  const totalTurns = moves.length / 2;

  for (let t = 1; t <= totalTurns; t++) {
    // --- 2. 턴 t의 방향. ---
    const d0 = charToDirection(moves[(t - 1) * 2]);
    const d1 = charToDirection(moves[(t - 1) * 2 + 1]);

    // --- 3. 목표 칸. ---
    const p0 = step(head0, d0);
    const p1 = step(head1, d1);

    // --- 4. 같은 W(t)로 동시에 판정한다 — 벽 확정보다 먼저다. ---
    const p0OffGrid = !inBounds(p0, width, height);
    const p1OffGrid = !inBounds(p1, width, height);
    const p0IsWall = !p0OffGrid && grid[idx(width, p0)] !== -1;
    const p1IsWall = !p1OffGrid && grid[idx(width, p1)] !== -1;
    const samePoint = p0.x === p1.x && p0.y === p1.y;

    const dead0 = p0OffGrid || p0IsWall || samePoint;
    const dead1 = p1OffGrid || p1IsWall || samePoint;

    // --- 5. 살아남은 쪽만 벽을 확정한다. ---
    const claimed: (Point | null)[] = [null, null];
    if (!dead0) {
      grid[idx(width, p0)] = 0;
      claimed[0] = p0;
    }
    if (!dead1) {
      grid[idx(width, p1)] = 1;
      claimed[1] = p1;
    }

    const alive: [boolean, boolean] = [!dead0, !dead1];
    turnCount = t;

    // 턴 t 처리 직후의 누적 격자를 저장한다 — grids[t]가 된다
    // (= W(t+1)이자, 이 매치가 턴 t에서 끝났다면 최종 보드이기도 하다).
    grids.push(grid.slice());

    turns.push({
      turn: t,
      heads: [head0, head1],
      dirs: [d0, d1],
      claimed,
      alive,
    });

    // --- 6. 둘 중 하나라도 죽었으면 경기가 끝난다. 사유는 5번 확정 후 격자에서. ---
    if (dead0 || dead1) {
      if (dead0 && dead1) {
        winner = -1;
        reason = samePoint ? 'HEAD_ON_COLLISION' : 'BOTH_DIED';
      } else if (dead0) {
        winner = 1;
        reason = p0OffGrid ? 'P0_OUT_OF_BOUNDS'
          : ownerAt(grid, width, p0) === 0 ? 'P0_HIT_OWN_WALL'
          : 'P0_HIT_OPPONENT_WALL';
      } else {
        winner = 0;
        reason = p1OffGrid ? 'P1_OUT_OF_BOUNDS'
          : ownerAt(grid, width, p1) === 1 ? 'P1_HIT_OWN_WALL'
          : 'P1_HIT_OPPONENT_WALL';
      }
      break;
    }

    // --- 7. 안 끝났으면 갱신하고 다음 턴. ---
    head0 = p0;
    head1 = p1;
    curDir0 = d0;
    curDir1 = d1;
  }

  // 경기가 moves를 다 쓰고도 안 끝났다면 (이론상 MAX_TURNS) 남는 자리는
  // 없다 — moves 길이는 항상 result.turns * 2 (schema.ts의 refine).
  void curDir0;
  void curDir1;

  return {
    width,
    height,
    turns,
    winner,
    turnCount,
    reason,
    startWalls,
    _grids: grids,
  };
}

function ownerAt(grid: Int8Array, width: number, p: Point): number {
  return grid[idx(width, p)];
}

function charToDirection(c: string): Direction {
  switch (c) {
    case 'U': return 'UP';
    case 'D': return 'DOWN';
    case 'L': return 'LEFT';
    case 'R': return 'RIGHT';
    default: throw new Error(`알 수 없는 방향 문자: ${c}`);
  }
}

/**
 * 화면이 칸 주인을 묻는 용도. 누적 격자는 TurnState가 아니라
 * DecodedMatch가 들고 있으므로 턴 번호를 함께 받는다.
 *
 * `owner(decoded, t, x, y)`는 **W(t)** — 턴 t를 판정할 때 실제로 쓰인
 * 벽 집합 — 을 조회한다. 즉 턴 t 자신의 확정(claim)은 아직 반영되지
 * 않은, 턴 t 시작 시점의 상태다. `owner(decoded, 1, ...)`이 시작 격자
 * 4칸만 보여주는 이유가 이것이다(턴 1이 아직 확정을 안 낸 시점).
 * 마지막 턴이 끝난 뒤의 최종 보드가 필요하면 `turn = decoded.turnCount + 1`을
 * 준다 — `_grids`는 게임 시작 전(index 0)부터 마지막 턴 처리 직후까지
 * `turnCount + 1`개의 스냅샷을 담고 있다.
 */
export function owner(decoded: DecodedMatch, turn: number, x: number, y: number): 0 | 1 | null {
  if (turn < 1 || turn > decoded._grids.length) {
    throw new Error(`턴 범위 밖: ${turn} (경기는 ${decoded._grids.length - 1}턴, 최종 보드는 turn=${decoded._grids.length})`);
  }
  const grid = decoded._grids[turn - 1];
  const v = grid[y * decoded.width + x];
  return v === -1 ? null : (v as 0 | 1);
}
