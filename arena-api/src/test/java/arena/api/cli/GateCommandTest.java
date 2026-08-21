package arena.api.cli;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GateCommandTest {

    /**
     * BotRegistry.byName이 등록되지 않은 이름에서 곧바로 예외를 던지므로
     * 관문(G2~G7)이 전혀 돌지 않는다 — 이 테스트는 밀리초 안에 끝난다.
     * 반려(코드 1)와 구분되는 호출 오류(코드 2)를 고정한다.
     */
    @Test
    void 등록되지_않은_봇_이름은_2를_반환한다() {
        assertEquals(2, GateCommand.run("NoSuchBot"));
    }
}
