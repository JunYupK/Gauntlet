'use client';
import { useCallback, useEffect } from 'react';
import { usePathname, useRouter } from 'next/navigation';
import { SCREENS } from '@/lib/screens';

function isTypingTarget(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) return false;
  const tag = target.tagName;
  return tag === 'INPUT' || tag === 'TEXTAREA' || target.isContentEditable;
}

/**
 * Space로 재생/정지를 토글한다. 재생 상태는 화면마다(`Gallery`,
 * `MatchDiagnosisView`) 따로 들고 있어 Deck은 그 상태를 모른다 — 대신
 * 두 화면이 이미 똑같은 라벨("재생"/"정지")로 내놓는 `PlaybackControls`
 * 버튼을 찾아 클릭한다. 발표자가 화면을 옮겨 다닐 때마다 마우스로 그
 * 버튼을 다시 찾지 않아도 되게 하는 것이 R4(환경 의존·조작 실수 최소화)
 * 의 요구다. 버튼이 없는 화면(목차, 루프 타임라인 등)에서는 아무 일도
 * 하지 않는다.
 */
function togglePlaybackButton(): boolean {
  const buttons = Array.from(document.querySelectorAll('button'));
  const target = buttons.find((b) => b.textContent === '재생' || b.textContent === '정지');
  if (!target) return false;
  target.click();
  return true;
}

/**
 * 발표 셸의 전역 내비게이션. `app/layout.tsx`에 한 번 마운트되어 모든
 * 화면에서 산다 — ←/→가 어느 화면에서 눌러도 다음/이전 화면으로 넘어가야
 * 하기 때문이다. 목차(`/`)는 이 순서 밖의 시작점이라 index -1로 다룬다.
 */
export function Deck() {
  const pathname = usePathname();
  const router = useRouter();
  const index = SCREENS.findIndex((s) => s.path === pathname);

  const goTo = useCallback(
    (next: number) => {
      if (next < -1 || next >= SCREENS.length) return;
      router.push(next === -1 ? '/' : SCREENS[next].path);
    },
    [router],
  );

  useEffect(() => {
    const onKeyDown = (e: KeyboardEvent) => {
      if (isTypingTarget(e.target)) return;
      if (e.key === 'ArrowRight') {
        e.preventDefault();
        goTo(index + 1);
      } else if (e.key === 'ArrowLeft') {
        e.preventDefault();
        goTo(index - 1);
      } else if (e.key === ' ' || e.code === 'Space') {
        if (togglePlaybackButton()) e.preventDefault();
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [index, goTo]);

  return (
    <nav
      aria-label="발표 내비게이션"
      className="fixed inset-x-0 bottom-0 z-50 flex items-center justify-between gap-3 border-t border-neutral-800 bg-neutral-950/95 px-4 py-2 text-sm backdrop-blur"
    >
      <button
        type="button"
        onClick={() => goTo(index - 1)}
        disabled={index <= -1}
        aria-label="이전 화면"
        className="rounded px-2 py-1 font-mono text-neutral-300 hover:bg-neutral-800 disabled:opacity-30"
      >
        ← 이전
      </button>

      <div className="flex min-w-0 items-center gap-2">
        <span className="shrink-0 font-mono text-neutral-500">
          {index === -1 ? '목차' : `${index + 1} / ${SCREENS.length}`}
        </span>
        <span className="truncate text-neutral-200">
          {index === -1 ? '발표 시작점' : SCREENS[index].title}
        </span>
      </div>

      <button
        type="button"
        onClick={() => goTo(index + 1)}
        disabled={index >= SCREENS.length - 1}
        aria-label="다음 화면"
        className="rounded px-2 py-1 font-mono text-neutral-300 hover:bg-neutral-800 disabled:opacity-30"
      >
        다음 →
      </button>
    </nav>
  );
}
