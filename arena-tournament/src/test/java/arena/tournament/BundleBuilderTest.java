package arena.tournament;

import arena.bots.Bot;
import arena.bots.baseline.RandomBot;
import arena.bots.baseline.WallAvoidBot;
import arena.bots.gen.Gen00Bot;
import arena.core.Direction;
import arena.core.GameView;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.*;

class BundleBuilderTest {

    private static final List<Long> SEEDS = LongStream.rangeClosed(1, 10).boxed().toList();

    private void build(Path out, Path records) {
        List<Bot> generations = List.of(new Gen00Bot(), new RandomBot(), new WallAvoidBot());
        BundleBuilder.build(generations, new WallAvoidBot(), 1L, SEEDS, SEEDS, 30, 30,
                new RecordStore(records), out);
    }

    @Test
    void 화면이_읽을_파일_넷을_만든다(@TempDir Path tmp) {
        Path out = tmp.resolve("data");
        build(out, tmp.resolve("records"));

        for (String name : new String[]{
                "gallery.json", "generations.json", "loop-history.json", "roundrobin.json"}) {
            assertTrue(Files.exists(out.resolve(name)), name + "이 없다");
        }
    }

    @Test
    void 갤러리는_세대마다_경기_하나씩_담는다(@TempDir Path tmp) throws Exception {
        Path out = tmp.resolve("data");
        build(out, tmp.resolve("records"));

        String json = Files.readString(out.resolve("gallery.json"));
        assertTrue(json.contains("Gen00Bot"), json.substring(0, Math.min(400, json.length())));
        assertTrue(json.contains("\"moves\""), "리플레이 본문이 없다");
    }

    /**
     * (리뷰 정정) 원래는 {@code json.contains("\"seed\":2")}가 없는지만
     * 봤는데, 이 MAPPER는 {@code INDENT_OUTPUT}을 켠 pretty printer라
     * 필드 구분자가 {@code " : "}다 — 실제 출력은 {@code "seed" : 1}처럼
     * 콜론 양옆에 공백이 들어가므로 {@code "seed":2}라는 부분 문자열은
     * 세대마다 다른 시드를 썼어도 애초에 나올 수 없다. 즉 이 부분
     * 문자열 매칭은 테스트 이름이 막으려는 상황에서도 항상 통과하는
     * 죽은 어서션이었다({@code RecordStoreTest}의 통과하는 테스트들이
     * 이미 공백 있는 형태를 보여준다). 그래서 실제로 {@code seed} 값
     * 자체를 확인한다 — 겸사겸사 갤러리 원소 개수도 세대 수와 일치하는지
     * 고정한다(원래 이 검증이 어디에도 없어서 갤러리가 1개짜리든
     * 6개짜리든 통과하는 공백이 있었다).
     *
     * {@code Replay[]}로 완전히 역직렬화하지 않고 {@link JsonNode}로
     * {@code seed}만 뽑는다 — {@code MatchResult.isDraw()}가 자바빈
     * getter 관례({@code isXxx()})를 따르는 탓에 Jackson이 직렬화할 때
     * 레코드에 없는 파생 필드 {@code "draw"}를 추가로 써 넣고, 그 결과
     * {@code Replay[]}로 완전히 되읽으면 {@code UnrecognizedPropertyException}이
     * 난다(직접 확인함) — 이 테스트가 필요로 하는 건 {@code seed} 값뿐이라
     * 그 무관한 기존 quirk를 우회한다.
     */
    @Test
    void 갤러리의_모든_경기는_같은_시드를_쓴다(@TempDir Path tmp) throws Exception {
        Path out = tmp.resolve("data");
        build(out, tmp.resolve("records"));

        JsonNode gallery = new ObjectMapper().readTree(out.resolve("gallery.json").toFile());

        assertEquals(3, gallery.size(), "세대 수만큼 경기가 담겨야 한다");
        for (JsonNode match : gallery) {
            assertEquals(1L, match.get("seed").asLong(),
                    match.get("matchId").asText() + "의 시드가 갤러리 공통 시드(1)와 다르다 — 패널끼리 비교할 수 없다");
        }
    }

    @Test
    void 세대_지표에_생존턴과_자멸률이_담긴다(@TempDir Path tmp) throws Exception {
        Path out = tmp.resolve("data");
        build(out, tmp.resolve("records"));

        String json = Files.readString(out.resolve("generations.json"));
        assertTrue(json.contains("avgSurvivalTurns"), json);
        assertTrue(json.contains("suicideRate"), json);
    }

