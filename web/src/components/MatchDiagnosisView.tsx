'use client';
import { useCallback, useEffect, useMemo, useRef, useState, type MouseEvent } from 'react';
import { ArenaCanvas } from './ArenaCanvas';
import { PlaybackControls, type Speed } from './PlaybackControls';
import { decodeReplay } from '@/lib/replay';
import { lossSeries, worstFor, type LossPoint } from '@/lib/worst';
import { BOT_COLORS } from '@/lib/colors';
import type { Replay, MatchDiagnosis, MoveAnalysis } from '@/lib/schema';

/**
 * 화면 5 — 단일 경기 + 진단. "기계가 실수를 어떻게 짚었나"(C2, R2)를
 * 보여준다. `app/match/page.tsx`(서버 컴포넌트)가 `Replay[]`와
 * `MatchDiagnosis[]`를 그대로 넘기고, 여기서 `lossSeries`/`worstFor`
 * (순수 함수, `lib/worst.ts`)를 부른다 — 화면 1·2·4와 같은 서버/클라이언트
 * 분리 관례.
 *
 * 경기와 진단은 배열 위치가 아니라 `matchId`로 짝짓는다 — `curve.ts`의
 * baseline/latest가 세대 번호로 원소를 찾는 것과 같은 이유(배열 순서에
 * 기댄 구현은 입력이 뒤섞이면 조용히 틀린 진단을 붙인다). 실제로는
 * `BundleBuilder.buildDiagnosis`가 gallery를 그대로 순회해 만들어 순서가
 * 같지만, 그 보장에 기대지 않는다.
 */

const PANEL_WIDTH_BUDGET_PX = 520;
const MS_PER_TURN = 100;

// 이 그래프만의 강조색 — 봇 좌석색(청 #38bdf8 / 주황 #fb923c, `BOT_COLORS`)과는
// 다른 슬롯이어야 "봉우리·fatal 표시"와 "어느 좌석인가"가 색으로 섞이지
// 않는다. `LoopTimeline`(D82)·`DiffViewer`(D83)가 이미 검증해 쓰고 있는
// 상태색을 재사용한다 — 새 팔레트를 다시 검증할 필요가 없다.
const PEAK_COLOR = '#eda100';   // gold — LoopTimeline의 "승격" 강조와 같은 값
const FATAL_COLOR = '#d03b3b';  // critical red — LoopTimeline의 "반려", DiffViewer의 "삭제"와 같은 값
const GRID_LINE = '#2c2c2a';
const AXIS_TEXT = '#898781';

const CHART_WIDTH = 620;
const CHART_HEIGHT = 220;
const MARGIN = { top: 16, right: 16, bottom: 24, left: 44 };

function scaleX(turn: number, maxTurn: number, innerW: number): number {
  if (maxTurn <= 1) return MARGIN.left + innerW / 2;
  return MARGIN.left + ((turn - 1) / (maxTurn - 1)) * innerW;
}

function scaleY(loss: number, maxLoss: number, innerH: number): number {
  if (maxLoss <= 0) return MARGIN.top + innerH;
  return MARGIN.top + innerH - (loss / maxLoss) * innerH;
}

function pathFor(points: LossPoint[], maxTurn: number, maxLoss: number, innerW: number, innerH: number): string {
  return points
    .map((p, i) => `${i === 0 ? 'M' : 'L'}${scaleX(p.turn, maxTurn, innerW).toFixed(2)},${scaleY(p.loss, maxLoss, innerH).toFixed(2)}`)
    .join(' ');
}

/** 경기 선택 탭 라벨. 시드가 replay에 실려 있으므로 matchId를 다시 파싱하지 않는다. */
function matchLabel(r: Replay): string {
  return `${r.bot0Id} vs ${r.bot1Id} · seed ${r.seed}`;
}

