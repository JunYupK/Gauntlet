package arena.tournament;

import arena.bots.Bot;
import arena.bots.gen.Gen00Bot;
import arena.core.Direction;
import arena.core.GameView;
import arena.gate.GateReport;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SourceBundleTest {

    @Test
    void 채택된_소스를_세대별_파일로_쓴다(@TempDir Path tmp) throws Exception {
        RecordStore store = new RecordStore(tmp.resolve("records"));
        store.saveGateReport(0, 1, "class Gen00Bot {}",
                new GateReport("Gen00Bot", true, null, "", List.of()));

        Path out = tmp.resolve("data");
        SourceBundle.write(List.of(new Gen00Bot()), store, out);

        assertEquals("class Gen00Bot {}",
                Files.readString(out.resolve("sources/gen-00.java"), StandardCharsets.UTF_8));
    }

    @Test
    void 소스가_없는_세대는_인덱스에_available_false로_남는다(@TempDir Path tmp) throws Exception {
        Path out = tmp.resolve("data");
        SourceBundle.write(List.of(new Gen00Bot()), new RecordStore(tmp.resolve("records")), out);

        List<Map<String, Object>> index = new ObjectMapper().readValue(
                out.resolve("sources/index.json").toFile(),
                new TypeReference<List<Map<String, Object>>>() {});

        assertEquals(1, index.size());
        assertEquals(Boolean.FALSE, index.get(0).get("available"));
        assertFalse(Files.exists(out.resolve("sources/gen-00.java")),
                "소스가 없으면 빈 파일을 만들지 않는다 — 화면이 '빈 코드'와 '기록 없음'을 구분해야 한다");
    }

    @Test
    void 인덱스는_세대_순서를_그대로_따른다(@TempDir Path tmp) throws Exception {
        RecordStore store = new RecordStore(tmp.resolve("records"));
        for (int gen = 0; gen < 3; gen++) {
            store.saveGateReport(gen, 1, "class G" + gen + " {}",
                    new GateReport("Gen0" + gen + "Bot", true, null, "", List.of()));
        }

        Path out = tmp.resolve("data");
        SourceBundle.write(List.of(new Gen00Bot(), new Gen00Bot(), new Gen00Bot()), store, out);

        List<Map<String, Object>> index = new ObjectMapper().readValue(
                out.resolve("sources/index.json").toFile(),
                new TypeReference<List<Map<String, Object>>>() {});

        assertEquals(List.of(0, 1, 2), index.stream().map(e -> e.get("generation")).toList());
    }

    /**
     * (보강) 위 테스트는 세 인자 모두 {@code new Gen00Bot()}이라 botName이
     * 모든 항목에서 "Gen00Bot"으로 같다 — generation 필드가 0,1,2로
     * 다르기만 하면 통과하므로, "세대별로 올바른 봇을 찾아 이름을 쓴다"와
     * "항상 첫 번째(또는 아무) 봇의 이름을 쓴다"를 구분하지 못한다. 세대마다
     * 다른 이름의 봇(래퍼 {@link NamedBot})을 넣어 인덱스가 실제로 그
     * 세대의 봇을 가리키는지 검증한다.
     */
    @Test
    void 인덱스의_botName은_해당_세대의_봇_이름을_따른다(@TempDir Path tmp) throws Exception {
        RecordStore store = new RecordStore(tmp.resolve("records"));
        for (int gen = 0; gen < 3; gen++) {
            store.saveGateReport(gen, 1, "class G" + gen + " {}",
                    new GateReport("이름" + gen, true, null, "", List.of()));
        }

        Path out = tmp.resolve("data");
        SourceBundle.write(
                List.of(new NamedBot("이름0"), new NamedBot("이름1"), new NamedBot("이름2")),
                store, out);

        List<Map<String, Object>> index = new ObjectMapper().readValue(
                out.resolve("sources/index.json").toFile(),
                new TypeReference<List<Map<String, Object>>>() {});

        assertEquals(List.of("이름0", "이름1", "이름2"),
                index.stream().map(e -> e.get("botName")).toList(),
                "botName이 세대마다 다른데도 뭉개졌다 — 세대별 조회가 아니라 항상 같은 봇을 읽고 있다는 뜻이다");
    }

    /** 이름을 생성자로 지정할 수 있는 래퍼. 세대별로 구분되는 봇을 만들기 위한 테스트 전용. */
    static final class NamedBot implements Bot {
        private final String name;
        NamedBot(String name) { this.name = name; }
        public String name() { return name; }
        public Direction move(GameView view) { return Direction.UP; }
    }
}
