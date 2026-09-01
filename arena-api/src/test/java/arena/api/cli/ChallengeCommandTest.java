package arena.api.cli;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChallengeCommandTest {

    /**
     * Gen00Bot은 지금 등록된 세대 중 가장 낮다 — BotRegistry.championFor는
     * 도전자보다 낮은 세대를 찾지 못해 IllegalArgumentException을 던지고,
     * ChallengeCommand는 이를 도전자 조회와 같은 catch로 잡아 종료 코드
     * 2를 낸다. 이 테스트는 경기를 한 판도 돌리지 않고 밀리초 안에
     * 끝난다. 반려(코드 1)와 구분되는 호출 오류(코드 2)를 고정한다.
     */
    @Test
    void 도전자보다_낮은_세대가_없으면_2를_반환한다() {
        assertEquals(2, ChallengeCommand.run("Gen00Bot"));
    }
}
