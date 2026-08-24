package arena.gate;

import arena.bots.baseline.RandomBot;
import arena.bots.baseline.StraightBot;
import arena.bots.baseline.WallAvoidBot;
import arena.core.*;

import java.util.List;

/**
 * G5 — 결정론.
 *
 * 두 층으로 본다.
 *   ① 같은 국면을 두 번 물으면 같은 답이 나오는가
 *   ② 베이스라인 상대 경기를 두 번 돌리면 리플레이 해시가 같은가
 *
 * ①은 봇 내부의 비결정론을, ②는 경기 전체에 걸쳐 누적되는
 * 비결정론을 잡는다.
 *
 * {@code positions}는 이 관문 하나의 소유가 아니다 — G4·G5·G6가 같은
 * 리스트를 공유해서 재사용한다. G4와 달리 ①은 같은 국면을 "한 봇에게
 * 여러 번" 먹인다는 점이 추가 위험이다: 국면 하나를 {@link
 * PositionSampler#copyOf}로 한 번만 복사해 그 복사본을 반복해서 넘기면,
 * 봇이 첫 호출에서 그 복사본의 {@code wall}을 고쳐 쓴 경우 두 번째 호출은
 * 이미 훼손된 배열을 보게 된다 — 그 결과 서로 다른 답이 나와도 그건
 * 비결정론이 아니라 자기 오염이다. 그래서 매 호출마다(반복 호출 포함)
 * 원본에서 새로 복사한다.
 */
public final class DeterminismGate implements Gate {

    private static final int REPEATS = 3;

    private final List<GameView> positions;

    public DeterminismGate(List<GameView> positions) {
        this.positions = positions;
    }

    @Override
    public String id() { return "G5"; }

    @Override
    public GateResult check(GateContext ctx) {
        GateResult perCall = checkPerCall(ctx);
        if (!perCall.passed()) return perCall;

        return checkReplayHash(ctx);
    }

    private GateResult checkPerCall(GateContext ctx) {
        for (int i = 0; i < positions.size(); i++) {
            GameView original = positions.get(i);

            Direction first;
            try {
                // 호출마다 원본에서 새로 복사한다 — 복사본을 재사용하면
                // 첫 호출에서 생긴 훼손이 두 번째 호출의 "다른 답"으로
                // 둔갑해, 비결정론이 아닌 자기 오염을 비결정론으로 오판한다.
                first = ctx.bot().move(PositionSampler.copyOf(original));
            } catch (RuntimeException | StackOverflowError e) {
                return GateResult.fail(id(), "국면 " + i + "에서 "
                        + describeThrow(e) + "\n" + describe(original));
            }
            if (first == null) {
                return GateResult.fail(id(),
                        "국면 " + i + "에서 null을 반환했다\n" + describe(original));
            }

            for (int repeat = 0; repeat < REPEATS; repeat++) {
                Direction again;
                try {
                    again = ctx.bot().move(PositionSampler.copyOf(original));
                } catch (RuntimeException | StackOverflowError e) {
                    return GateResult.fail(id(), "국면 " + i + "에서 "
                            + describeThrow(e) + "\n" + describe(original));
                }
                if (again != first) {
                    return GateResult.fail(id(),
                            "국면 " + i + "에서 같은 입력에 다른 답을 냈다: "
                                    + first + " → " + again
                                    + "\n" + describe(original));
                }
            }
        }
        return GateResult.pass(id());
    }

    private GateResult checkReplayHash(GateContext ctx) {
        BotFunction subject = v -> ctx.bot().move(v);
        String[] names = { "StraightBot", "RandomBot", "WallAvoidBot" };
        BotFunction[] opponents = {
                v -> new StraightBot().move(v),
                v -> new RandomBot().move(v),
                v -> new WallAvoidBot().move(v),
        };

        for (long seed : ctx.judgingSeeds()) {
            for (int i = 0; i < opponents.length; i++) {
                Replay first;
                Replay second;
                try {
                    first = Match.play(ctx.bot().name(), subject, names[i], opponents[i],
                            seed, ctx.width(), ctx.height());
                    second = Match.play(ctx.bot().name(), subject, names[i], opponents[i],
                            seed, ctx.width(), ctx.height());
                } catch (RuntimeException | StackOverflowError e) {
                    return GateResult.fail(id(), "vs " + names[i] + " 시드 " + seed
                            + " 경기 중 " + describeThrow(e));
                }

                if (!first.hash().equals(second.hash())) {
                    return GateResult.fail(id(),
                            "vs " + names[i] + " 시드 " + seed + " 경기를 두 번 돌렸더니"
                                    + " 리플레이 해시가 달랐다\n  1회차 " + first.hash()
                                    + "\n  2회차 " + second.hash());
                }
            }
        }
        return GateResult.pass(id());
    }

    /** 에이전트가 그대로 재현할 수 있도록 반례 국면을 적는다. */
    private static String describe(GameView v) {
        return "  turn=" + v.turn()
                + " myHead=" + v.myHead() + " myDir=" + v.myDir()
                + " oppHead=" + v.oppHead() + " oppDir=" + v.oppDir();
    }

    private static String describeThrow(Throwable e) {
        return e.getClass().getSimpleName()
                + (e.getMessage() == null ? "" : ": " + e.getMessage());
    }
}
