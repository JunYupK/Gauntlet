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
 * 현 챔피언과 100경기를 붙여 승격 여부를 판정한다.
 *
 * {@code botName}이 {@link BotRegistry}에 없으면 {@link GateCommand}와
 * 같은 이유로 여기서 붙잡아 종료 코드 2를 낸다 — 반려(코드 1)와는
 * 다른 실패다.
 */
public final class ChallengeCommand {

    private ChallengeCommand() {}

    public static int run(String botName) {
        Bot challenger;
        try {
            challenger = BotRegistry.byName(botName);
        } catch (IllegalArgumentException e) {
            System.out.println(e.getMessage());
            return 2;
        }

        Bot champion = BotRegistry.latestGeneration();

        if (challenger.name().equals(champion.name())) {
            System.out.println("도전자가 현 챔피언과 같다: " + botName);
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
