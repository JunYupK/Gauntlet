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
 * 대부분의 함정은 국면 하나 처리 비용이 사실상 0이므로(WeakTrap 포함)
 * 프로덕션 표본 크기(10,000)·심사 시드(1..50) 그대로 먹여도 순식간에
 * 끝난다 — 그래서 {@link #run(Bot)}은 항상 {@link
 * GateRunner#run(GateContext)}(오버로드 없는 쪽)를 그대로 쓴다.
 *
 * 예외가 둘 있다. {@code SlowTrap}은 한 수에 수십 ms를 써서, G4·G5
 * (리플레이 겹 포함) 두 겹만 프로덕션 표본으로 먹여도 수십 분이 걸린다
 * (실측: 국면 10,000개·시드 50개 조합으로 2분 넘게 끝나지 않아 강제
 * 종료했다). {@code CleanBot}은 한 수에 수 ms를 쓴다(G7을 통과하려고
 * 국면 여러 개에 걸쳐 무작위 롤아웃을 여러 번 돌리기 때문 — {@link
 * CleanBot} javadoc 참고) — G4(10,000회)·G5 ①층(40,000회)·G6(약
 * 13,000회) 세 겹이 국면 표본 크기에 정비례해서 늘어나므로, 프로덕션
 * 표본 그대로면 이 한 테스트만 10분을 넘긴다.
 *
 * 둘 다 표본·시드를 줄인 별도 오버로드({@link GateRunner#run(GateContext,
 * int, double)})를 쓴다 — 줄이는 건 오직 "어느 관문에서 걸리는가"라는
 * 라우팅 질문(SlowTrap)이나 "G4·G5·G6를 통과하는가"라는 구조적 질문
 * (CleanBot)에 필요한 만큼일 뿐이다. CleanBot의 진짜 관심사인 G7(고정
 * 베이스라인 상대 0패)은 국면 표본이 아니라 심사 시드(judgingSeeds)에
 * 좌우되므로, 심사 시드는 두 테스트 모두 50개 그대로 둔다 — 표본만
 * 줄이면 G7의 엄격함은 조금도 낮아지지 않는다. SAMPLE_SIZE·
 * P99_LIMIT_MILLIS 상수 자체(10,000·5.0)는 {@link
 * #관문_상수는_스펙값을_그대로_쓴다}가 값으로 직접 고정한다.
 */
class GateRunnerTest {

    private GateReport run(Bot bot) {
        return GateRunner.run(GateContextFixture.of(bot));
    }

    @Test
    void CleanBot은_모든_관문을_통과한다() {
        // 표본을 200으로 줄여도 G4(합법성)·G5 ①층(결정론)·G6(속도)은
        // CleanBot에게 사소하게 통과된다 — 이 봇은 항상 유효한 방향을
        // 돌려주고, 순수 함수이며, 5ms 상한 대비 여유가 크다. G7(고정
        // 베이스라인 0패, 이 테스트가 실제로 검증하려는 것)은 표본이
        // 아니라 심사 시드에 좌우되므로 50개 그대로 둔다.
        GateContext ctx = GateContextFixture.of(new CleanBot());
        GateReport report = GateRunner.run(ctx, 200, GateRunner.P99_LIMIT_MILLIS);
        assertTrue(report.passed(),
                "대조군이 " + report.failedGate() + "에서 막혔다: " + report.detail());
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
