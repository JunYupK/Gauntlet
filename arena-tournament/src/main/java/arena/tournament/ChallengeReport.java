package arena.tournament;

import java.util.List;

/**
 * 챔피언전 결과. 반려 시 JSON으로 직렬화해 에이전트에게 돌려준다.
 *
 * holdoutScoreRate는 승격했을 때만 채워지고, 반려 시에는 NaN이다.
 * 심사 승률과의 격차가 시드 과적합의 정도를 말해준다.
 */
public record ChallengeReport(
        String challenger,
        String champion,
        boolean promoted,
        double scoreRate,
        double threshold,
        int wins,
        int draws,
        int losses,
        double holdoutScoreRate,
        List<DiagnosisEntry> diagnosis
) {}
