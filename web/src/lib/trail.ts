/**
 * 트레일 밝기 곡선. 스펙 §9.3: "최근 지나온 칸일수록 밝게. 정지 화면
 * 에서도 진행 방향이 읽힌다." head(age=0)는 완전 불투명(1.0)에서
 * 시작해 `maxAge`칸을 지나는 동안 바닥(FLOOR)까지 선형으로 내려가고,
 * 그 밖(age >= maxAge)은 FLOOR에서 멈춘다 — 벽은 영구적이라(스펙
 * §9.3) 아무리 오래돼도 완전히 안 보이면 안 된다.
 *
 * `ArenaCanvas`의 영구층(확정된 벽을 한 번만 칠하는 층)은
 * `trailAlpha(999, TRAIL)`로 이 FLOOR 값을 얻어 쓴다.
 */
export const TRAIL = 20;

/**
 * 가장 오래된 칸도 이 밝기 아래로는 내려가지 않는다.
 * `trailAlpha(999, 20) > 0.15` 테스트가 하한을 못박으므로 0.15보다
 * 여유 있게 잡는다.
 */
const FLOOR = 0.2;

export function trailAlpha(age: number, maxAge: number): number {
  if (age >= maxAge) return FLOOR;
  const t = age / maxAge; // 0(머리) .. 1 직전(창의 끝)
  return 1 - t * (1 - FLOOR);
}
