package arena.gate;

import arena.bots.Bot;
import arena.gate.traps.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 함정 봇 스위트의 본체.
 * 각 함정이 정확히 자기 관문에서 걸려야 한다.
 *
 * {@code CleanBot}은 계약 관문(G2~G6)의 대조군이지 G7(실력)의 대조군이
 * 아니다(D51). "관문이 통과시켜야 할 것까지 반려하지 않는가"를 확인하는
 * 게 CleanBot의 일이고, 그 일을 증명하려면 짧고 뻔한 봇이어야 값어치가
 * 있다 — 그래서 {@code CleanBot은_계약_관문에서_반려되지_않는다}는 G7을
 * 보지 않는다. G7의 accept 경로(진짜 강한 봇을 통과시키는가)는
 * {@link RegressionGateTest}가 {@link arena.gate.traps.StrongBot}으로
 * 따로 검증한다.
 *
 * 대부분의 함정은 국면 하나 처리 비용이 사실상 0이므로(CleanBot·WeakTrap
 * 포함) 프로덕션 표본 크기(10,000)·심사 시드(1..50) 그대로 먹여도 순식간에
 * 끝난다 — 그래서 {@link #run(Bot)}은 항상 {@link
 * GateRunner#run(GateContext)}(오버로드 없는 쪽)를 그대로 쓴다.
 *
 * 예외가 하나 있다. {@code SlowTrap}은 한 수에 수십 ms를 써서, G4·G5
 * (리플레이 겹 포함) 두 겹만 프로덕션 표본으로 먹여도 수십 분이 걸린다
 * (실측: 국면 10,000개·시드 50개 조합으로 2분 넘게 끝나지 않아 강제
 * 종료했다). {@code SlowTrap은_G6에서_걸린다}는 그래서 표본·시드를 줄인
 * 별도 오버로드({@link GateRunner#run(GateContext, int, double)})를 쓴다
 * — 줄이는 건 오직 "어느 관문에서 걸리는가"라는 라우팅 질문에 필요한
 * 만큼일 뿐이다. 이 오버로드가 프로덕션 경로와 어떻게 갈라지지 않게
 * 막혀 있는지는 {@link GateRunner} javadoc과 {@code
 * 관문_상수는_스펙값을_그대로_쓴다}를 보라.
 */
class GateRunnerTest {

    private GateReport run(Bot bot) {
        return GateRunner.run(GateContextFixture.of(bot));
    }

    /**
     * CleanBot의 일은 계약 준수(G2~G6)의 대조군이지 실력(G7) 증명이
     * 아니다(D51) — 짧고 뻔한 봇일수록 "관문이 통과시켜야 할 것까지
     * 반려하지 않는가"를 더 잘 증명한다. 그래서 이 테스트는 G7 결과를
     * 보지 않고, results()를 훑어 G2~G6 각각이 실제로 통과했는지만
     * 확인한다 — CleanBot이 G7에서 막히더라도(WallAvoidBot에게 실제로
     * 진다, D50) 그건 이 테스트의 관심사가 아니다.
     */
    @Test
    void CleanBot은_계약_관문에서_반려되지_않는다() {
        GateReport report = run(new CleanBot());

        List<String> contractGates = List.of("G2", "G3", "G4", "G5", "G6");
        for (GateResult r : report.results()) {
            if (!contractGates.contains(r.gateId())) continue;
            assertTrue(r.passed(), r.gateId() + "가 CleanBot을 반려했다: " + r.detail());
        }
        // 계약 관문 5개가 전부 실제로 채점됐는지도 확인한다 — G7에서
        // 일찍 멈춰 CleanBot이 애초에 다섯 관문을 다 거치지 못했다면
        // 위 루프는 아무것도 못 보고 공허하게 통과해버린다.
        List<String> checked = report.results().stream().map(GateResult::gateId).toList();
        assertTrue(checked.containsAll(contractGates),
                "계약 관문 5개를 다 거치지 못했다: " + checked);
    }

    @Test
    void StatefulTrap은_G2에서_걸린다() {
        assertEquals("G2", run(new StatefulTrap()).failedGate());
    }

    @Test
    void ClockTrap은_G3에서_걸린다() {
        assertEquals("G3", run(new ClockTrap()).failedGate());
    }

    @Test
    void UnseededRandomTrap은_G3에서_걸린다() {
        assertEquals("G3", run(new UnseededRandomTrap()).failedGate());
    }

    @Test
    void CrashTrap은_G4에서_걸린다() {
        assertEquals("G4", run(new CrashTrap()).failedGate());
    }

    @Test
    void NondeterministicTrap은_G5에서_걸린다() {
        assertEquals("G5", run(new NondeterministicTrap()).failedGate());
    }

    @Test
    void SlowTrap은_G6에서_걸린다() {
        // 표본·시드를 줄여도 "G6에서 걸리는가"라는 라우팅 질문의 답은
        // 바뀌지 않는다 — SlowTrap은 한 수에 수십 ms를 쓰므로 국면 몇십 개,
        // 시드 몇 개로도 G4·G5를 거뜬히 통과하고 G6에서 명백히 반려된다.
        // 클래스 상단 javadoc 참고.
        GateContext ctx = GateContextFixture.of(new SlowTrap(), List.of(1L, 2L));
        GateReport report = GateRunner.run(ctx, 50, GateRunner.P99_LIMIT_MILLIS);
        assertEquals("G6", report.failedGate());
    }

    @Test
    void 관문_상수는_스펙값을_그대로_쓴다() {
        // GateRunner.SAMPLE_SIZE·P99_LIMIT_MILLIS는 G4·G5·G6가 공유하는
        // 국면 표본 크기·p99 응답 시간 상한의 단일 출처다. 다른 태스크가
        // 이 값을 그대로 인용하므로 여기서 값 자체를 고정해 둔다 —
        // 루프가 못 넘는다는 이유로 낮추지 않는다.
        assertEquals(10_000, GateRunner.SAMPLE_SIZE);
        assertEquals(5.0, GateRunner.P99_LIMIT_MILLIS, 1e-9);
    }

    @Test
    void WeakTrap은_G7에서_걸린다() {
        assertEquals("G7", run(new WeakTrap()).failedGate());
    }

    @Test
    void 첫_실패에서_멈추고_뒤_관문은_돌리지_않는다() {
        GateReport report = run(new StatefulTrap());
        assertEquals(1, report.results().size(), "G2에서 실패했는데 뒤 관문까지 돌렸다");
    }
}
