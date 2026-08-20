package arena.tournament;

import arena.bots.Bot;
import arena.bots.baseline.WallAvoidBot;
import arena.bots.gen.Gen00Bot;
import arena.core.Direction;
import arena.core.GameView;
import arena.core.Standing;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.LongStream;
import static org.junit.jupiter.api.Assertions.*;

class ChampionshipTest {

    private static final List<Long> JUDGING = LongStream.rangeClosed(1, 50).boxed().toList();
    private static final List<Long> HOLDOUT = LongStream.rangeClosed(1001, 1050).boxed().toList();

    private ChallengeReport judge(Bot challenger, Bot champion) {
        return Championship.judge(challenger, champion, JUDGING, HOLDOUT, 30, 30);
    }

    @Test
    void 승격_기준은_60퍼센트다() {
        assertEquals(0.60, Championship.PROMOTION_THRESHOLD, 1e-9);
    }

    @Test
    void 벽회피봇은_직진봇_챔피언을_압도해_승격한다() {
        ChallengeReport r = judge(new WallAvoidBot(), new Gen00Bot());

        assertTrue(r.promoted(), "승점 승률 " + r.scoreRate() + "로 승격에 실패했다");
        assertTrue(r.scoreRate() >= 0.60);
        assertEquals(100, r.wins() + r.draws() + r.losses(), "심사는 100경기여야 한다");
    }

    @Test
    void 같은_봇끼리_붙으면_승격하지_못한다() {
        ChallengeReport r = judge(new SlightlyDifferentStraight(), new Gen00Bot());

        assertFalse(r.promoted(), "직진봇과 사실상 같은데 승격했다");
    }

    @Test
    void 반려되면_진단이_붙는다() {
        ChallengeReport r = judge(new SlightlyDifferentStraight(), new Gen00Bot());

        assertFalse(r.promoted());
        assertFalse(r.diagnosis().isEmpty(), "반려됐는데 진단이 비어 있다");
        assertTrue(r.diagnosis().get(0).loss() >= 0);
    }

    @Test
    void 승격하면_홀드아웃_승률이_함께_기록된다() {
        ChallengeReport r = judge(new WallAvoidBot(), new Gen00Bot());

        assertTrue(r.promoted());
        assertTrue(r.holdoutScoreRate() > 0.0, "홀드아웃 승률이 기록되지 않았다");
    }

    @Test
    void 반려되면_홀드아웃은_돌리지_않는다() {
        ChallengeReport r = judge(new SlightlyDifferentStraight(), new Gen00Bot());

        assertFalse(r.promoted());
        assertEquals(Double.NaN, r.holdoutScoreRate(),
                "반려된 봇에 홀드아웃 시드를 낭비했다");
    }

    @Test
    void 같은_인자로_두_번_판정하면_같은_결과가_나온다() {
        ChallengeReport a = judge(new WallAvoidBot(), new Gen00Bot());
        ChallengeReport b = judge(new WallAvoidBot(), new Gen00Bot());

        assertEquals(a.scoreRate(), b.scoreRate(), 1e-12);
        assertEquals(a.wins(), b.wins());
    }

    /**
     * 도전자와 챔피언의 name()이 완전히 같아도(악의적 제출이든, 우연이든)
     * 판정이 죽지 않아야 한다 — Standing.seatOf가 이름 불일치 시 던지는
     * IllegalArgumentException을 Championship이 그대로 흘리면, 이름이
     * 충돌하는 제출 하나가 하네스 전체를 무너뜨릴 수 있다(BRIEF §11
     * 결정 1). Championship은 SeriesRunner·Standing에 bot.name()을
     * 직접 넘기지 않고 내부 예약 id를 쓰므로 이름이 같아도 정상적으로
     * 판정이 나와야 한다.
     */
    @Test
    void 도전자와_챔피언_이름이_같아도_판정이_죽지_않는다() {
        Bot challenger = new NamedStraight("SameName");
        Bot champion = new NamedStraight("SameName");

        ChallengeReport r = assertDoesNotThrow(() -> judge(challenger, champion));

        assertEquals("SameName", r.challenger());
        assertEquals("SameName", r.champion());
        assertEquals(100, r.wins() + r.draws() + r.losses());
    }

    /** 같은 봇 인스턴스를 도전자·챔피언 양쪽에 넘겨도 판정이 죽지 않는다. */
    @Test
    void 같은_인스턴스로_붙어도_판정이_죽지_않는다() {
        Bot bot = new Gen00Bot();

        ChallengeReport r = assertDoesNotThrow(() -> judge(bot, bot));

        assertEquals(100, r.wins() + r.draws() + r.losses());
    }

    /**
     * 승점 승률이 정확히 0.60이면 승격해야 한다 — 기준은 "60% 이상"이지
     * "60% 초과"가 아니다. 실제 대국에서 정확히 0.60이 나오도록 봇을
     * 조작하는 건 취약하므로, 판정 로직(Championship.meetsThreshold)을
     * 직접 경계값으로 찔러 본다.
     */
    @Test
    void 승점_승률이_정확히_60퍼센트면_승격한다() {
        Standing exact = new Standing(60, 0, 40, 0.60);
        assertTrue(Championship.meetsThreshold(exact));
    }

    @Test
    void 승점_승률이_60퍼센트에_살짝_못미치면_승격하지_않는다() {
        Standing justBelow = new Standing(59, 1, 40, 0.595);
        assertFalse(Championship.meetsThreshold(justBelow));
    }

    /**
     * 직진봇과 전략이 완전히 같고 이름만 다른 봇. 승격 기준을 넘지 못해야
     * 한다.
     *
     * (참고) 애초에 브리프가 준 초안은 "격자 밖으로 나갈 때만 한 번
     * 꺾는다"는 조건을 넣어 뒀었는데, 실측해 보니 그 한 번의 회피만으로
     * Gen00Bot(직진봇) 상대 승점 승률이 0.89까지 올라갔다 — 벽에 그대로
     * 박아 죽는 것과 한 번이라도 피하는 것 사이의 격차가 이만큼 크다는
     * 뜻이다. "이름만 다르고 전략은 사실상 같다"는 주석의 의도를 실제로
     * 담으려면 회피 로직을 빼고 완전히 동일한 전략이어야 한다 — 그래야
     * 좌석 교대로 평균이 0.5로 수렴해(실측: wins=50 draws=0 losses=50,
     * scoreRate=0.5) 60% 문턱을 넉넉히 밑도는 안정적인 회귀 픽스처가
     * 된다.
     */
    static final class SlightlyDifferentStraight implements Bot {
        public String name() { return "SlightlyDifferentStraight"; }
        public Direction move(GameView view) {
            return view.myDir();
        }
    }

    /** 이름을 생성자로 지정할 수 있는 직진봇. 이름 충돌 시나리오 전용. */
    static final class NamedStraight implements Bot {
        private final String name;
        NamedStraight(String name) { this.name = name; }
        public String name() { return name; }
        public Direction move(GameView view) { return view.myDir(); }
    }
}
