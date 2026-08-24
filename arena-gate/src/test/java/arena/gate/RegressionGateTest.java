package arena.gate;

import arena.bots.Bot;
import arena.core.Direction;
import arena.core.GameView;
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
 * 이렇게 시드를 줄이는 건 이 클래스의 accept-path 테스트 하나뿐이다.
 * 프로덕션 G7({@link GateRunner})은 언제나 judgingSeeds 1..50 전체를
 * 그대로 쓰고, {@code RegressionGate}의 임계값(패배 0회)도 손대지
 * 않는다 — 여기서 시험하는 건 그 임계값이 진짜 강한 봇을 실제로
 * 통과시키는지이지, 임계값 자체를 낮추는 게 아니다.
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

    /**
     * 리뷰 반려(D52) — {@code RegressionGate}가 예전에는 {@code
     * ctx.bot().name()}을 그대로 시리즈 참가자 id로 넘겼다. 제출된 봇의
     * 이름이 우연히 베이스라인 이름과 같으면 그 매치업에서 {@code
     * bot0Id == bot1Id}가 되어, {@link Standing}이 좌석 교대(swapped)
     * 절반에서 승패를 통째로 뒤집었다 — 이름이 "WallAvoidBot"인 봇은
     * WallAvoidBot 상대 매치업에서 정확히 이 상황에 걸린다.
     *
     * 지금은 {@code RegressionGate}가 베이스라인 이름과 절대 겹치지
     * 않는 예약어("subject")를 항상 넘기므로, 봇 이름이 뭐든 내부 판정에
     * 새어 들어가지 않는다 — 그러므로 이름만 다르고 move() 로직이
     * 완전히 같은 두 봇은 정확히 같은 판정을 받아야 한다. seed 13(교대
     * 방향에서 진짜로 지는 시드)을 포함시켜야 이 비교가 실제로 뭔가를
     * 증명한다 — 이기기만 하는 시드로는 옛날 버그가 있어도 결과가
     * 똑같이 나왔을 것이다.
     */
    @Test
    void 봇_이름이_베이스라인과_같아도_결과가_왜곡되지_않는다() {
        List<Long> seeds = List.of(13L, 21L); // StrongBot이 WallAvoidBot에게 진짜로 지는 시드들

        StrongBot normalDelegate = new StrongBot();
        Bot normalBot = new Bot() {
            @Override public String name() { return "StrongBot"; }
            @Override public Direction move(GameView view) { return normalDelegate.move(view); }
        };
        Bot collidingBot = new Bot() {
            private final StrongBot delegate = new StrongBot();
            @Override public String name() { return "WallAvoidBot"; } // 베이스라인과 이름 충돌
            @Override public Direction move(GameView view) { return delegate.move(view); }
        };

        GateContext normalCtx = new GateContext(normalBot, normalBot.getClass(), 30, 30, seeds);
        GateContext collidingCtx = new GateContext(collidingBot, collidingBot.getClass(), 30, 30, seeds);

        GateResult normalResult = new RegressionGate().check(normalCtx);
        GateResult collidingResult = new RegressionGate().check(collidingCtx);

        assertEquals(normalResult.passed(), collidingResult.passed());
        assertEquals(normalResult.detail(), collidingResult.detail(),
                "이름이 베이스라인과 충돌한다고 승패 귀속이 달라지면 안 된다");
    }
}
