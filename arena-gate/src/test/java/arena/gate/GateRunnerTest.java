package arena.gate;

import arena.bots.Bot;
import arena.core.Direction;
import arena.core.GameView;
import arena.gate.traps.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.LongStream;

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

    /**
     * 리뷰 반려(D52) — 지금까지 109개 테스트 중 어느 것도 {@link
     * GateReport#passed()}가 실제로 true가 되는 경로를 돌려 보지 않았다.
     * 이 비트가 세대 루프가 분기하는 유일한 값인데, {@code
     * GateRunner.run}의 마지막 줄이 항상 false를 반환하거나 {@code
     * failedGate}가 절대 null이 안 되도록 망가져도 기존 테스트는 전부
     * 그대로 GREEN이었을 것이다 — CleanBot 테스트는 계약 관문만 보고,
     * RegressionGateTest는 GateResult만 보지 GateReport는 안 본다.
     *
     * {@link StrongBot}을 판정이 실제로 0패인 48개 시드 부분집합으로
     * (RegressionGateTest와 같은 근거) 표본 200으로 줄인 오버로드를 통해
     * 돌린다 — 표본을 프로덕션 크기(10,000) 그대로 쓰면 G4·G5 ①층·G6
     * 세 겹이 더해져 이 테스트 하나가 40초 이상 더 걸리는데, 여기서
     * 확인하려는 건 "accept 경로에 실제로 도달하는가"이지 표본 크기가
     * 아니므로 줄여도 이 테스트의 목적은 그대로 유지된다.
     */
    @Test
    void 강한_봇을_실제로_통과시킨다() {
        List<Long> seedsStrongBotClears = LongStream.rangeClosed(1, 50)
                .filter(seed -> seed != 13 && seed != 21)
                .boxed()
                .toList();

        StrongBot bot = new StrongBot();
        GateContext ctx = new GateContext(bot, bot.getClass(), 30, 30, seedsStrongBotClears);

        GateReport report = GateRunner.run(ctx, 200, GateRunner.P99_LIMIT_MILLIS);

        assertTrue(report.passed(), "강한 봇이 " + report.failedGate() + "에서 막혔다: " + report.detail());
        assertNull(report.failedGate());
        assertEquals(6, report.results().size(), "G2~G7 여섯 관문이 모두 기록돼야 한다");
    }

    /**
     * 리뷰 반려(D52) — {@code GateRunner.run(ctx)}(오버로드 없는 프로덕션
     * 진입점)의 본문이 {@code return run(ctx, 100, P99_LIMIT_MILLIS);}처럼
     * 몰래 작은 값으로 바뀌어도, 이 테스트가 생기기 전까지는 109개 테스트
     * 전부가 그대로 GREEN이었을 것이다 — 함정 테스트는 "어느 관문에서
     * 걸리는가"만 보고, 그건 표본 크기에 둔감하기 때문이다(관문 순서는
     * 표본이 10,000이든 100이든 똑같다). 그래서 이 가드는 "관문이 몇 번
     * 불렸는가"를 직접 센다.
     *
     * G2가 인스턴스 필드를 금지하므로, 봇 클래스 자신의 (합성이 아닌)
     * 필드로는 호출 횟수를 셀 수 없다({@link StatelessGate}가 {@code
     * isSynthetic()} 필드만 건너뛴다) — 그래서 테스트 메서드의 지역 변수
     * {@code int[] calls}를 익명 클래스가 캡처하게 한다. 캡처된 지역
     * 변수는 컴파일러가 그 익명 클래스에 합성(synthetic) 필드로 넣으므로
     * G2가 건너뛰고, {@code calls[0]++}은 배열 원소 대입일 뿐 금지 API
     * 호출이 아니므로 G3도 건드리지 않는다 — 실제로 이 봇이 G2·G3를
     * 무사히 통과하고 G4까지 가는 걸 실행으로 확인했다.
     *
     * 심사 시드를 1개로 좁힌다 — 시드 수에 비례하는 층(G5 리플레이 해시,
     * G7 실전 대국)의 호출 수를 작게 눌러 둬야, 표본 크기(SAMPLE_SIZE)에
     * 비례하는 층(G4·G5 ①층·G6)의 변화가 총 호출 수에 뚜렷이 드러난다.
     *
     * 실측(스크래치 프로브, 저장소에는 남기지 않음): 시드 1개로 프로덕션
     * {@code run(ctx)}를 돌리면 calls=63,062(G4 10,000 + G5 ①층 40,000 +
     * G6 약 13,000 + 시드 1개짜리 G5 ②층·G7). 같은 조건에서 표본만
     * 100으로 바꿔 부르면(={@code run(ctx, 100, ...)}) calls=3,662로
     * 뚝 떨어진다 — SAMPLE_SIZE(10,000)를 밑도는 값이라, 아래 단정이
     * 실제로 그 드리프트를 잡아낸다는 증거다.
     */
    @Test
    void 프로덕션_경로는_SAMPLE_SIZE만큼_국면을_실제로_먹인다() {
        int[] calls = {0};
        Bot countingBot = new Bot() {
            @Override public String name() { return "CountingBot"; }
            @Override public Direction move(GameView view) {
                calls[0]++;
                return view.myDir();
            }
        };
        GateContext ctx = new GateContext(countingBot, countingBot.getClass(), 30, 30, List.of(1L));

        GateReport report = GateRunner.run(ctx); // 오버로드 없는 프로덕션 진입점

        assertTrue(calls[0] >= GateRunner.SAMPLE_SIZE,
                "국면 호출 수(" + calls[0] + ")가 SAMPLE_SIZE(" + GateRunner.SAMPLE_SIZE
                        + ")에 못 미친다 — 프로덕션 경로가 표본을 실제로 다 먹이지 않았다는 뜻이다");
        assertEquals(6, report.results().size()); // G2~G7까지 실제로 다 돌았다
    }
}
