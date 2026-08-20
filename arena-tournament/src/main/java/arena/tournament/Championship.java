package arena.tournament;

import arena.bots.Bot;
import arena.core.Replay;
import arena.core.SeriesRunner;
import arena.core.Standing;
import arena.diagnostics.LossAnalyzer;
import arena.diagnostics.MoveAnalysis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 도전자가 챔피언을 교체할 자격이 있는지 판정한다.
 *
 * 봇이 무상태 결정론이고 시작 위치가 시드로 고정되므로 승률은 확률이
 * 아니라 하나의 고정된 숫자다. 표본 크기나 우연에 대한 통계 논의가
 * 필요 없고 임계값만 있으면 된다.
 *
 * 도전자·챔피언은 둘 다 제출된 봇이고, 봇은 자신의 {@code name()}을
 * 통제한다(arena-gate가 G2에서 시행하는 무상태 제약과 별개로, name()
 * 문자열 자체는 봇 작성자가 자유롭게 고를 수 있다). 두 이름이 우연히든
 * 악의적으로든 같으면 {@link Standing#seatOf}가 어느 좌석에 앉았는지
 * 더는 구분할 수 없어 예외를 던진다(arena-core 쪽 결정). 그래서 이
 * 클래스는 {@code bot.name()}을 좌석 식별에 절대 쓰지 않는다 — 대신
 * 서로 다른 게 보장된 내부 예약 id({@link #CHALLENGER_ID},
 * {@link #CHAMPION_ID})를 {@link SeriesRunner}·{@link Standing}에
 * 넘긴다. 이름이 같은 두 봇이 붙어도, 심지어 같은 인스턴스가 두 자리에
 * 다 들어와도 판정 자체는 정상적으로 끝난다 — 제출된 봇이 이름 하나로
 * 하네스를 무너뜨릴 수 없어야 한다.
 */
public final class Championship {

    /** 승점 승률 기준. 루프가 못 넘는다고 해서 낮추지 않는다 (BRIEF §11-4). */
    public static final double PROMOTION_THRESHOLD = 0.60;

    /** 반려 리포트에 담을 치명적인 수의 개수. */
    private static final int DIAGNOSIS_LIMIT = 3;

    /**
     * 좌석 식별용 내부 예약 id. 도전자·챔피언의 실제 {@code name()}과는
     * 무관하며, 둘 사이·baseline 이름과의 충돌 가능성을 원천 차단하려고
     * 서로 다른 상수로 고정했다.
     */
    private static final String CHALLENGER_ID = "__challenger__";
    private static final String CHAMPION_ID = "__champion__";

    private Championship() {}

    public static ChallengeReport judge(
            Bot challenger, Bot champion,
            List<Long> judgingSeeds, List<Long> holdoutSeeds,
            int width, int height) {

        List<Replay> replays = SeriesRunner.run(
                CHALLENGER_ID, challenger::move,
                CHAMPION_ID, champion::move,
                judgingSeeds, width, height, true);

        Standing standing = Standing.of(replays, CHALLENGER_ID);
        boolean promoted = meetsThreshold(standing);

        double holdoutRate = Double.NaN;
        List<DiagnosisEntry> diagnosis = List.of();

        if (promoted) {
            // 승격한 봇만 홀드아웃 시드를 쓴다. 심사 승률과의 격차가 과적합 신호다.
            List<Replay> holdout = SeriesRunner.run(
                    CHALLENGER_ID, challenger::move,
                    CHAMPION_ID, champion::move,
                    holdoutSeeds, width, height, true);
            holdoutRate = Standing.of(holdout, CHALLENGER_ID).scoreRate();
        } else {
            diagnosis = diagnose(replays);
        }

        return new ChallengeReport(
                challenger.name(), champion.name(), promoted,
                standing.scoreRate(), PROMOTION_THRESHOLD,
                standing.wins(), standing.draws(), standing.losses(),
                holdoutRate, diagnosis);
    }

    /**
     * 승점 승률이 기준을 만족하는가. 기준은 "60% 이상"이지 "60% 초과"가
     * 아니다 — {@code >=}로 정확히 경계값을 포함한다.
     */
    static boolean meetsThreshold(Standing standing) {
        return standing.scoreRate() >= PROMOTION_THRESHOLD;
    }

    /** 패배한 경기들에서 손실이 가장 큰 수를 뽑는다. */
    private static List<DiagnosisEntry> diagnose(List<Replay> replays) {
        List<DiagnosisEntry> all = new ArrayList<>();

        for (Replay r : replays) {
            int mySeat = Standing.seatOf(r, CHALLENGER_ID);
            boolean lost = !r.result().isDraw() && r.result().winner() != mySeat;
            if (!lost) continue;

            for (MoveAnalysis a : LossAnalyzer.worstMoves(r, mySeat, 1)) {
                all.add(new DiagnosisEntry(
                        r.seed(), a.turn(),
                        a.chose().name(), a.best().name(),
                        a.reachAfterBest(), a.reachAfterChosen(), a.loss()));
            }
        }

        return all.stream()
                .sorted(Comparator.comparingInt(DiagnosisEntry::loss).reversed())
                .limit(DIAGNOSIS_LIMIT)
                .toList();
    }
}
