/**
 * 세대 갤러리 패널 배치. `count`개 패널을 cols × rows 격자에 놓는다.
 *
 * cols = ceil(sqrt(count)), rows = ceil(count / cols) — 가로를 먼저
 * 정사각형에 가깝게 잡고(sqrt를 올림), 그 가로 폭이 다 채우지 못하는
 * 나머지를 세로로 밀어 넣는다. 이렇게 하면 항상 cols >= rows가
 * 성립한다(cols >= sqrt(count) 이므로 count/cols <= cols, 그래서
 * rows = ceil(count/cols) <= cols) — 프로젝터는 가로가 넓으므로 이
 * 방향으로 정렬한다. 또한 rows가 "cols칸씩 채울 때 필요한 최소
 * 줄 수"이므로 빈 자리는 항상 cols칸 미만이다(정의상
 * (rows-1)*cols < count, 즉 rows*cols - count < cols).
 *
 * 스펙 §9.1의 두 예: 12세대 → 3행 4열, 16세대 → 4행 4열.
 */
export function panelGrid(count: number): { cols: number; rows: number } {
  if (!Number.isInteger(count) || count < 1) {
    throw new Error(`panelGrid: count는 1 이상의 정수여야 한다 (받은 값: ${count})`);
  }
  const cols = Math.ceil(Math.sqrt(count));
  const rows = Math.ceil(count / cols);
  return { cols, rows };
}
