package arena.bots.gen;

import arena.bots.Bot;
import arena.core.Direction;
import arena.core.GameView;

/**
 * 챔피언 계보의 첫 진짜 세대. Gen 0(직진)에서 한 걸음: 즉시 죽지 않는
 * 방향 중, "이번 턴 안에 확실히 내 것으로 셀 수 있는" 공간이 가장 넓은
 * 방향을 flood-fill로 골라 튼다.
 *
 * <p><b>왜 "확실히"인가 — 단순 flood-fill로는 부족했다.</b> 처음엔
 * {@code view.wall()} 스냅샷만으로 도달 칸 수를 세는 순수 flood-fill을
 * 썼다(우선순위만 24가지 순열로 바꿔가며 시험). 최선의 순열도 WallAvoidBot
 * 상대 100판 중 2판을 자기 벽에 부딪혀 졌다. 진 판 하나를 턴 단위로
 * 복기하니(그 국면 재현은 이 클래스 소스 히스토리·log.md D... 참고) 원인이
 * 드러났다: 어느 턴에 세 방향(DOWN·RIGHT·UP)의 도달 칸 수가 488로 완전히
 * 같았다. 그중 하나(DOWN)를 골랐는데, 그 칸이 넓은 공간과 이어지는
 * 유일한 통로가 하필 상대 머리가 "이번 턴에" 밟을 칸이었다 — 상대가
 * 정확히 그 칸으로 이동하며 그 통로를 벽으로 막아버렸다. 내 flood-fill은
 * 이번 턴 시작 시점의 벽만 보므로 이걸 볼 수 없었다(스펙 §2.1 — 어떤
 * 봇도 상대의 이번 턴 선택을 미리 볼 수 없다. 즉 이건 버그가 아니라
 * 1수 근사의 구조적 한계다).
 *
 * <p>고친 방법: 도달 칸을 셀 때 상대 머리가 이번 턴에 갈 수 있는 네
 * 이웃 칸(상대의 다음 위치 후보)을 "아직 내 것이 아닌 칸"으로 잠정
 * 취급한다 — 셀 때는 빼고, 그 칸을 거쳐야만 이어지는 공간도 함께
 * 뺀다. 상대가 실제로 그 방향으로 갈지는 모르지만(스펙 §2.1), 그 칸에
 * 기대는 것 자체가 이번 수의 안전 판단으로는 위험하다는 뜻이다. 이
 * 한 줄을 더하자 우선순위 순열을 다시 훑어 그 24가지 중 곧바로
 * WallAvoidBot·StraightBot·RandomBot 세 베이스라인 모두에게 심사
 * 시드(1‥50)·홀드아웃 시드(1001‥1050) 양쪽에서 패배 0회인 조합
 * ([LEFT, UP, DOWN, RIGHT])을 찾았다 — 순수 flood-fill로는 최선의
 * 순열도 2패였다는 점과 대조된다. 자세한 시행 기록은 log.md 참고.
 *
 * <p>여전히 1수 근사다 — 상대가 "그 다음" 무엇을 할지, 내가 그 다음
 * 무엇을 할지는 안 본다. 근시안 실수를 줄이는 건 이후 세대(전략
 * 사다리 5 — 2수 예측)의 몫이다.
 */
public final class Gen01Bot implements Bot {

    private static final Direction[] PREFERENCE = {
            Direction.LEFT, Direction.UP, Direction.DOWN, Direction.RIGHT
    };

    @Override
    public String name() { return "Gen01Bot"; }

    @Override
    public Direction move(GameView view) {
        Direction best = null;
        int bestReach = -1;

        for (Direction d : PREFERENCE) {
            if (view.isDeadly(d)) continue;

            int reach = reachableFrom(view, d);
            if (reach > bestReach) {
                bestReach = reach;
                best = d;
            }
        }

        // 사방이 막혔다 — 어차피 죽지만 유효한 방향은 내야 한다(G4).
        return best != null ? best : PREFERENCE[0];
    }

    /**
     * 방향 {@code d}로 한 걸음 디딘 뒤 flood-fill로 도달 가능한 빈칸 수를
     * 센다(디딘 칸 포함). 두 가지를 벽처럼 다룬다: ① 이번 턴 시작
     * 시점의 실제 벽({@code view.wall()}), ② 상대 머리가 이번 턴에
     * 이동할 수 있는 네 이웃 칸 — 그 칸을 상대가 실제로 밟으면 거기
     * 의존하던 공간은 다음 턴 도달 불가능해지므로, 지금 셀 때부터
     * 믿지 않는다(클래스 docs 참고). 다만 내가 이번 수에 실제로 딛는
     * 칸({@code startX, startY})은 이미 {@code isDeadly}로 안전이
     * 확인됐으므로, 그 칸이 상대의 다음 위치 후보와 우연히 겹치더라도
     * 실제 벽 상태 그대로 되돌려 놓아 BFS 시작점이 스스로를 막는
     * 모순을 피한다.
     *
     * <p>지역 배열만 쓰는 순수 함수라 인스턴스 필드 없이도(G2) 무상태를
     * 유지한다.
     */
    private static int reachableFrom(GameView view, Direction d) {
        int width = view.width();
        int height = view.height();
        int startX = view.myHead().x() + d.dx();
        int startY = view.myHead().y() + d.dy();

        boolean[][] wall = view.wall();
        boolean[][] risky = new boolean[height][width];
        for (int y = 0; y < height; y++) {
            risky[y] = wall[y].clone();
        }
        for (Direction od : PREFERENCE) {
            int ox = view.oppHead().x() + od.dx();
            int oy = view.oppHead().y() + od.dy();
            if (ox >= 0 && ox < width && oy >= 0 && oy < height) {
                risky[oy][ox] = true;
            }
        }
        if (startX >= 0 && startX < width && startY >= 0 && startY < height) {
            risky[startY][startX] = wall[startY][startX];
        }

        boolean[][] visited = new boolean[height][width];
        int[] stackX = new int[width * height];
        int[] stackY = new int[width * height];
        int sp = 0;

        stackX[sp] = startX;
        stackY[sp] = startY;
        sp++;
        visited[startY][startX] = true;
        int count = 0;

        while (sp > 0) {
            sp--;
            int x = stackX[sp];
            int y = stackY[sp];
            count++;

            for (Direction nd : PREFERENCE) {
                int nx = x + nd.dx();
                int ny = y + nd.dy();
                if (nx < 0 || nx >= width || ny < 0 || ny >= height) continue;
                if (visited[ny][nx] || risky[ny][nx]) continue;
                visited[ny][nx] = true;
                stackX[sp] = nx;
                stackY[sp] = ny;
                sp++;
            }
        }

        return count;
    }
}
