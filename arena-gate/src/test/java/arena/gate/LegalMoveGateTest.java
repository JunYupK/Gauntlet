package arena.gate;

import arena.bots.baseline.WallAvoidBot;
import arena.core.Direction;
import arena.core.GameView;
import arena.gate.traps.CleanBot;
import arena.gate.traps.CrashTrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class LegalMoveGateTest {

    private static List<GameView> positions;

    @BeforeAll
    static void samplePositions() {
        positions = PositionSampler.sample(2_000, 30, 30);
    }

    @Test
    void 아이디는_G4다() {
        assertEquals("G4", new LegalMoveGate(positions).id());
    }

    @Test
    void 예외를_던지는_봇을_반려하고_반례를_알려준다() {
        GateResult r = new LegalMoveGate(positions).check(GateContextFixture.of(new CrashTrap()));

        assertFalse(r.passed());
        assertTrue(r.detail().contains("ArrayIndexOutOfBounds"), r.detail());
        assertTrue(r.detail().contains("myHead"), "반례 국면을 알려줘야 한다: " + r.detail());
    }

    @Test
    void 정상_봇을_통과시킨다() {
        assertTrue(new LegalMoveGate(positions)
                .check(GateContextFixture.of(new CleanBot())).passed());
        assertTrue(new LegalMoveGate(positions)
                .check(GateContextFixture.of(new WallAvoidBot())).passed());
    }

    @Test
    void null을_반환하는_봇을_반려한다() {
        GateResult r = new LegalMoveGate(positions).check(GateContextFixture.of(new NullBot()));
        assertFalse(r.passed());
        assertTrue(r.detail().contains("null"), r.detail());
    }

    /**
     * 리뷰 반려(1차) 이후 다시 쓴 테스트. 예전 버전은 "낙서 봇 다음에 다른
     * 봇을 돌려도 G4가 통과한다"만 봤는데, G4는 wall 내용에 아예 무감각한
     * 검사(예외 없이 값을 반환하는가)라 표본이 실제로 훼손됐어도 이 assert는
     * 그대로 통과한다 — 아무것도 증명하지 못하는 테스트였다.
     *
     * 이번엔 wall "내용"을 직접 비교한다: 낙서 봇을 먹이기 전 표본의 wall을
     * 전부 깊은 복사로 떠 두고, 낙서 봇이 (G4가 각 국면을 넘길 때마다) 자기가
     * 받은 wall을 전부 true로 덮어쓰게 한 다음, 표본이 여전히 원래 내용과
     * 같은지 확인한다. 공유 static {@code positions}를 건드리면 다른 테스트가
     * 실행 순서에 의존하게 되므로, 이 테스트만 쓰는 별도 표본을 뜬다.
     */
    @Test
    void 낙서_봇에게_먹여도_표본_원본의_wall_내용은_바뀌지_않는다() {
        List<GameView> local = PositionSampler.sample(300, 30, 30);
        boolean[][][] before = deepCopyAll(local);

        new LegalMoveGate(local).check(GateContextFixture.of(new ScribbleTrap()));

        for (int i = 0; i < local.size(); i++) {
            assertTrue(Arrays.deepEquals(before[i], local.get(i).wall()),
                    "국면 " + i + "의 wall 내용이 낙서 봇 심사 중에 바뀌었다");
        }
    }

    private static boolean[][][] deepCopyAll(List<GameView> views) {
        boolean[][][] copy = new boolean[views.size()][][];
        for (int i = 0; i < views.size(); i++) {
            boolean[][] src = views.get(i).wall();
            boolean[][] rowCopy = new boolean[src.length][];
            for (int y = 0; y < src.length; y++) {
                rowCopy[y] = src[y].clone();
            }
            copy[i] = rowCopy;
        }
        return copy;
    }

    static final class NullBot implements arena.bots.Bot {
        public String name() { return "NullBot"; }
        public Direction move(GameView view) { return null; }
    }

    /** 자기가 받은 국면의 wall을 직접 훼손하는 낙서 봇. */
    static final class ScribbleTrap implements arena.bots.Bot {
        public String name() { return "ScribbleTrap"; }
        public Direction move(GameView view) {
            for (boolean[] row : view.wall()) {
                java.util.Arrays.fill(row, true);
            }
            return view.myDir();
        }
    }
}
