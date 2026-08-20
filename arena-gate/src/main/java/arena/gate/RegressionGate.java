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

    /**
     * {@code ctx.bot().name()}을 그대로 시리즈 참가자 id로 쓰면, 제출된
     * 봇의 이름이 우연히(또는 악의적으로) 베이스라인 이름과 같을 때
     * {@code bot0Id == bot1Id}인 리플레이가 생긴다 — {@link Standing#of}가
     * 그런 리플레이에서는 좌석을 제대로 가려낼 수 없다(양쪽 다 subjectId와
     * 일치해 보이므로). 베이스라인 이름("StraightBot" 등)과 절대 겹치지
     * 않는 예약어를 대신 쓴다.
     */
    private static final String SUBJECT_ID = "subject";

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
                    SUBJECT_ID, subject, names[i], baselines[i],
                    ctx.judgingSeeds(), ctx.width(), ctx.height(), true);

            Standing standing = Standing.of(replays, SUBJECT_ID);

            if (standing.losses() > 0) {
                // 시드마다 정방향·교대 두 경기가 나오므로, 한 시드에서 둘 다
                // 지면 같은 시드가 목록에 두 번 들어갈 수 있다 — distinct()로
                // 최대 5개가 서로 다른 시드를 가리키게 한다.
                List<String> lostSeeds = replays.stream()
                        .filter(r -> !r.result().isDraw()
                                && r.result().winner() != Standing.seatOf(r, SUBJECT_ID))
                        .map(r -> String.valueOf(r.seed()))
                        .distinct()
                        .limit(5)
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
