package arena.gate;

import arena.core.GameView;

import java.util.ArrayList;
import java.util.List;

/**
 * G2부터 G7까지 순서대로 돌리고 첫 실패에서 멈춘다.
 *
 * 비용이 싼 것부터 배치했다. 피드백 속도와 비용 순서가 같은 방향이라
 * 흔한 실수일수록 빨리 알려준다.
 *
 * G1(컴파일)은 여기에 없다. Gradle이 이 코드에 도달하기 전에 판정한다.
 *
 * 관문 하나가 검사 도중 예외를 던지더라도(예: {@link ForbiddenApiGate}가
 * 클래스 바이트를 읽지 못해 던지는 {@link IllegalStateException}) 관문
 * 실행 전체가 죽지 않는다 — 그 관문을 실패로 돌리고 멈춘다. "봇은
 * 반려당할 뿐, 하네스를 무너뜨릴 수 없다"는 계약을 지키기 위함이다.
 * 한 봇이 스캔 불가능하다는 이유로 나머지 봇 심사까지 중단시키면 안 된다.
 */
public final class GateRunner {

    /** G4·G5·G6가 공유하는 국면 표본 크기. */
    public static final int SAMPLE_SIZE = 10_000;

    /** G6 응답 시간 상한. 루프가 못 넘는다고 해서 올리지 않는다. */
    public static final double P99_LIMIT_MILLIS = 5.0;

    private GateRunner() {}

    public static GateReport run(GateContext ctx) {
        return run(ctx, SAMPLE_SIZE, P99_LIMIT_MILLIS);
    }

    /**
     * 표본 크기·p99 상한을 주입할 수 있는 시야. 프로덕션 경로({@link #run(GateContext)})는
     * 언제나 {@link #SAMPLE_SIZE}·{@link #P99_LIMIT_MILLIS}를 그대로 써서 이
     * 오버로드로 위임한다 — 두 상수 자체를 낮추는 수단이 아니다.
     *
     * 존재 이유는 순전히 테스트 비용이다: 관문 순서·조기 종료 같은
     * "어느 관문에서 걸리는가"를 확인하는 테스트가, 국면 하나 처리에
     * 수십 ms를 쓰는 함정 봇(SlowTrap 등)을 프로덕션 표본 크기(10,000)
     * 그대로 먹이면 G4·G5 두 겹만으로 수십 분이 걸린다 — 표본을 줄여도
     * "어느 관문에서 걸리는가"라는 라우팅 질문의 답은 바뀌지 않는다.
     */
    static GateReport run(GateContext ctx, int sampleSize, double p99LimitMillis) {
        List<GameView> positions = PositionSampler.sample(sampleSize, ctx.width(), ctx.height());

        List<Gate> gates = List.of(
                new StatelessGate(),
                new ForbiddenApiGate(),
                new LegalMoveGate(positions),
                new DeterminismGate(positions),
                new TimeBudgetGate(positions, p99LimitMillis),
                new RegressionGate());

        List<GateResult> results = new ArrayList<>();

        for (Gate gate : gates) {
            GateResult result = checkSafely(gate, ctx);
            results.add(result);

            if (!result.passed()) {
                return new GateReport(ctx.bot().name(), false,
                        result.gateId(), result.detail(), List.copyOf(results));
            }
        }
        return new GateReport(ctx.bot().name(), true, null, "", List.copyOf(results));
    }

    /**
     * 관문이 검사 도중 예외를 던지면 그 관문의 실패로 변환한다.
     * 봇 하나가 하네스 전체를 무너뜨리는 것을 막는 마지막 방어선이다.
     */
    private static GateResult checkSafely(Gate gate, GateContext ctx) {
        try {
            return gate.check(ctx);
        } catch (RuntimeException e) {
            return GateResult.fail(gate.id(),
                    "관문 실행 중 예외 발생: " + e);
        }
    }
}
