export interface Screen {
  path: string;
  title: string;
  /** 이 화면이 증명하는 것 — 스펙 §9.2 표의 "증거" 칸. */
  evidence: string;
}

/**
 * 발표 순서 (스펙 §9.2) — **결과를 먼저 보여주고 과정을 나중에 밝힌다.**
 * 이 배열 하나가 홈(`app/page.tsx`, 서버 컴포넌트)의 목차와
 * `components/Deck.tsx`(클라이언트 컴포넌트)의 ←/→ 내비게이션 양쪽의
 * 유일한 출처다 — 순서를 두 곳에 따로 적으면 한쪽만 바뀌는 사고가
 * 난다. 바꾸지 않는다(계획의 불변식).
 *
 * 순수 데이터만 두는 별도 파일인 이유: `Deck.tsx`는 `'use client'`라
 * RSC 경계에서 그 파일의 모든 export가 "클라이언트 참조"로 바뀐다 —
 * 컴포넌트가 아니라 평범한 배열이어도 마찬가지라, 서버 컴포넌트인
 * `page.tsx`가 그 배열을 직접 import해 `.map`을 부르면 배열이 아니라
 * 참조 객체를 받아 즉시 죽는다(빌드 타임 프리렌더에서 `TypeError:
 * SCREENS.map is not a function`으로 실측). 데이터는 클라이언트
 * 경계가 없는 이 파일에 두고, 서버·클라이언트 양쪽이 여기서 가져온다.
 */
export const SCREENS: Screen[] = [
  { path: '/gallery', title: '세대 갤러리', evidence: 'R3' },
  { path: '/curve', title: '개선 곡선', evidence: 'R3, R2' },
  { path: '/loop', title: '루프 타임라인', evidence: 'C2' },
  { path: '/diff', title: '세대별 코드 diff', evidence: 'C1' },
  { path: '/match', title: '단일 경기 + 진단', evidence: 'C2, R2' },
  { path: '/heatmap', title: '히트맵 · 과적합 격차', evidence: '부록' },
];