function LossGraph({
  seriesA, seriesB, worstA, worstB, maxTurn, onJump,
}: {
  seriesA: LossPoint[]; seriesB: LossPoint[];
  worstA: MoveAnalysis[]; worstB: MoveAnalysis[];
  maxTurn: number;
  onJump: (turn: number) => void;
}) {
  const innerW = CHART_WIDTH - MARGIN.left - MARGIN.right;
  const innerH = CHART_HEIGHT - MARGIN.top - MARGIN.bottom;

  const maxLoss = Math.max(
    1,
    ...seriesA.map((p) => p.loss),
    ...seriesB.map((p) => p.loss),
  );
  const yTicks = [0, 0.25, 0.5, 0.75, 1].map((f) => Math.round(f * maxLoss));

  // 클릭한 x 위치에서 가장 가까운 턴으로 이동한다 — 봉우리 마커뿐 아니라
  // 그래프 어디를 눌러도 그 시점으로 넘어가게 해서 "클릭하면 그 턴으로
  // 이동한다"는 화면의 핵심 상호작용을 넓게 지원한다.
  const handleClick = (e: MouseEvent<SVGSVGElement>) => {
    const rect = e.currentTarget.getBoundingClientRect();
    const px = ((e.clientX - rect.left) / rect.width) * CHART_WIDTH;
    const frac = (px - MARGIN.left) / innerW;
    const turn = Math.round(1 + frac * (maxTurn - 1));
    onJump(Math.max(1, Math.min(maxTurn, turn)));
  };

  return (
    <svg
      viewBox={`0 0 ${CHART_WIDTH} ${CHART_HEIGHT}`}
      className="w-full cursor-pointer"
      role="img"
      aria-label="턴별 손실(loss) 그래프 — 봉우리를 클릭하면 그 턴으로 이동한다"
      onClick={handleClick}
    >
      {yTicks.map((t) => (
        <g key={t}>
          <line x1={MARGIN.left} x2={CHART_WIDTH - MARGIN.right} y1={scaleY(t, maxLoss, innerH)} y2={scaleY(t, maxLoss, innerH)} stroke={GRID_LINE} strokeWidth={1} />
          <text x={MARGIN.left - 6} y={scaleY(t, maxLoss, innerH)} textAnchor="end" dominantBaseline="middle" fontSize={10} fill={AXIS_TEXT}>{t}</text>
        </g>
      ))}
      <text x={MARGIN.left} y={CHART_HEIGHT - 6} fontSize={10} fill={AXIS_TEXT}>턴 1</text>
      <text x={CHART_WIDTH - MARGIN.right} y={CHART_HEIGHT - 6} textAnchor="end" fontSize={10} fill={AXIS_TEXT}>턴 {maxTurn}</text>

      <path d={pathFor(seriesA, maxTurn, maxLoss, innerW, innerH)} fill="none" stroke={BOT_COLORS[0]} strokeWidth={1.5} />
      <path d={pathFor(seriesB, maxTurn, maxLoss, innerW, innerH)} fill="none" stroke={BOT_COLORS[1]} strokeWidth={1.5} />

      {/* 가장 나쁜 수 마커 — worstFor가 이미 fatal을 loss보다 먼저
          정렬해 두므로(LossAnalyzer.worstMoves) 각 좌석의 사망 수는
          이 목록 안에 있다. fatal은 빨강 고리, 그 외 상위 손실은 금색
          고리로 구분한다(loss:0인 정면 충돌도 fatal 고리로 보인다). */}
      {[...worstA.map((m) => ({ m, color: BOT_COLORS[0] })), ...worstB.map((m) => ({ m, color: BOT_COLORS[1] }))].map(({ m, color }, i) => (
        <circle
          key={i}
          cx={scaleX(m.turn, maxTurn, innerW)}
          cy={scaleY(m.loss, maxLoss, innerH)}
          r={m.fatal ? 6 : 5}
          fill={color}
          stroke={m.fatal ? FATAL_COLOR : PEAK_COLOR}
          strokeWidth={2.5}
          onClick={(e) => { e.stopPropagation(); onJump(m.turn); }}
        >
          <title>{`턴 ${m.turn} · ${m.fatal ? 'fatal' : 'loss ' + m.loss}`}</title>
        </circle>
      ))}
    </svg>
  );
}

function WorstMoveRow({ move, seat, onJump }: { move: MoveAnalysis; seat: 0 | 1; onJump: (turn: number) => void }) {
  return (
    <button
      type="button"
      onClick={() => onJump(move.turn)}
      className="flex w-full items-center gap-2 rounded px-2 py-1 text-left text-xs hover:bg-neutral-800"
    >
      <span className="inline-block h-2.5 w-2.5 rounded-full shrink-0" style={{ backgroundColor: BOT_COLORS[seat] }} aria-hidden />
      <span className="font-mono tabular-nums text-neutral-400 w-14 shrink-0">턴 {move.turn}</span>
      <span className="text-neutral-300 shrink-0">
        {move.chose} <span className="text-neutral-600">(최선 {move.best})</span>
      </span>
      <span className="ml-auto font-mono tabular-nums text-neutral-100 shrink-0">loss {move.loss}</span>
      {move.fatal && (
        <span
          className="shrink-0 rounded-full px-1.5 py-0.5 text-[10px] font-bold"
          style={{ backgroundColor: 'rgba(208,59,59,0.18)', color: FATAL_COLOR }}
        >
          fatal
        </span>
      )}
    </button>
  );
}

