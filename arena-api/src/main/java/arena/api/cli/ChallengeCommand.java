package arena.api.cli;

import arena.api.Seeds;
import arena.bots.Bot;
import arena.bots.BotRegistry;
import arena.tournament.ChallengeReport;
import arena.tournament.Championship;
import arena.tournament.DiagnosisEntry;
import arena.tournament.RecordStore;

import java.nio.file.Path;

/**
 * 도전자보다 한 세대 낮은 챔피언과 100경기를 붙여 승격 여부를 판정한다.
 * 챔피언은 {@link BotRegistry#championFor(Bot)}로 고른다 — 등록된 세대 중
 * 도전자 세대 번호보다 낮은 최댓값이다. {@code latestGeneration()}(=가장
 * 높은 등록 세대)을 쓰지 않는 이유는, 도전자 {@code GenN}이 붙으려면 그
 * 자신이 등록돼 있어야 하고 그러면 {@code latestGeneration()}도 {@code GenN}이
 * 되어 도전자==챔피언이 되기 때문이다.
 *
 * {@code botName}이 {@link BotRegistry}에 없거나, {@code championFor}가
 * 도전자보다 낮은 세대를 찾지 못하면(도전자가 Gen 0) 여기서 붙잡아
 * 종료 코드 2를 낸다 — 둘 다 호출자 잘못이지 대전 반려(코드 1)가 아니다.
 */
public final class ChallengeCommand {

    private ChallengeCommand() {}

    public static int run(String botName) {
        Bot challenger;
        Bot champion;
        try {
            challenger = BotRegistry.byName(botName);
            champion = BotRegistry.championFor(challenger);
        } catch (IllegalArgumentException e) {
            System.out.println(e.getMessage());
            return 2;
        }

        ChallengeReport report = Championship.judge(
                challenger, champion, Seeds.JUDGING, Seeds.HOLDOUT, Seeds.WIDTH, Seeds.HEIGHT);

        System.out.printf("승점 승률 %.3f (기준 %.2f) — 승 %d 무 %d 패 %d%n",
                report.scoreRate(), report.threshold(),
                report.wins(), report.draws(), report.losses());

        if (report.promoted()) {
            System.out.printf("승격 — 홀드아웃 승률 %.3f (격차 %.3f)%n",
                    report.holdoutScoreRate(),
                    report.scoreRate() - report.holdoutScoreRate());
        } else {
            System.out.println("반려 — 손실이 가장 컸던 수:");
            for (DiagnosisEntry d : report.diagnosis()) {
                System.out.printf("  시드 %d 턴 %d: %s를 골랐다 (최선은 %s). "
                                + "닿을 수 있는 칸이 %d → %d로 %d칸 줄었다%n",
                        d.seed(), d.turn(), d.chose(), d.best(),
                        d.reachIfBest(), d.reachChosen(), d.loss());
            }
        }

        int generation = GateCommand.generationOf(botName);
        if (generation >= 0) {
            RecordStore store = new RecordStore(Path.of("records"));
            store.saveChallengeReport(generation, store.nextAttempt(generation), report);
        }
        return report.promoted() ? 0 : 1;
    }
}
