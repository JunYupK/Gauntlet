package arena.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class StandingTest {

    private Replay replay(String id0, String id1, boolean swapped, int winner) {
        MatchResult result = new MatchResult(winner, 10,
                winner < 0 ? DeathReason.HEAD_ON_COLLISION : DeathReason.P0_HIT_OWN_WALL);
        return new Replay(Replay.SCHEMA, "m", 30, 30, 1L, swapped,
                id0, new Point(1, 1), Direction.UP,
                id1, new Point(2, 2), Direction.DOWN,
                "UU", result, "sha256:x");
    }

    @Test
    void 좌석_교대_경기의_승자를_올바르게_귀속한다() {
        List<Replay> replays = List.of(
                replay("hero", "rival", false, 0),   // hero가 0번 좌석에서 승
                replay("rival", "hero", true, 1)     // hero가 1번 좌석에서 승
        );

        Standing s = Standing.of(replays, "hero");
        assertEquals(2, s.wins());
        assertEquals(0, s.losses());
    }

    @Test
    void 무승부는_0점5를_준다() {
        List<Replay> replays = List.of(
                replay("hero", "rival", false, 0),    // 승
                replay("hero", "rival", false, -1),   // 무
                replay("hero", "rival", false, 1)     // 패
        );

        Standing s = Standing.of(replays, "hero");
        assertEquals(1, s.wins());
        assertEquals(1, s.draws());
        assertEquals(1, s.losses());
        assertEquals(0.5, s.scoreRate(), 1e-9);
    }

    @Test
    void 승점_승률은_승과_무의_절반을_합해_나눈_값이다() {
        List<Replay> replays = List.of(
                replay("hero", "rival", false, 0),
                replay("hero", "rival", false, 0),
                replay("hero", "rival", false, 0),
                replay("hero", "rival", false, -1)
        );
        // (3 + 0.5) / 4 = 0.875
        assertEquals(0.875, Standing.of(replays, "hero").scoreRate(), 1e-9);
    }

    /**
     * 리뷰 반려(D52) — {@code bot0Id().equals(subjectId) ? 0 : 1}처럼
     * "0번이 아니면 무조건 1번"으로 넘겨짚으면, subjectId가 이 리플레이
     * 어디에도 없는 경우(오타 등)조차 조용히 0번으로 판정해버린다. 그런
     * 리플레이를 섞으면 결과가 틀렸다는 신호 없이 그럴듯한 Standing이
     * 나온다 — 침묵 오판정이 던지는 예외보다 나쁘다.
     */
    @Test
    void subjectId가_리플레이에_없으면_예외를_던진다() {
        List<Replay> replays = List.of(replay("hero", "rival", false, 0));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> Standing.of(replays, "typo"));
        assertTrue(e.getMessage().contains("typo"), e.getMessage());
    }

    /**
     * seatOf 자체는 bot0Id를 먼저 확인하므로, bot0Id와 bot1Id가 우연히
     * 같은 문자열(진짜 이름 충돌)이면 여전히 0번으로 판정한다 — 이건
     * Standing 하나만으로는 풀 수 없는 진짜 모호함이다(양쪽 좌석
     * 이름이 정말 같으면 이름만으로 어느 쪽인지 구분할 방법이 없다).
     * 그래서 실제 수정은 여기가 아니라 호출자 쪽에 있다 — RegressionGate가
     * 절대 베이스라인 이름과 겹치지 않는 예약어("subject")를 넘겨서
     * 애초에 이 상황 자체가 프로덕션에서 발생하지 않게 막는다(
     * RegressionGateTest.봇_이름이_베이스라인과_같아도_결과가_왜곡되지_않는다
     * 가 그 쪽을 증명한다). 여기서는 그 잔여 모호함이 어떻게 해소되는지
     * (예외가 아니라 0번 고정)를 문서로 고정해 둔다.
     */
    @Test
    void 이름이_충돌하면_bot0Id_쪽으로_판정한다() {
        List<Replay> replays = List.of(replay("dup", "dup", false, 0));

        Standing s = Standing.of(replays, "dup");
        assertEquals(1, s.wins(), "충돌 시 bot0Id 우선 규칙이 바뀌면 이 테스트가 알려준다");
    }
}
