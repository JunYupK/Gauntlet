package arena.api.cli;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChallengeCommandTest {

    /**
     * Gen00Bot은 지금 유일하게 등록된 세대라 BotRegistry.latestGeneration()
     * 자신이다 — 도전자==챔피언 가드가 Championship.judge를 부르기 전에
     * 곧바로 걸린다. 이 테스트는 경기를 한 판도 돌리지 않고 밀리초
     * 안에 끝난다. 반려(코드 1)와 구분되는 호출 오류(코드 2)를 고정한다.
     */
    @Test
    void 도전자가_현_챔피언과_같으면_2를_반환한다() {
        assertEquals(2, ChallengeCommand.run("Gen00Bot"));
    }
}
