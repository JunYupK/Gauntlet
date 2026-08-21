package arena.tournament;

/**
 * 한 번의 시도.
 *
 * stage는 GATE(관문 단계에서 결론이 남) 또는 CHAMPIONSHIP(챔피언전까지 진행됨)이다.
 * verdict는 stage에 따라 PASSED(관문 통과, 챔피언전은 별도 기록),
 * PROMOTED(챔피언전 승격) 또는 REJECTED(관문·챔피언전 어느 쪽이든 반려) 중 하나다.
 * failedGate는 GATE 단계에서 반려된 경우에만 채워지고, 그 외에는 null이다.
 */
public record AttemptRecord(
        int generation,
        int attempt,
        String verdict,
        String stage,
        String failedGate,
        String detail
) {}
