package arena.tournament;

import arena.bots.Bot;
import arena.core.Replay;
import arena.core.SeedList;
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
 * 문자열 자체는 봇 작성자가 자유롭게 고를 수 있다). {@link Standing#seatOf}는
 * {@code bot0Id}를 먼저 검사하므로, 두 이름이 우연히든 악의적으로든 같아
 * {@code bot0Id == bot1Id}가 되면 예외를 던지지 않고 조용히 좌석 0으로
 * 판정해버린다 — 예외는 subjectId가 그 리플레이에 아예 없을 때만 나온다.
 * 즉 이름 충돌의 실제 위험은 크래시가 아니라 **조용한 증거 반전**이다:
 * {@code bot.name()}을 좌석 id로 그대로 쓰면 이름이 같은 두 봇의 대전에서
 * 도전자가 100경기 전부 좌석 0으로 귀속돼, 우연히 60% 이상을 손에 넣은
 * 클론이 초록 스위트인 채로 승격할 수 있다. 그래서 이 클래스는
 * {@code bot.name()}을 좌석 식별에 절대 쓰지 않는다 — 대신 서로 다른 게
 * 보장된 내부 예약 id({@link #CHALLENGER_ID}, {@link #CHAMPION_ID})를
 * {@link SeriesRunner}·{@link Standing}에 넘긴다. 이름이 같은 두 봇이
 * 붙어도, 심지어 같은 인스턴스가 두 자리에 다 들어와도 판정은 정상적으로
 * 끝나고 좌석 귀속도 왜곡되지 않는다.
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

        SeedList.validate(judgingSeeds, "judgingSeeds");
        SeedList.validate(holdoutSeeds, "holdoutSeeds");

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
            diagnosis = diagnose(replays, CHALLENGER_ID);
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

    /**
     * 패배한 경기들에서 손실이 가장 큰 수를 뽑는다.
     *
     * 패배가 하나도 없는 반려도 나올 수 있다 — 900턴 상한이나 정면
     * 충돌은 무승부로 끝나므로, "죽진 않지만 이기지도 못하는" 초기
     * 세대(승 0~19, 패 0, 나머지 전부 무승부)는 승점 승률이 0.60 밑인데
     * 패배 경기가 하나도 없다. {@code lost}만 보면 이런 반려는 진단이
     * 통째로 비어 다섯 번뿐인 재시도 하나를 아무 단서 없이 날린다. 그래서
     * 패배 → (없으면) 무승부 → (그래도 없으면) 전체 경기 순으로 후보를
     * 넓혀가며, 반려된 이상 진단이 비지 않게 한다. 무승부에도
     * {@code fatal}이 정면 충돌 턴을 그대로 표시하므로({@link MoveAnalysis}
     * 참고) worstMoves는 이 경우에도 결정적인 수를 여전히 앞세운다.
     *
     * {@code subjectId}를 인자로 받는다(내부 상수를 직접 참조하지 않는다)
     * — 테스트가 손으로 만든 {@link Replay}로 무승부 전용 경로를 직접
     * 찌를 수 있게 하기 위해서다. 패키지 전용으로 연 것도 같은 이유다.
     */
    static List<DiagnosisEntry> diagnose(List<Replay> replays, String subjectId) {
        List<DiagnosisEntry> fromLosses = collect(replays, subjectId, ChallengeOutcome.LOSS);
        if (!fromLosses.isEmpty()) return fromLosses;

        List<DiagnosisEntry> fromDraws = collect(replays, subjectId, ChallengeOutcome.DRAW);
        if (!fromDraws.isEmpty()) return fromDraws;

        return collect(replays, subjectId, ChallengeOutcome.ANY);
    }

    private enum ChallengeOutcome { LOSS, DRAW, ANY }

    private static List<DiagnosisEntry> collect(
            List<Replay> replays, String subjectId, ChallengeOutcome outcome) {
        List<DiagnosisEntry> all = new ArrayList<>();

        for (Replay r : replays) {
            int mySeat = Standing.seatOf(r, subjectId);
            boolean isDraw = r.result().isDraw();
            boolean isLoss = !isDraw && r.result().winner() != mySeat;

            boolean include = switch (outcome) {
                case LOSS -> isLoss;
                case DRAW -> isDraw;
                case ANY -> true;
            };
            if (!include) continue;

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
