package arena.gate.traps;

import arena.bots.Bot;
import arena.core.Direction;
import arena.core.GameView;
import arena.core.Point;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 함정 봇이 정말 함정인지 확인한다.
 * 함정이 함정이 아니면 관문 테스트가 통과해도 아무것도 증명하지 못한다.
 */
class TrapSanityTest {

    private GameView view(int x, int y) {
        return new GameView(30, 30, new boolean[30][30],
                new Point(x, y), Direction.RIGHT,
                new Point(1, 1), Direction.LEFT, 1);
    }

    @Test
    void StatefulTrap은_정말_인스턴스_필드를_갖는다() {
        boolean hasInstanceField = false;
        for (var f : StatefulTrap.class.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers())) hasInstanceField = true;
        }
        assertTrue(hasInstanceField);
    }

    @Test
    void CrashTrap은_아래_가장자리에서_정말_터진다() {
        assertThrows(ArrayIndexOutOfBoundsException.class,
                () -> new CrashTrap().move(view(15, 29)));
    }

    @Test
    void NondeterministicTrap은_같은_국면에_다른_답을_낼_수_있다() {
        Bot bot = new NondeterministicTrap();
        GameView v = view(15, 15);

        Direction first = bot.move(v);
        boolean differed = false;
        for (int i = 0; i < 100_000 && !differed; i++) {
            if (bot.move(v) != first) differed = true;
        }
        assertTrue(differed, "비결정론 함정이 결정론적으로 동작한다");
    }

    @Test
    void CleanBot은_모든_위치에서_유효한_방향을_낸다() {
        Bot bot = new CleanBot();
        for (int x = 1; x < 29; x++) {
            for (int y = 1; y < 29; y++) {
                assertNotNull(bot.move(view(x, y)));
            }
        }
    }

    @Test
    void CleanBot은_인스턴스_필드가_없다() {
        for (var f : CleanBot.class.getDeclaredFields()) {
            assertTrue(Modifier.isStatic(f.getModifiers()),
                    "CleanBot에 인스턴스 필드가 있다: " + f.getName());
        }
    }
}