    /**
     * 세대 목록에 최종 챔피언과 이름이 같은 세대(WallAvoidBot)가 이미
     * 섞여 있다({@link #build}의 픽스처 자체가 그렇다). bot.name()·
     * champion.name()을 좌석 id로 그대로 쓰는 회귀가 있었다면 이 세대의
     * occupancy·suicideRate·scoreRate가 절반(좌석 교대 경기)에서 조용히
     * 틀렸을 것이다 — 여기서는 최소한 값 자체가 유효 범위 안에 있는지로
     * 그 회귀의 증상(비율이 0~1 범위를 벗어나는 것) 하나를 잡는다.
     */
    @Test
    void 챔피언과_이름이_같은_세대도_지표가_유효_범위_안에_있다(@TempDir Path tmp) throws Exception {
        Path out = tmp.resolve("data");
        build(out, tmp.resolve("records"));

        ObjectMapper mapper = new ObjectMapper();
        GenerationStat[] stats = mapper.readValue(
                Files.readString(out.resolve("generations.json")), GenerationStat[].class);

        assertEquals(3, stats.length);
        for (GenerationStat s : stats) {
            assertTrue(s.occupancy() >= 0.0 && s.occupancy() <= 1.0,
                    s.botName() + "의 occupancy가 범위를 벗어났다: " + s.occupancy());
            assertTrue(s.suicideRate() >= 0.0 && s.suicideRate() <= 1.0,
                    s.botName() + "의 suicideRate가 범위를 벗어났다: " + s.suicideRate());
            assertTrue(s.scoreRate() >= 0.0 && s.scoreRate() <= 1.0,
                    s.botName() + "의 scoreRate가 범위를 벗어났다: " + s.scoreRate());
        }
    }

    /**
     * (리뷰 정정) 위 테스트는 occupancy·suicideRate·scoreRate가 [0,1]
     * 범위 안인지만 봤는데, {@code buildStats}가 좌석을 오귀속해도
     * 여전히 비율이고 여전히 범위 안이라(scoreRate는 좌석 0 승률 근사인
     * 0.5 근처로 무너질 뿐) 그 테스트는 회귀가 있어도 통과한다. 실제로
     * 충돌이 발동하는 조건({@link BundleBuilder#buildStats}의 javadoc
     * 참고: 세대와 챔피언의 이름이 같음)을 비대칭 강도로 재현해야
     * 회귀에 민감해진다 — {@code RoundRobin}의
     * {@code 이름이_같은_두_봇이_섞여도_행렬이_왜곡되지_않는다}와 같은
     * 트릭이다.
     *
     * generations의 유일한 원소는 실제 {@link WallAvoidBot}(강함)이고,
     * finalChampion은 이름은 "WallAvoidBot"이라고 보고하지만 실제로는
     * {@link Gen00Bot}(약함)에게 위임한다. {@code bot.name()}을 좌석
     * id로 쓰는 회귀가 있다면 id0==id1이 되어 좌석 교대 경기 절반에서
     * mySeat이 항상 0으로 오판되고, scoreRate는 seat 0의 승률(대략
     * 0.5 근처)로 무너진다. 정상 판정이면 WallAvoidBot이 Gen00Bot을
     * 압도해 0.8을 훌쩍 넘는다.
     */
    @Test
    void 세대와_챔피언의_이름이_같아도_scoreRate가_뭉개지지_않는다(@TempDir Path tmp) throws Exception {
        Path out = tmp.resolve("data");
        List<Bot> generations = List.of(new WallAvoidBot());
        Bot champion = new NamedBot("WallAvoidBot", new Gen00Bot());

        BundleBuilder.build(generations, champion, 1L, SEEDS, SEEDS, 30, 30,
                new RecordStore(tmp.resolve("records")), out);

        ObjectMapper mapper = new ObjectMapper();
        GenerationStat[] stats = mapper.readValue(
                Files.readString(out.resolve("generations.json")), GenerationStat[].class);

        assertEquals(1, stats.length);
        assertTrue(stats[0].scoreRate() > 0.8,
                "이름이 같다는 이유로 WallAvoidBot의 scoreRate가 뭉개졌다: " + stats[0].scoreRate());
    }

