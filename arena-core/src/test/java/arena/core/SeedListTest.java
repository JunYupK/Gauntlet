package arena.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 시드 목록 규칙의 단일 정의를 못박는다.
 *
 * 이 규칙은 원래 세 곳(SeriesRunner·Championship·BundleBuilder)에
 * 없거나 제각각이었다 — BundleBuilder만 roundRobinSeeds의 빈 목록을
 * 거부하고 judgingSeeds는 무검사로 통과시켰다. 규칙을 자리마다 다시
 * 적는 대신 여기 한 번만 적고 세 경계가 모두 이걸 부른다.
 */
class SeedListTest {

    @Test
    void 빈_목록을_거부한다() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> SeedList.validate(List.of(), "judgingSeeds"));
        assertTrue(e.getMessage().contains("judgingSeeds"),
                "어느 인자가 잘못됐는지 메시지에 없다: " + e.getMessage());
    }

    @Test
    void null_목록을_거부한다() {
        assertThrows(IllegalArgumentException.class,
                () -> SeedList.validate(null, "holdoutSeeds"));
    }

    /**
     * 같은 시드가 두 번 들어오면 완전히 같은 경기가 두 번 치러진다 —
     * 결정론이라 결과가 정확히 같기 때문에 그 중복이 승률·평균을 조용히
     * 편향시킨다. 승격 판정(60%)이 걸린 자리라 실력이 아니라 인자 실수가
     * 판정을 바꾸는 길이 된다.
     */
    @Test
    void 중복된_시드를_거부하고_어느_시드인지_알려준다() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> SeedList.validate(List.of(1L, 2L, 1L), "judgingSeeds"));
        assertTrue(e.getMessage().contains("1"), e.getMessage());
    }

    @Test
    void null_원소를_거부한다() {
        List<Long> withNull = new ArrayList<>();
        withNull.add(1L);
        withNull.add(null);

        assertThrows(IllegalArgumentException.class,
                () -> SeedList.validate(withNull, "seeds"));
    }

    @Test
    void 정상적인_목록은_통과시킨다() {
        assertDoesNotThrow(() -> SeedList.validate(List.of(1L, 2L, 3L), "seeds"));
        assertDoesNotThrow(() -> SeedList.validate(List.of(7L), "seeds"));
    }
}
