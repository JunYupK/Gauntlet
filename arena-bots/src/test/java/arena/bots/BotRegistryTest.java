package arena.bots;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BotRegistryTest {

    @Test
    void 이름으로_봇을_찾는다() {
        assertEquals("Gen00Bot", BotRegistry.byName("Gen00Bot").name());
        assertEquals("WallAvoidBot", BotRegistry.byName("WallAvoidBot").name());
    }

    @Test
    void 없는_이름은_친절한_오류를_낸다() {
        var e = assertThrows(IllegalArgumentException.class,
                () -> BotRegistry.byName("Gen99Bot"));
        assertTrue(e.getMessage().contains("Gen99Bot"), e.getMessage());
    }

    @Test
    void 세대_봇은_번호_오름차순으로_나온다() {
        var generations = BotRegistry.allGenerations();

        assertFalse(generations.isEmpty());
        assertEquals("Gen00Bot", generations.get(0).name());
        for (int i = 1; i < generations.size(); i++) {
            assertTrue(generations.get(i).name().compareTo(generations.get(i - 1).name()) > 0,
                    "세대 순서가 뒤엉켰다");
        }
    }

    @Test
    void 최신_세대는_목록의_마지막이다() {
        var generations = BotRegistry.allGenerations();
        assertEquals(generations.get(generations.size() - 1).name(),
                BotRegistry.latestGeneration().name());
    }
}
