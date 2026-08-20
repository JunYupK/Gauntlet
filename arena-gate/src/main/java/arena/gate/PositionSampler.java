package arena.gate;

import arena.bots.baseline.RandomBot;
import arena.bots.baseline.StraightBot;
import arena.bots.baseline.WallAvoidBot;
import arena.core.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 실제 대전을 재생하며 국면을 모은다.
 *
 * 무작위로 격자를 채워 만들지 않는 이유가 있다. 도달 불가능한 상태로
 * 봇을 시험하면 실제로는 일어나지 않을 실패를 잡아 루프를 헛돌게 한다.
 *
 * 표본은 G4·G5·G6가 공유해서 재사용한다 — 특히 G5는 같은 국면을 같은
 * 봇에게 두 번 먹여 비결정성을 잡는다. {@link GameView}는 wall 배열을
 * 방어적으로 복사하지 않고 내부 참조를 그대로 내주므로(엔진 안에서는
 * {@link Match}가 매 시야마다 새 스냅샷을 만들어 주어 안전했지만, 여기서는
 * 봇이 그 참조를 훼손할 수 있다), 위험이 두 군데다.
 *
 * (1) 생성 시점 — 한 턴에 나오는 두 시야(봇0·봇1)가 같은 스냅샷 배열을
 * 공유하면 안 된다. 이 클래스가 {@link #viewsOf}에서 시야마다 독립된
 * {@link Grid#wallSnapshot()}을 떠서 막는다.
 *
 * (2) 소비 시점 — {@code sample()}이 돌려주는 {@code List<GameView>}는
 * 한 번 만들어지면 G4·G5·G6와 그 안에서 심사되는 모든 봇이 반복
 * 재사용한다. 이 리스트를 직접 봇에게 넘기면, 한 봇이 자기가 받은
 * {@code wall}을 고쳐 쓰는 순간 그 {@code GameView}는 리스트 안에서
 * "영구히" 훼손되고, 이후 같은 리스트로 심사되는 다른 봇·다른 관문이
 * 그 오염된 국면을 본다. 이건 생성 시점 수정으로 막을 수 없다 — 리스트를
 * 소비하는 모든 지점이 각자 책임져야 한다. 그 책임을 한 곳에 모으려고
 * {@link #copyOf(GameView)}를 공개했다: 봇에게 국면을 먹이기 직전에
 * 반드시 이 메서드로 복사본을 만들어 넘겨야 한다. 복사는 값싸다(30×30
 * 기준 900바이트 안팎이고 그 호출 하나로 끝난다) — 표본 전체를 관문마다
 * 복제하는 것과는 비용이 다르다.
 */
public final class PositionSampler {

    private PositionSampler() {}

    public static List<GameView> sample(int count, int width, int height) {
        List<GameView> collected = new ArrayList<>(count);

        BotFunction[] pool = {
                v -> new WallAvoidBot().move(v),
                v -> new RandomBot().move(v),
                v -> new StraightBot().move(v),
        };

        outer:
        for (long seed = 1; seed <= 10_000; seed++) {
            for (BotFunction a : pool) {
                for (BotFunction b : pool) {
                    Replay replay = Match.play("a", a, "b", b, seed, width, height);
                    for (GameView v : viewsOf(replay)) {
                        collected.add(v);
                        if (collected.size() >= count) break outer;
                    }
                }
            }
        }

        if (collected.size() < count) {
            throw new IllegalStateException(
                    "시드 10,000개를 다 써도 국면 " + count + "개를 못 모았다 (모은 것 "
                            + collected.size() + "개)");
        }
        return collected;
    }

    /**
     * 표본 국면 하나를 봇에게 먹이기 직전에 뜨는 방어적 복사본.
     *
     * {@code wall}만 새 배열로 복제한다 — 나머지 필드(좌표·방향·턴)는
     * 불변 레코드/원시값이라 복제할 이유가 없다. 봇이 반환한 복사본의
     * {@code wall}을 고쳐 써도 원본 표본에는 닿지 않는다.
     *
     * 시간을 재는 소비자(G6의 수 하나당 p99 측정 등)를 위해 일부러
     * {@code move()} 호출과 분리된, 그 자체로 완결된 메서드로 뒀다 —
     * 호출자가 이 메서드를 타이머 밖에서 먼저 부르고, {@code move()} 호출만
     * 타이머 안에 두면 복사 비용이 측정치를 부풀리지 않는다.
     */
    public static GameView copyOf(GameView v) {
        boolean[][] wall = new boolean[v.wall().length][];
        for (int y = 0; y < wall.length; y++) {
            wall[y] = v.wall()[y].clone();
        }
        return new GameView(v.width(), v.height(), wall,
                v.myHead(), v.myDir(), v.oppHead(), v.oppDir(), v.turn());
    }

    /** 리플레이를 재생하며 매 턴 두 봇의 시야를 만든다. */
    private static List<GameView> viewsOf(Replay replay) {
        List<GameView> views = new ArrayList<>();

        Grid grid = new Grid(replay.width(), replay.height());
        Point[] head = { replay.start0(), replay.start1() };
        Direction[] dir = { replay.dir0(), replay.dir1() };
        grid.claim(head[0], 0);
        grid.claim(head[1], 1);

        for (int turn = 1; turn <= replay.result().turns(); turn++) {
            // 두 시야가 wall 배열을 공유하면, 한 봇이 자기 시야를 훼손했을 때
            // 같은 턴에 나온 상대편 시야까지 함께 오염된다. 시야마다 독립된
            // 스냅샷을 떠서 그 공유를 끊는다.
            for (int me = 0; me < 2; me++) {
                int opp = 1 - me;
                views.add(new GameView(
                        replay.width(), replay.height(), grid.wallSnapshot(),
                        head[me], dir[me], head[opp], dir[opp], turn));
            }

            Direction d0 = replay.moveAt(turn, 0);
            Direction d1 = replay.moveAt(turn, 1);
            Point p0 = head[0].move(d0);
            Point p1 = head[1].move(d1);

            boolean dead0 = !grid.inBounds(p0) || grid.isWall(p0) || p0.equals(p1);
            boolean dead1 = !grid.inBounds(p1) || grid.isWall(p1) || p1.equals(p0);
            if (dead0 || dead1) break;

            grid.claim(p0, 0);
            grid.claim(p1, 1);
            head[0] = p0; head[1] = p1;
            dir[0] = d0;  dir[1] = d1;
        }
        return views;
    }
}
