package arena.gate;

import arena.core.GameView;
import arena.gate.traps.CleanBot;
import arena.gate.traps.SlowTrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import static org.junit.jupiter.api.Assertions.*;

class TimeBudgetGateTest {

    private static List<GameView> positions;

    @BeforeAll
    static void samplePositions() {
        // G6는 시간을 재므로 국면 수를 줄여 테스트를 빠르게 유지한다.
        positions = PositionSampler.sample(500, 30, 30);
    }

    @Test
    void 아이디는_G6다() {
        assertEquals("G6", new TimeBudgetGate(positions, 5.0).id());
    }

    @Test
    void 느린_봇을_반려하고_실측값을_알려준다() {
        // SlowTrap은 한 수에 수십 ms를 쓰므로 국면 20개로도 충분히 판별된다.
        GateResult r = new TimeBudgetGate(positions.subList(0, 20), 5.0)
                .check(GateContextFixture.of(new SlowTrap()));

        assertFalse(r.passed());
        assertTrue(r.detail().contains("p99"), r.detail());
    }

    /**
     * G6의 accept 경로를 <b>프로덕션 상한 5.0 그대로</b> 덮는 테스트다.
     *
     * {@code GateRunnerTest.강한_봇을_실제로_통과시킨다}가 상한을 완화한
     * 뒤로, G6가 실제 예산 안에서 통과 판정을 내리는지를 확인하는 곳은
     * 여기 하나다(D69). 옮기거나 완화하지 말 것.
     */
    @Test
    void 빠른_봇을_통과시킨다() {
        GateResult r = new TimeBudgetGate(positions, 5.0)
                .check(GateContextFixture.of(new CleanBot()));
        assertTrue(r.passed(), r.detail());
    }

    /**
     * 통과할 때도 실측 p50·p99를 남겨야 한다.
     *
     * 예전에는 통과 시 {@code GateResult.pass()}가 빈 detail을 주는 바람에
     * 여유가 얼마나 남았는지 아무도 볼 수 없었다 — 마진이 세대를 거듭하며
     * 줄어드는 게 보이지 않다가, 어느 날 반려로 뒤집히고 나서야 처음
     * 숫자를 보게 된다. 그 시점엔 "언제부터 느려졌나"를 되짚을 기록이
     * 없다. D64의 G6 플레이크 진단이 실제로 이것 때문에 오래 걸렸다.
     */
    @Test
    void 통과할_때도_실측_p50과_p99를_남긴다() {
        GateResult r = new TimeBudgetGate(positions, 5.0)
                .check(GateContextFixture.of(new CleanBot()));

        assertTrue(r.passed(), r.detail());
        assertTrue(r.detail().contains("p50"), "통과 결과에 p50이 없다: '" + r.detail() + "'");
        assertTrue(r.detail().contains("p99"), "통과 결과에 p99가 없다: '" + r.detail() + "'");
        assertTrue(r.detail().contains("상한"), "통과 결과에 상한이 없다: '" + r.detail() + "'");
    }

    /**
     * 이 문자열은 gate-report.json으로 흘러 들어간다. 기본 로케일에
     * 맡기면 소수점이 쉼표인 로케일(예: 독일어)에서 같은 입력이 다른
     * 바이트를 만든다 — 이 산출물의 존재 이유가 바이트 동일성이다.
     * 기본 로케일을 실제로 바꿔 놓고 재서, 끝나면 되돌린다.
     */
    @Test
    void 실측값은_로케일이_바뀌어도_같은_문자열이다() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.US);
            String us = new TimeBudgetGate(positions.subList(0, 20), 5.0)
                    .check(GateContextFixture.of(new CleanBot())).detail();

            Locale.setDefault(Locale.GERMANY);
            String de = new TimeBudgetGate(positions.subList(0, 20), 5.0)
                    .check(GateContextFixture.of(new CleanBot())).detail();

            // 실측 숫자 자체는 실행마다 다르므로, 로케일이 바꿔치기하는
            // 소수 구분자만 본다: 쉼표 소수점이 섞이면 안 된다.
            assertTrue(us.contains("상한 5.0 ms"), "US 로케일 출력이 이상하다: " + us);
            assertTrue(de.contains("상한 5.0 ms"),
                    "독일어 로케일에서 소수점이 쉼표로 바뀌었다: " + de);
        } finally {
            Locale.setDefault(original);
        }
    }
}
