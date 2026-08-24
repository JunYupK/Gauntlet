// 데모 번들 — 실제 세대 루프가 만든 소스가 아니다.
// gen-01/attempt-2 (채택)
// depth=1의 벽회피봇 Demo01Bot
public final class Demo01Bot implements arena.bots.Bot {
    @Override public String name() { return "Demo01Bot"; }
    @Override public arena.core.Direction move(arena.core.GameView view) {
        // 안전한 방향 중 depth=1수 앞을 내다봐 가장 넓은 쪽을 고른다.
        throw new UnsupportedOperationException("데모 스텁 — 실행되지 않는다");
    }
}
