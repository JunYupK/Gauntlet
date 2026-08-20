package arena.tournament;

import arena.bots.Bot;
import arena.bots.baseline.WallAvoidBot;
import arena.bots.gen.Gen00Bot;
import arena.core.DeathReason;
import arena.core.Direction;
import arena.core.GameView;
import arena.core.MatchResult;
import arena.core.Point;
import arena.core.Replay;
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
        assertFalse(r.diagnosis().isEmpty(), "반려됐는데 진단이 비어 있다");
    }

    @Test
    void 반려되면_진단이_붙는다() {
        ChallengeReport r = judge(new SlightlyDifferentStraight(), new Gen00Bot());

        assertFalse(r.promoted());
        assertFalse(r.diagnosis().isEmpty(), "반려됐는데 진단이 비어 있다");
        assertTrue(r.diagnosis().get(0).loss() >= 0);
    }

    /**
     * 패배가 하나도 없는 반려에서도 진단이 비면 안 된다.
     *
     * 900턴 상한이나 정면 충돌은 무승부로 끝난다. 초기 세대가 "죽지는
     * 않지만 이기지도 못하는" 성격이면(승 0~19, 패 0, 나머지 무승부)
     * 승점 승률은 0.60 밑인데 패배 경기가 하나도 없다 — {@code lost}만
     * 보던 원래 구현은 이런 반려에 빈 진단을 돌려줬다(다섯 번뿐인
     * 재시도 하나를 단서 없이 날림).
     *
     * 실제 대국으로 "패배 0, 무승부 존재"를 재현 가능하게 만들려면 100
     * 경기 전부에서 정면 충돌만 나도록 두 봇을 정교하게 설계해야 하는데,
     * 그건 그 자체로 취약한 픽스처다. 대신 diagnose()를 패키지 전용으로
     * 열어 두고, 손으로 만든 1턴짜리 정면 충돌 Replay(패배 0, 무승부 1)를
     * 직접 넘겨 무승부 대체 경로(DRAW 폴백)를 찌른다.
     */
    @Test
    void 패배없이_무승부만_있어도_진단이_비지_않는다() {
        // width=5,height=5인 손수 만든 리플레이 한 판. start0=(2,2)에서
        // RIGHT, start1=(4,2)에서 LEFT로 한 걸음씩 가면 둘 다 (3,2)를
        // 동시에 노려 1턴 만에 정면 충돌한다(패배가 아니라 무승부).
        Point start0 = new Point(2, 2);
        Point start1 = new Point(4, 2);
        MatchResult drawResult = new MatchResult(-1, 1, DeathReason.HEAD_ON_COLLISION);
        Replay headOn = new Replay(
                Replay.SCHEMA, "hand-built-draw", 5, 5, 1L, false,
                "subject", start0, Direction.RIGHT,
                "rival", start1, Direction.LEFT,
                "RL", drawResult, "sha256:test");

        List<DiagnosisEntry> diagnosis = Championship.diagnose(List.of(headOn), "subject");

        assertFalse(diagnosis.isEmpty(),
                "패배 없는(무승부만 있는) 반려인데도 진단이 비어 있다");
        assertEquals(1L, diagnosis.get(0).seed());
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
     * 판정이 죽지 않고, 좌석 귀속도 왜곡되지 않아야 한다.
     *
     * (정정) {@code Standing.seatOf}는 {@code bot0Id}를 먼저 검사하므로,
     * 이름이 충돌해도 예외를 던지지 않는다 — 조용히 좌석 0으로 판정할
     * 뿐이다. 즉 진짜 위험은 크래시가 아니라 **조용한 증거 반전**이다:
     * {@code bot.name()}을 좌석 id로 그대로 쓰는 회귀가 있다면, 시드마다
     * 정방향·교대 두 경기가 "1승 1패"로 갈리는 대신(아래 참고) 좌석 0이
     * 이긴 쪽으로 두 경기 다 몰린다 — 어느 좌석이 이기느냐는 시드마다
     * 다르므로 100경기 전체로는 0.5 근처로 몰리되 더는 정확히 0.5가
     * 아니게 된다(실측: 되돌린 코드로 이 테스트를 돌리면 0.48이 나온다 —
     * 아래 D55 되돌리기 증거 참고). 어느 쪽으로도 치우칠 수 있는 값이라
     * "일반적으로 위로 벗어난다"고 단정할 순 없지만, 두 이름이 우연이
     * 아니라 챔피언 이름을 그대로 베낀 악의적 제출이라면 이 왜곡이
     * 승격 쪽으로 몰릴 위험은 그대로 남는다. 그래서
     * {@code assertDoesNotThrow}만으로는 부족하다 — 이 회귀가 나면
     * 경기 수·이름 필드는 멀쩡한 채로 죽지 않고 조용히 지나가므로,
     * scoreRate·promoted까지 정확한 값으로 고정해야 실제로 이 회귀를
     * 잡는다.
     *
     * 두 봇의 전략(직진봇)이 완전히 같으므로 0.5는 우연이 아니라
     * 구조적으로 강제된다: 같은 시드의 교대 경기는 같은 궤적을 좌석만
     * 바꿔 재생하고, 좌석이 이기고 지는 건 전략이 아니라 시작 배치가
     * 정하므로, 정방향·교대 각각에서 좌석 0이 항상 이긴다 — 시드마다
     * 정확히 1승 1패가 나와 100경기 전체가 정확히 50승 50패가 된다.
     */
    @Test
    void 도전자와_챔피언_이름이_같아도_판정이_죽지_않는다() {
        Bot challenger = new NamedStraight("SameName");
        Bot champion = new NamedStraight("SameName");

        ChallengeReport r = assertDoesNotThrow(() -> judge(challenger, champion));

        assertEquals("SameName", r.challenger());
        assertEquals("SameName", r.champion());
        assertEquals(100, r.wins() + r.draws() + r.losses());
        assertEquals(0.5, r.scoreRate(), 1e-12,
                "이름 충돌로 좌석 귀속이 왜곡됐다 — 정상이라면 동일 전략끼리는 정확히 0.5여야 한다");
        assertFalse(r.promoted(), "이름이 같다는 이유만으로 클론이 승격해선 안 된다");
    }

    /**
     * 같은 봇 인스턴스를 도전자·챔피언 양쪽에 넘겨도 판정이 죽지 않고,
     * 좌석 귀속도 왜곡되지 않는다. 위 테스트와 같은 근거로 scoreRate·
     * promoted까지 고정한다 — assertDoesNotThrow만으로는 조용한 증거
     * 반전을 못 잡는다.
     */
    @Test
    void 같은_인스턴스로_붙어도_판정이_죽지_않는다() {
        Bot bot = new Gen00Bot();

        ChallengeReport r = assertDoesNotThrow(() -> judge(bot, bot));

        assertEquals(100, r.wins() + r.draws() + r.losses());
        assertEquals(0.5, r.scoreRate(), 1e-12,
                "이름 충돌로 좌석 귀속이 왜곡됐다 — 정상이라면 동일 전략끼리는 정확히 0.5여야 한다");
        assertFalse(r.promoted());
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
