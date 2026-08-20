package arena.gate;

import arena.core.Direction;
import arena.core.GameView;

import java.util.Arrays;
import java.util.List;

/**
 * G6 — 시간 예산.
 *
 * 성능을 재는 유일한 지점이다. 대전 중에는 어떤 시간 제한도 걸지
 * 않는다. 시간 기반 판정은 같은 조건에 다른 결과를 내어 R1을
 * 깨뜨리기 때문이다. 여기를 통과한 봇의 대전은 순수하게 결정론이다.
 *
 * <p><b>복사는 타이머 밖에서.</b> {@link PositionSampler#copyOf}가 타이머와
 * 완전히 분리된 메서드로 존재하는 이유가 바로 이 관문이다 — 30×30 기준
 * {@code wall} 복제는 약 900바이트짜리 배열 클론 하나로 끝나지만, 이걸
 * 타이머 안에 넣으면 그 비용이 매 수마다 측정치에 얹혀 p99를 부풀린다.
 * 5ms라는 상한을 판정하는 자리에서 그 정도 오차도 무시할 수 없으므로,
 * {@code copyOf}는 항상 {@code System.nanoTime()} 호출 이전에 끝낸다 —
 * 웜업 루프에서도 마찬가지다: 표본 리스트는 G4·G5·G6가 공유하므로, 웜업이라고
 * 원본을 그대로 넘기면 봇이 훼손한 국면이 표본에 영구히 남는다.
 *
 * <p><b>웜업.</b> 콜드 JIT(인터프리터 단계)로 잰 첫 호출들은 스테디스테이트보다
 * 훨씬 느리다 — 이걸 그대로 표본에 섞으면 빠른 봇도 어쩌다 p99를 넘겨
 * 이 관문에서 들쭉날쭉(flaky) 떨어질 수 있다({@code CleanBot}처럼 항상
 * 통과해야 하는 봇이 어쩌다 떨어지면 그건 봇의 결함이 아니라 관문의
 * 결함이다). 웜업은 반복 횟수 상한({@link #WARMUP_ITERATIONS})과 시간
 * 상한({@link #WARMUP_BUDGET_NANOS})을 동시에 걸어 "and"로 멈춘다 — 빠른
 * 봇은 반복 상한에 먼저 걸려 넉넉히 데워지고, {@code SlowTrap}처럼 한 수에
 * 수십 ms가 걸리는 봇은 시간 상한에 먼저 걸려 웜업 단계에서 테스트 실행
 * 시간을 잡아먹지 않는다 — 어차피 느린 봇은 데워도 느리므로 웜업을 오래
 * 끌 이유가 없다.
 *
 * <p><b>p99 계산.</b> n개 표본을 오름차순 정렬한 뒤 "표본의 99%가 이 값
 * 이하"가 되는 가장 작은 값을 쓴다(최근접 순위법) — 인덱스는
 * {@code ceil(n * 0.99) - 1}(0-기준)이다. n=10,000이면 인덱스 9,899, 즉
 * 상위 100개(1%)만 이 값을 넘을 수 있다.
 */
public final class TimeBudgetGate implements Gate {

    private static final int WARMUP_ITERATIONS = 3_000;
    private static final long WARMUP_BUDGET_NANOS = 300_000_000L; // 300ms

    private final List<GameView> positions;
    private final double p99LimitMillis;

    public TimeBudgetGate(List<GameView> positions, double p99LimitMillis) {
        this.positions = positions;
        this.p99LimitMillis = p99LimitMillis;
    }

    @Override
    public String id() { return "G6"; }

    @Override
    public GateResult check(GateContext ctx) {
        if (positions.isEmpty()) return GateResult.pass(id()); // 잴 국면이 없으면 공허하게 통과

        GateResult warmupResult = warmUp(ctx);
        if (warmupResult != null) return warmupResult;

        long[] nanos = new long[positions.size()];
        for (int i = 0; i < positions.size(); i++) {
            // 복사는 타이머 밖에서 — move() 호출만 잰다.
            GameView view = PositionSampler.copyOf(positions.get(i));

            long start = System.nanoTime();
            Direction d;
            try {
                d = ctx.bot().move(view);
            } catch (RuntimeException | StackOverflowError e) {
                return GateResult.fail(id(), "국면 " + i + "에서 "
                        + describeThrow(e) + "\n" + describe(positions.get(i)));
            }
            nanos[i] = System.nanoTime() - start;

            if (d == null) {
                return GateResult.fail(id(),
                        "국면 " + i + "에서 null을 반환했다\n" + describe(positions.get(i)));
            }
        }

        Arrays.sort(nanos);
        double p50 = percentileMillis(nanos, 0.50);
        double p99 = percentileMillis(nanos, 0.99);

        if (p99 <= p99LimitMillis) {
            return GateResult.pass(id());
        }
        return GateResult.fail(id(), String.format(
                "너무 느리다 — p50 %.3f ms, p99 %.3f ms (상한 %.1f ms)",
                p50, p99, p99LimitMillis));
    }

    /** 반환값이 null이 아니면 웜업 중 문제가 생긴 것 — 실패 결과를 그대로 올린다. */
    private GateResult warmUp(GateContext ctx) {
        if (positions.isEmpty()) return null;

        long deadline = System.nanoTime() + WARMUP_BUDGET_NANOS;
        int i = 0;
        while (i < WARMUP_ITERATIONS && System.nanoTime() < deadline) {
            GameView original = positions.get(i % positions.size());
            GameView view = PositionSampler.copyOf(original); // 웜업도 타이머 밖 복사 규칙을 따른다
            try {
                ctx.bot().move(view);
            } catch (RuntimeException | StackOverflowError e) {
                return GateResult.fail(id(), "웜업 중 국면(원본 인덱스 " + (i % positions.size())
                        + ")에서 " + describeThrow(e) + "\n" + describe(original));
            }
            i++;
        }
        return null;
    }

    /** 최근접 순위법. idx = ceil(n*p) - 1, [0, n-1]로 클램프. */
    private static double percentileMillis(long[] sortedNanos, double p) {
        int n = sortedNanos.length;
        int idx = (int) Math.ceil(n * p) - 1;
        idx = Math.max(0, Math.min(n - 1, idx));
        return sortedNanos[idx] / 1_000_000.0;
    }

    private static String describe(GameView v) {
        return "  turn=" + v.turn()
                + " myHead=" + v.myHead() + " myDir=" + v.myDir()
                + " oppHead=" + v.oppHead() + " oppDir=" + v.oppDir();
    }

    private static String describeThrow(Throwable e) {
        return e.getClass().getSimpleName()
                + (e.getMessage() == null ? "" : ": " + e.getMessage());
    }
}
