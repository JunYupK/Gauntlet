export const SPEEDS = [0.5, 1, 2, 4] as const;
export type Speed = (typeof SPEEDS)[number];

/**
 * 재생/정지·배속·처음으로. 턴을 직접 세지 않는다 — 턴 계산은
 * `Gallery`가 하나의 requestAnimationFrame 루프에서 맡고, 이 컴포넌트는
 * 그 상태를 보여주고 사용자 조작을 콜백으로 올려보내기만 한다.
 */
export function PlaybackControls({ playing, speed, onTogglePlay, onSpeedChange, onReset }: {
  playing: boolean;
  speed: Speed;
  onTogglePlay: () => void;
  onSpeedChange: (speed: Speed) => void;
  onReset: () => void;
}) {
  return (
    <div className="flex items-center gap-3 rounded-lg border border-neutral-800 bg-neutral-900 px-4 py-2">
      <button
        type="button"
        onClick={onTogglePlay}
        className="rounded bg-neutral-700 px-3 py-1 text-sm font-medium text-neutral-100 hover:bg-neutral-600"
      >
        {playing ? '정지' : '재생'}
      </button>

      <button
        type="button"
        onClick={onReset}
        className="rounded bg-neutral-700 px-3 py-1 text-sm font-medium text-neutral-100 hover:bg-neutral-600"
      >
        처음으로
      </button>

      <div className="flex items-center gap-1">
        {SPEEDS.map((s) => (
          <button
            key={s}
            type="button"
            onClick={() => onSpeedChange(s)}
            aria-pressed={speed === s}
            className={`rounded px-2 py-1 text-sm font-mono ${
              speed === s
                ? 'bg-sky-500 text-black'
                : 'bg-neutral-800 text-neutral-300 hover:bg-neutral-700'
            }`}
          >
            {s}×
          </button>
        ))}
      </div>
    </div>
  );
}
