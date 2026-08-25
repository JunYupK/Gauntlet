// 데모 번들 — 실제 세대 루프가 만든 소스가 아니다.
// gen-00/attempt-1 (채택)
// 직진봇 StraightBot — 방향을 계속 유지하다가 첫 벽에서 죽는다. 내다보기·회피 없음.
public final class StraightBot implements arena.bots.Bot {
    @Override public String name() { return "StraightBot"; }
    @Override public arena.core.Direction move(arena.core.GameView view) {
        // 현재 방향을 그대로 유지한다 — 내다보지 않는다.
        throw new UnsupportedOperationException("데모 스텁 — 실행되지 않는다");
    }
}