    // --- 재현 가능성: 같은 입력은 바이트 단위로 같은 번들을 낸다 ---

    @Test
    void 같은_입력은_두_번_만들어도_바이트_단위로_동일한_번들을_낸다(@TempDir Path tmp) throws Exception {
        Path out1 = tmp.resolve("data1");
        Path out2 = tmp.resolve("data2");
        build(out1, tmp.resolve("records1"));
        build(out2, tmp.resolve("records2"));

        for (String name : new String[]{
                "gallery.json", "generations.json", "loop-history.json", "roundrobin.json"}) {
            byte[] first = Files.readAllBytes(out1.resolve(name));
            byte[] second = Files.readAllBytes(out2.resolve(name));
            assertArrayEquals(first, second,
                    name + "이 실행마다 다른 바이트를 낸다 — 재현 가능성이 깨졌다");
        }
    }

    // --- NaN 타입 함정: roundrobin.json은 자바스크립트가 읽는 파일이다 ---

    /**
     * (리뷰 정정) 처음엔 Jackson 기본 동작(따옴표 붙은 {@code "NaN"}
     * 문자열, 별도 설정 없이 double로 왕복)이 이 파일에도 그대로
     * 적용된다고 가정했다 — 왕복 자체는 사실이지만, 이 파일의 실제
     * 소비자는 자바로 다시 읽는 코드가 아니라 자바스크립트 프론트엔드다.
     * 숫자 배열 안에 섞인 문자열 {@code "NaN"}은 {@code cell.toFixed(2)}를
     * 던지게 하거나 {@code cell > 0.5}를 조용히 {@code false}로 만드는
     * 타입 함정이다. 그래서 계약을 바꿨다: 대각선은 JSON {@code null}로
     * 나가고, {@code "NaN"} 문자열은 이 파일 어디에도 없어야 한다.
     */
    @Test
    void 라운드로빈_JSON의_대각선은_null이고_비대각선은_숫자다(@TempDir Path tmp) throws Exception {
        Path out = tmp.resolve("data");
        build(out, tmp.resolve("records"));

        Path path = out.resolve("roundrobin.json");
        String json = Files.readString(path);
        assertFalse(json.contains("\"NaN\""),
                "roundrobin.json에 문자열 \"NaN\"이 남아있다 — JS 소비자에게 타입 함정이다: " + json);

        JsonNode matrix = new ObjectMapper().readTree(json).get("matrix");
        int n = matrix.size();
        for (int i = 0; i < n; i++) {
            assertEquals(n, matrix.get(i).size());
            for (int j = 0; j < n; j++) {
                JsonNode cell = matrix.get(i).get(j);
                if (i == j) {
                    assertTrue(cell.isNull(), "대각선 (" + i + "," + i + ")이 null이 아니다: " + cell);
                } else {
                    assertTrue(cell.isNumber(), "(" + i + "," + j + ")이 숫자가 아니다: " + cell);
                }
            }
        }
    }

    // --- roundRobinSeeds는 필수다: 빈 시드로 조용히 빈 행렬을 내보내지 않는다 ---

    @Test
    void roundRobinSeeds가_null이면_거부한다(@TempDir Path tmp) {
        List<Bot> generations = List.of(new Gen00Bot());
        assertThrows(IllegalArgumentException.class, () ->
                BundleBuilder.build(generations, new Gen00Bot(), 1L, SEEDS, null, 30, 30,
                        new RecordStore(tmp.resolve("records")), tmp.resolve("data")));
    }

    @Test
    void roundRobinSeeds가_비어있으면_거부한다(@TempDir Path tmp) {
        List<Bot> generations = List.of(new Gen00Bot());
        assertThrows(IllegalArgumentException.class, () ->
                BundleBuilder.build(generations, new Gen00Bot(), 1L, SEEDS, List.of(), 30, 30,
                        new RecordStore(tmp.resolve("records")), tmp.resolve("data")));
    }

    /** 이름을 생성자로 지정할 수 있는 래퍼. 이름 충돌 시나리오 전용. */
    static final class NamedBot implements Bot {
        private final String name;
        private final Bot delegate;
        NamedBot(String name, Bot delegate) { this.name = name; this.delegate = delegate; }
        public String name() { return name; }
        public Direction move(GameView view) { return delegate.move(view); }
    }
}
