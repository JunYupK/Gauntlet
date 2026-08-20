package arena.gate;

import java.util.List;

/**
 * 관문 전체의 판정 결과. 반려 시 JSON으로 직렬화해 에이전트에게 돌려준다.
 * failedGate가 null이면 통과다.
 */
public record GateReport(
        String botName,
        boolean passed,
        String failedGate,
        String detail,
        List<GateResult> results
) {}