export function MatchDiagnosisView({ gallery, diagnosis }: { gallery: Replay[]; diagnosis: MatchDiagnosis[] }) {
  const [matchId, setMatchId] = useState<string>(gallery[0]?.matchId ?? '');

  const replay = useMemo(() => gallery.find((r) => r.matchId === matchId) ?? gallery[0], [gallery, matchId]);
  const diag = useMemo(() => diagnosis.find((d) => d.matchId === replay?.matchId), [diagnosis, replay]);

  const decoded = useMemo(() => (replay ? decodeReplay(replay) : null), [replay]);
  const cellSize = decoded ? Math.max(1, Math.floor(PANEL_WIDTH_BUDGET_PX / decoded.width)) : 1;

  const [turn, setTurn] = useState(0);
  const [playing, setPlaying] = useState(false);
  const [speed, setSpeed] = useState<Speed>(1);

  const turnFloatRef = useRef(0);
  const lastTsRef = useRef<number | null>(null);
  const speedRef = useRef<Speed>(speed);
  speedRef.current = speed;
  const rafRef = useRef<number | null>(null);
  const finalTurn = replay?.result.turns ?? 0;

  // 경기를 바꾸면 재생 상태를 처음으로 되돌린다. 캔버스 자체는 아래
  // `key={replay.matchId}`로 다시 마운트되지만(ArenaCanvas의 `drawn` ref는
  // decoded prop 교체를 감지하지 못하므로 — 화면 1의 GalleryPanel과 같은
  // 이유), 이 컴포넌트가 들고 있는 turn 상태도 같이 리셋하지 않으면
  // 새 경기가 이전 경기의 마지막 턴에서 시작해 버린다.
  useEffect(() => {
    turnFloatRef.current = 0;
    lastTsRef.current = null;
    setTurn(0);
    setPlaying(false);
  }, [matchId]);

  useEffect(() => {
    if (!playing) { lastTsRef.current = null; return; }
    const tick = (ts: number) => {
      if (lastTsRef.current === null) lastTsRef.current = ts;
      const elapsedMs = ts - lastTsRef.current;
      lastTsRef.current = ts;
      turnFloatRef.current = Math.min(finalTurn, turnFloatRef.current + (elapsedMs / MS_PER_TURN) * speedRef.current);
      const next = Math.floor(turnFloatRef.current);
      setTurn((prev) => (prev !== next ? next : prev));
      if (turnFloatRef.current >= finalTurn) { setPlaying(false); return; }
      rafRef.current = requestAnimationFrame(tick);
    };
    rafRef.current = requestAnimationFrame(tick);
    return () => { if (rafRef.current !== null) cancelAnimationFrame(rafRef.current); rafRef.current = null; };
  }, [playing, finalTurn]);

  const handleReset = useCallback(() => {
    turnFloatRef.current = 0;
    lastTsRef.current = null;
    setTurn(0);
  }, []);

  // 봉우리·가장 나쁜 수 클릭 → 해당 턴으로 점프한다. 뒤로 가면
  // ArenaCanvas가 전체 다시 그리기로 처리하고(turn < drawn.current),
  // 앞으로 가면 증분 렌더로 처리한다 — 둘 다 별도 대응이 필요 없다.
  // 재생 중이었다면 멈춘다: 방금 가리킨 턴에서 화면이 바로 다시 흘러가
  // 버리면 "그 턴을 본다"는 클릭의 목적이 무색해진다.
  const handleJump = useCallback((t: number) => {
    turnFloatRef.current = t;
    lastTsRef.current = null;
    setTurn(t);
    setPlaying(false);
  }, []);

  if (!replay || !decoded || !diag) {
    return <p className="p-6 text-sm text-neutral-500">번들에 갤러리 경기가 없다.</p>;
  }

  const seriesA = lossSeries(diag, 0);
  const seriesB = lossSeries(diag, 1);
  const worstA = worstFor(diag, 0);
  const worstB = worstFor(diag, 1);

  const canvasTurn = Math.min(turn, decoded.turnCount);
  const dead = turn >= finalTurn;
  const survived = Math.min(turn, finalTurn);

  return (
    <main className="flex flex-col gap-6 p-6 max-w-6xl mx-auto">
      <header>
        <h1 className="text-xl font-bold">단일 경기 + 진단 — 기계가 실수를 어떻게 짚었나</h1>
        <p className="mt-1 text-sm text-neutral-400">
          경기를 재생하면서 옆의 턴별 loss 그래프를 본다. 봉우리(테두리
          금색)를 클릭하면 그 턴으로 이동한다 — 빨강 테두리는 그 수가
          실제로 경기를 끝낸(fatal) 수다. fatal은 loss가 0이어도(정면
          충돌) 별도로 표시된다.
        </p>
      </header>

      <section className="flex flex-wrap gap-1.5" role="tablist" aria-label="경기 선택">
        {gallery.map((r) => (
          <button
            key={r.matchId}
            type="button"
            role="tab"
            aria-selected={r.matchId === matchId}
            onClick={() => setMatchId(r.matchId)}
            className="rounded px-2.5 py-1.5 text-xs font-mono transition-colors"
            style={{
              backgroundColor: r.matchId === matchId ? '#2a2f38' : '#171a1f',
              color: r.matchId === matchId ? '#f2f4f7' : '#8b9099',
              border: r.matchId === matchId ? '1px solid #4a5160' : '1px solid #262a31',
            }}
          >
            {matchLabel(r)}
          </button>
        ))}
      </section>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-[auto_1fr]">
        <div className="flex flex-col items-center gap-3">
          <PlaybackControls
            playing={playing}
            speed={speed}
            onTogglePlay={() => setPlaying((p) => !p)}
            onSpeedChange={setSpeed}
            onReset={handleReset}
          />
          {/* 매치 정체성에 고정된 key: ArenaCanvas의 drawn ref는 decoded
              prop이 다른 경기로 바뀐 것을 스스로 감지하지 못한다(전체
              다시 그리기는 turn이 뒤로 갈 때만 일어난다) — key가 그대로면
              경기를 바꿔도 이전 경기의 픽셀 위에 새 경기를 덧칠하게
              된다. matchId를 key로 못박아 경기가 바뀌면 항상 새 캔버스로
              마운트되게 한다(화면 1 GalleryPanel과 같은 이유). */}
          <ArenaCanvas key={replay.matchId} decoded={decoded} turn={canvasTurn} cellSize={cellSize} dead={dead} />
          <div className="flex items-center gap-3 font-mono text-xs tabular-nums text-neutral-400">
            <span className="flex items-center gap-1"><span className="inline-block h-2.5 w-2.5 rounded-full" style={{ backgroundColor: BOT_COLORS[0] }} />{replay.bot0Id}</span>
            <span className="flex items-center gap-1"><span className="inline-block h-2.5 w-2.5 rounded-full" style={{ backgroundColor: BOT_COLORS[1] }} />{replay.bot1Id}</span>
            <span>생존 {survived}턴 / {finalTurn}{dead ? ' · 종료' : ''}</span>
          </div>
        </div>

        <div className="flex flex-col gap-4">
          <section className="rounded-lg border border-neutral-800 bg-neutral-900 p-4">
            <p className="mb-2 text-sm text-neutral-400">턴별 loss — 선택한 수의 도달 가능 칸이 최선 대비 얼마나 줄었나</p>
            <LossGraph seriesA={seriesA} seriesB={seriesB} worstA={worstA} worstB={worstB} maxTurn={finalTurn} onJump={handleJump} />
          </section>

          <section className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <div className="rounded-lg border border-neutral-800 bg-neutral-900 p-3">
              <p className="mb-1.5 text-xs font-semibold text-neutral-400">{replay.bot0Id} — 가장 나쁜 수</p>
              <div className="flex flex-col">
                {worstA.length === 0
                  ? <p className="px-2 py-1 text-xs text-neutral-600">기록 없음</p>
                  : worstA.map((m, i) => <WorstMoveRow key={i} move={m} seat={0} onJump={handleJump} />)}
              </div>
            </div>
            <div className="rounded-lg border border-neutral-800 bg-neutral-900 p-3">
              <p className="mb-1.5 text-xs font-semibold text-neutral-400">{replay.bot1Id} — 가장 나쁜 수</p>
              <div className="flex flex-col">
                {worstB.length === 0
                  ? <p className="px-2 py-1 text-xs text-neutral-600">기록 없음</p>
                  : worstB.map((m, i) => <WorstMoveRow key={i} move={m} seat={1} onJump={handleJump} />)}
              </div>
            </div>
          </section>
        </div>
      </div>
    </main>
  );
}
