package arena.core;

/**
 * 봇이 볼 수 있는 전부. 봇은 이것 말고는 세상에 접근할 수단이 없다.
 *
 * wall은 이동 전 벽 집합 W(t)다. 히스토리는 제공하지 않는다 —
 * 봇은 무상태 순수 함수다.
 *
 * <p><b>이 레코드는 wall을 복사하지 않는다.</b> 생성자에서도, 접근자
 * {@code wall()}에서도 복사하지 않고 넘겨받은 배열 참조를 그대로 들고
 * 있다가 그대로 내준다 — 즉 봇이 {@code v.wall()[y][x] = true}로 자기가
 * 받은 국면을 훼손할 수 있다. 방어는 <b>만드는 쪽</b>의 책임이다:
 * 엔진({@code Match.viewFor})은 시야마다 {@link Grid#wallSnapshot()}으로
 * 새 스냅샷을 떠서 넘기므로 봇이 무엇을 하든 엔진 상태가 안전하고,
 * 표본을 재사용하는 {@code arena-gate}의 {@code PositionSampler}는
 * 같은 이유로 {@code copyOf}를 따로 두어 봇에게 먹이기 직전마다
 * 복사본을 만든다.
 *
 * 한때 이 자리에 "방어적 복사본이다"라고 적혀 있었는데 거짓이었다.
 * 그 문장을 믿고 표본 리스트의 {@code GameView}를 봇에게 그대로 넘기면,
 * 한 봇의 낙서가 그 국면에 영구히 남아 이후 심사되는 다른 봇들이 오염된
 * 국면을 본다 — 레코드가 막아줄 것처럼 읽히는 문장이 정확히 그 사고를
 * 부른다.
 */
public record GameView(
        int width,
        int height,
        boolean[][] wall,
        Point myHead,
        Direction myDir,
        Point oppHead,
        Direction oppDir,
        int turn
) {
    public boolean isWall(int x, int y) {
        return wall[y][x];
    }

    public boolean inBounds(int x, int y) {
        return x >= 0 && x < width && y >= 0 && y < height;
    }

    /** 그 방향으로 한 칸 가면 즉시 죽는가. 봇이 가장 자주 묻는 질문이다. */
    public boolean isDeadly(Direction d) {
        Point p = myHead.move(d);
        return !inBounds(p.x(), p.y()) || wall[p.y()][p.x()];
    }
}
