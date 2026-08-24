package arena.api;

import arena.bots.BotRegistry;
import arena.bots.baseline.WallAvoidBot;
import arena.gate.GateContext;
import arena.gate.GateReport;
import arena.gate.GateResult;
import arena.gate.GateRunner;
import arena.tournament.ChallengeReport;
import arena.tournament.Championship;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 하네스 전체가 이어져 도는지 확인한다.
 * 개별 단위는 각 모듈의 테스트가 지킨다.
 *
 * 여기 나오는 수치는 전부 프로덕션 상수를 직접 읽어 비교한다 — 이
 * 파일에 리터럴로 다시 적은 숫자와 그 상수를 비교하는 게 아니라,
 * 상수 자체를 스펙 값과 비교한다. 누군가 상수를 고치면 이 파일을
 * 고치지 않아도 실패해야 한다.
 */
class HarnessSmokeTest {

    /**
     * 프로덕션 진입점 {@link GateRunner#run(GateContext)}을 표본 크기·p99
     * 상한 둘 다 그대로(오버로드 주입 없이) 실제 심사 시드로 돌린다.
     * WallAvoidBot은 한 수 앞만 보는 O(1) 판단이라 국면 10,000개를
     * 먹여도 순식간이다 — 표본을 줄여도 실제로 아낄 시간이 거의
     * 없으므로, 축소판 오버로드를 쓰지 않고 실제 하네스가 도전자를
     * 심사할 때와 같은 경로를 그대로 탄다(G6도 실제 P99_LIMIT_MILLIS로
     * 채점된다).
     *
     * <p>이 테스트가 {@code report.passed()}를 요구하지 않는 이유는
     * 브리프 초안이 가정했던 것과 달리 코드로 실제 실행해보니 거짓으로
     * 드러났기 때문이다 — 자체 검토에서 발견해 여기 남긴다. G7({@link
     * arena.gate.RegressionGate})은 도전자를 베이스라인 3종과 붙이는데,
     * 그 중 하나가 WallAvoidBot 자신이다. 완전히 같은 결정론적 전략끼리
     * 겨루면 좌석 교대(스펙 §2.2)의 대칭성 때문에 승 수와 패 수가
     * 항상 정확히 같아진다 — 실측 22승 22패 56무. 무승부가 전부가
     * 아닌 한 패배가 반드시 나오므로 "패배 0회"인 G7을 절대 못
     * 넘는다. 베이스라인은 동결이라 이 성질을 바꿀 수 없고, 실제
     * 세대 루프의 도전자가 베이스라인 자신과 완전히 같은 코드일 일도
     * 없으므로 실전에서는 마주치지 않는다 — 이 경계는 계약 관문이
     * 실제로 도는지 프로덕션 봇 하나로 확인하려는 이 스모크 테스트
     * 자체가 만든 특수 상황이다. 그래서 여기서는 G2~G6(계약 관문)이
     * 전부 통과하고, 딱 G7에서(그것도 정확히 이 이유로) 막히는지를
     * 확인한다 — G2~G6 중 어디서든 막히면 그건 프로덕션 경로 배선이
     * 실제로 깨졌다는 뜻이라 즉시 실패해야 한다.
     */
    @Test
    void 벽회피봇은_계약_관문_G2부터_G6까지_통과하고_G7에서만_자기_자신과의_대칭성으로_막힌다() {
        var bot = new WallAvoidBot();
        GateReport report = GateRunner.run(new GateContext(
                bot, bot.getClass(), Seeds.WIDTH, Seeds.HEIGHT, Seeds.JUDGING));

        for (GateResult r : report.results()) {
            if (!r.gateId().equals("G7")) {
                assertTrue(r.passed(), r.gateId() + "가 반려됐다: " + r.detail());
            }
        }
        assertEquals(6, report.results().size(), "G2~G7 여섯 관문이 모두 기록돼야 한다");
        assertEquals("G7", report.failedGate(),
                "G7이 아닌 다른 곳에서 막혔다면 그건 이 테스트가 예상한 자기 자신과의 대칭성이 아니다: "
                        + report.failedGate() + " — " + report.detail());
    }

