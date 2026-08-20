package arena.gate;

import arena.gate.traps.StrongBot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * G7의 accept 경로 — 실제로 강한 봇을 통과시키는가.
 *
 * {@code GateRunnerTest.WeakTrap은_G7에서_걸린다}가 reject 경로를
 * 증명하지만, reject 케이스 하나만으로는 부족하다 — G7이 사실은 뭐가
 * 오든 항상 반려하도록 고장나 있어도 그 테스트는 여전히 GREEN이다.
 * "패배 0회면 통과시키는가"라는 accept 규칙은 실제로 이길 수 있는
 * 봇으로 확인해야 들킨다.
 *
 * {@link StrongBot}은 심사 시드 1..50 전체에서는 WallAvoidBot에게
 * 100판 중 2판을 진다(seed 13 교대, seed 21 정방향 — D50). 여기서
 * 검증하려는 건 StrongBot의 실력이 아니라 "패배 0회 ⇒ 통과"라는
 * {@link RegressionGate}의 규칙 자체이므로, StrongBot이 실제로 0패인
 * 시드 부분집합(1..50에서 13·21만 뺀 48개)으로 좁혀서 돈다 —
 * {@link arena.core.SeriesRunner}는 시드마다 정방향·교대 두 경기를
 * 만들므로, 시드를 통째로 빼면 그 시드의 두 경기 모두 제외된다.
 *
 * 이렇게 시드를 줄이는 건 이 테스트 하나뿐이다. 프로덕션 G7({@link
 * GateRunner})은 언제나 judgingSeeds 1..50 전체를 그대로 쓰고,
 * {@code RegressionGate}의 임계값(패배 0회)도 손대지 않는다 — 여기서
 * 시험하는 건 그 임계값이 진짜 강한 봇을 실제로 통과시키는지이지,
 * 임계값 자체를 낮추는 게 아니다.
 */
class RegressionGateTest {

    @Test
    void 세_베이스라인_모두에게_패배_0회인_강한_봇을_통과시킨다() {
        List<Long> seedsStrongBotClears = LongStream.rangeClosed(1, 50)
                .filter(seed -> seed != 13 && seed != 21)
                .boxed()
                .toList();

        StrongBot bot = new StrongBot();
        GateContext ctx = new GateContext(bot, bot.getClass(), 30, 30, seedsStrongBotClears);

        GateResult result = new RegressionGate().check(ctx);

        assertTrue(result.passed(), "강한 봇이 G7에서 막혔다: " + result.detail());
    }
}
