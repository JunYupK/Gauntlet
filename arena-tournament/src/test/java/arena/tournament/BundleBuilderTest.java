package arena.tournament;

import arena.bots.Bot;
import arena.bots.baseline.RandomBot;
import arena.bots.baseline.WallAvoidBot;
import arena.bots.gen.Gen00Bot;
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
        BundleBuilder.build(generations, new WallAvoidBot(), 1L, SEEDS, 30, 30,
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

    @Test
    void 갤러리의_모든_경기는_같은_시드를_쓴다(@TempDir Path tmp) throws Exception {
        Path out = tmp.resolve("data");
        build(out, tmp.resolve("records"));

        String json = Files.readString(out.resolve("gallery.json"));
        assertFalse(json.contains("\"seed\":2"),
                "세대마다 다른 시드를 쓰면 패널끼리 비교할 수 없다");
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

    // --- NaN: roundrobin.json의 대각선은 NaN이고, 왕복해도 NaN으로 남는다 ---

    @Test
    void 라운드로빈_대각선의_NaN이_왕복된다(@TempDir Path tmp) throws Exception {
        Path out = tmp.resolve("data");
        build(out, tmp.resolve("records"));

        Path path = out.resolve("roundrobin.json");
        String json = Files.readString(path);
        assertTrue(json.contains("\"NaN\""), json);

        ObjectMapper mapper = new ObjectMapper();
        RoundRobinBundle bundle = mapper.readValue(path.toFile(), RoundRobinBundle.class);

        assertEquals(bundle.bots().size(), bundle.matrix().length);
        for (int i = 0; i < bundle.matrix().length; i++) {
            assertTrue(Double.isNaN(bundle.matrix()[i][i]),
                    "대각선이 NaN으로 왕복되지 않았다: " + bundle.matrix()[i][i]);
        }
    }

    private record RoundRobinBundle(List<String> bots, double[][] matrix) {}
}