    /**
     * 관문 통과 → 챔피언전 → 승격 판정까지 하네스의 핵심 경로가 끝까지
     * 이어지는지 확인한다. Gen00Bot(직진만 하는 사람 심은 기준선)을
     * WallAvoidBot이 실제로 교체할 수 있어야 루프가 성립한다.
     */
    @Test
    void 관문을_통과한_봇이_Gen0_챔피언을_교체한다() {
        ChallengeReport report = Championship.judge(
                new WallAvoidBot(), BotRegistry.byName("Gen00Bot"),
                Seeds.JUDGING, Seeds.HOLDOUT, Seeds.WIDTH, Seeds.HEIGHT);

        assertTrue(report.promoted(),
                "승점 승률 " + report.scoreRate() + "로 승격에 실패했다");
    }

    @Test
    void 심사와_홀드아웃_시드는_겹치지_않는다() {
        assertTrue(Seeds.JUDGING.stream().noneMatch(Seeds.HOLDOUT::contains));
    }

    /**
     * D8·D9의 전역 제약 값이 프로덕션 상수와 일치하는지 고정한다.
     * 값 하나하나가 arena-gate·arena-tournament·arena-api 세 모듈에
     * 흩어진 단일 출처를 직접 가리킨다 — 이 테스트에서 리터럴로 다시
     * 적는 건 "스펙이 요구하는 값" 그 자체뿐이고, 비교 대상은 언제나
     * 프로덕션 상수다.
     */
    @Test
    void 승격_기준과_시간_상한이_스펙과_일치한다() {
        assertEquals(0.60, Championship.PROMOTION_THRESHOLD, 1e-9);
        assertEquals(5.0, GateRunner.P99_LIMIT_MILLIS, 1e-9);
        assertEquals(10_000, GateRunner.SAMPLE_SIZE);
        assertEquals(100, Seeds.JUDGING.size() * 2, "심사는 100경기여야 한다");
    }

    /**
     * 심사·홀드아웃·라운드로빈·갤러리 시드 범위가 스펙(1‥50, 1001‥1050,
     * 1‥10, 1)과 정확히 일치하는지 고정한다. {@code Seeds}가 이 값들의
     * 유일한 출처이므로, 이 테스트는 그 출처를 스펙 값과 대조하는
     * 마지막 지점이다.
     */
    @Test
    void 시드_범위가_스펙과_일치한다() {
        assertEquals(50, Seeds.JUDGING.size());
        assertEquals(1L, Seeds.JUDGING.get(0));
        assertEquals(50L, Seeds.JUDGING.get(Seeds.JUDGING.size() - 1));

        assertEquals(50, Seeds.HOLDOUT.size());
        assertEquals(1001L, Seeds.HOLDOUT.get(0));
        assertEquals(1050L, Seeds.HOLDOUT.get(Seeds.HOLDOUT.size() - 1));

        assertEquals(10, Seeds.ROUND_ROBIN.size());
        assertEquals(1L, Seeds.ROUND_ROBIN.get(0));
        assertEquals(10L, Seeds.ROUND_ROBIN.get(Seeds.ROUND_ROBIN.size() - 1));

        assertEquals(1L, Seeds.GALLERY);
    }

    /**
     * 격자 30×30과 최대 턴 900의 관계를 코드가 실제로 표현하는 방식
     * 그대로 고정한다. {@code Match}는 별도의 900 상수를 두지 않고
     * {@code maxTurns = width * height}로 매 경기 계산한다(스펙 §2.1) —
     * 그러니 900은 독립된 상수가 아니라 격자 크기의 결과다. 여기서
     * 검증할 수 있는 건 "격자 크기의 단일 출처인 {@code Seeds.WIDTH}·
     * {@code Seeds.HEIGHT}가 30×30이고, 그 곱이 스펙이 말하는 900과
     * 같다"는 것뿐이다 — {@code Match}에 900을 리터럴로 다시 적어
     * 비교하면 그 자체가 두 번째 출처가 되어버린다.
     */
    @Test
    void 격자_30x30이_최대턴_900을_결정한다() {
        assertEquals(30, Seeds.WIDTH);
        assertEquals(30, Seeds.HEIGHT);
        assertEquals(900, Seeds.WIDTH * Seeds.HEIGHT,
                "격자 크기의 곱이 스펙의 최대 턴(900)과 어긋난다");
    }
}
