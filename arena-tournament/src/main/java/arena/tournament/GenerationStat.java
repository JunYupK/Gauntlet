package arena.tournament;

/**
 * 세대 하나의 성적. 개선 곡선 화면이 이걸 그대로 그린다.
 *
 * avgSurvivalTurns가 R3의 주 지표다. 화면에서 패널이 멈추는 시점
 * 그 자체이므로, 눈에 보이는 것과 코드가 재는 것이 같은 양이 된다.
 */
public record GenerationStat(
        int generation,
        String botName,
        double avgSurvivalTurns,
        double occupancy,
        double suicideRate,
        double scoreRate,
        double holdoutScoreRate,
        int attempts
) {}
