package arena.tournament;

import arena.diagnostics.MoveAnalysis;

import java.util.List;

/**
 * 갤러리 경기 하나의 진단. {@code diagnosis.json}의 원소이며
 * {@code gallery.json}과 같은 순서로 같은 개수가 나간다 — 화면이
 * 인덱스로 짝지을 수 있어야 하기 때문이다.
 *
 * {@code matchId}를 함께 싣는 이유는 그 짝짓기를 화면이 검증할 수
 * 있게 하려는 것이다. 순서만 약속으로 두면 한쪽 배열이 어긋났을 때
 * 화면이 조용히 엉뚱한 경기의 진단을 그린다.
 *
 * reach·loss는 {@code [봇][턴]}이다. 턴 인덱스는 0-based이고 리플레이의
 * 턴 1이 인덱스 0이다 — {@link arena.diagnostics.MatchMetrics}의 배열을
 * 그대로 내보내므로 그쪽 규약을 따른다. 반면 {@link MoveAnalysis#turn()}은
 * 1-based다. 두 규약이 한 파일에 섞여 나가므로 화면 쪽에서 반드시
 * 구분해야 하고, 그래서 이 javadoc이 그것을 명시한다.
 */
public record MatchDiagnosis(
        String matchId,
        int[][] reach,
        int[][] loss,
        double[] occupancy,
        double[] suicideRate,
        List<MoveAnalysis> worstMoves0,
        List<MoveAnalysis> worstMoves1
) {}
