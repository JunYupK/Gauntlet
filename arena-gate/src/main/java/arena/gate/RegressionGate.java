package arena.gate;

import arena.bots.baseline.RandomBot;
import arena.bots.baseline.StraightBot;
import arena.bots.baseline.WallAvoidBot;
import arena.core.BotFunction;
import arena.core.Replay;
import arena.core.SeriesRunner;
import arena.core.Standing;

import java.util.List;

/**
 * G7 — 고정된 베이스라인 3종에게 한 번도 지지 않아야 한다.
 *
 * "전승"이 아니라 "패배 0회"인 이유가 있다. RandomBot 상대로는
 * 정면 충돌 무승부가 구조적으로 발생하므로, 전승을 요구하면 실력과
 * 무관하게 반려된다.
 *
 * 고정 상대라 변하지 않는 절대 좌표가 생긴다. 도전자가 운으로
 * 챔피언을 이기고 승격해 세대 공선이 뒤로 가는 사고를 막는다.
 */
public final class RegressionGate implements Gate {

    @Override
    public String id() { return "G7"; }

    @Override
    public GateResult check(GateContext ctx) {
        BotFunction subject = v -> ctx.bot().move(v);

        String[] names = { "StraightBot", "RandomBot", "WallAvoidBot" };
        BotFunction[] baselines = {
                v -> new StraightBot().move(v),
                v -> new RandomBot().move(v),
                v -> new WallAvoidBot().move(v),
        };

        for (int i = 0; i < baselines.length; i++) {
            List<Replay> replays = SeriesRunner.run(
                    ctx.bot().name(), subject, names[i], baselines[i],
                    ctx.judgingSeeds(), ctx.width(), ctx.height(), true);

            Standing standing = Standing.of(replays, ctx.bot().name());

            if (standing.losses() > 0) {
                List<String> lostSeeds = replays.stream()
                        .filter(r -> {
                            int mySeat = r.bot0Id().equals(ctx.bot().name()) ? 0 : 1;
                            return !r.result().isDraw() && r.result().winner() != mySeat;
                        })
                        .limit(5)
                        .map(r -> String.valueOf(r.seed()))
                        .toList();

                return GateResult.fail(id(),
                        names[i] + "에게 " + standing.losses() + "번 졌다 "
                                + "(승 " + standing.wins() + " 무 " + standing.draws() + ")"
                                + "\n  진 시드(최대 5개): " + lostSeeds);
            }
        }
        return GateResult.pass(id());
    }
}
