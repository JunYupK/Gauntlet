package arena.api.cli;

import arena.bots.Bot;
import arena.core.Match;
import arena.core.Replay;
import arena.tournament.AttemptRecord;
import arena.tournament.GenerationStat;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class FixtureCommandTest {

    private static final int WIDTH = 30;
    private static final int HEIGHT = 30;

    @Test
    void 데모_봇은_깊이가_깊을수록_오래_산다(@TempDir Path tmp) {
        // 깊이 0(즉사만 피함)과 깊이 6을 같은 시드 20개에서 붙여, 깊은 쪽이
        // 평균 생존 턴에서 앞서는지 본다. 이게 거짓이면 데모 번들의
        // 개선 곡선은 우연이고, 화면이 증명하는 것이 아무것도 없다.
        double shallow = averageTurns(FixtureCommand.demoBot(0), 20);
        double deep = averageTurns(FixtureCommand.demoBot(6), 20);

        System.out.println("깊이 0 평균 생존 턴: " + shallow);
        System.out.println("깊이 6 평균 생존 턴: " + deep);

        assertTrue(deep > shallow * 1.5,
                "깊이 6(" + deep + ")이 깊이 0(" + shallow + ")보다 확실히 오래 살아야 한다");
    }

    @Test
    void 데모_번들은_12세대를_담고_스스로_데모라고_밝힌다(@TempDir Path tmp) throws Exception {
        assertEquals(0, FixtureCommand.run(tmp));

        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> meta = mapper.readValue(
                tmp.resolve("meta.json").toFile(), new TypeReference<Map<String, Object>>() {});
        assertEquals(Boolean.TRUE, meta.get("demo"),
                "데모 번들이 스스로를 진짜라고 말하면 안 된다");
        assertEquals(12, meta.get("generations"));

        List<GenerationStat> stats = mapper.readValue(
                tmp.resolve("generations.json").toFile(),
                new TypeReference<List<GenerationStat>>() {});
        assertEquals(12, stats.size());
    }

    @Test
    void 데모_번들의_개선_곡선은_실제로_올라간다(@TempDir Path tmp) throws Exception {
        FixtureCommand.run(tmp);
        List<GenerationStat> stats = new ObjectMapper().readValue(
                tmp.resolve("generations.json").toFile(),
                new TypeReference<List<GenerationStat>>() {});

        // R3의 합격선(스펙 §13)은 Gen 0 대비 10배다. 데모가 그 선을 넘지
        // 못하면 갤러리 화면이 R3을 증명하는 그림을 못 만든다.
        double gen0 = stats.get(0).avgSurvivalTurns();
        double last = stats.get(11).avgSurvivalTurns();

        System.out.println("세대별 평균 생존 턴: "
                + stats.stream().map(GenerationStat::avgSurvivalTurns).toList());
        System.out.println("R3 비율(gen0 -> gen11): " + (last / gen0));

        assertTrue(last >= gen0 * 10,
                "데모 곡선이 R3 합격선(10배)에 못 미친다: " + gen0 + " → " + last);
    }

    @Test
    void 루프_이력에_반려_사유가_여러_종류_들어있다(@TempDir Path tmp) throws Exception {
        FixtureCommand.run(tmp);
        Map<String, List<AttemptRecord>> history = new ObjectMapper().readValue(
                tmp.resolve("loop-history.json").toFile(),
                new TypeReference<Map<String, List<AttemptRecord>>>() {});

        Set<String> gates = history.values().stream()
                .flatMap(List::stream)
                .map(AttemptRecord::failedGate)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        assertTrue(gates.size() >= 3,
                "반려 사유가 " + gates + " 뿐이면 화면 3의 사유별 색을 검증할 수 없다");
    }

    /**
     * (보강) 브리프의 위 테스트는 시도 이력의 사유 "다양성"만 보고,
     * 반려된 시도가 실제로 있는지(즉 REJECTED 시도가 하나라도 있는지)는
     * 확인하지 않는다. 모든 세대가 시도 1회만에 곧장 승격하는 구현이라도
     * PROMOTED 하나짜리 이력이면 gates 집합이 비어(모두 null) 위 테스트가
     * 실패하니 그건 이미 걸리지만, "反려 이력이 세대마다 여러 번
     * 반복된다"는 성질까지는 안 보고 있었다 — 이력이 정말 "1~3회 시도,
     * 일부는 반려"인지, 총 시도 수와 반려 개수 자체로 확인한다.
     */
    @Test
    void 세대마다_시도_이력이_하나_이상이고_반려된_시도가_실제로_존재한다(@TempDir Path tmp) throws Exception {
        FixtureCommand.run(tmp);
        Map<String, List<AttemptRecord>> history = new ObjectMapper().readValue(
                tmp.resolve("loop-history.json").toFile(),
                new TypeReference<Map<String, List<AttemptRecord>>>() {});

        assertEquals(12, history.size());

        long totalAttempts = 0;
        long rejectedAttempts = 0;
        for (List<AttemptRecord> records : history.values()) {
            assertFalse(records.isEmpty(), "세대에 시도 이력이 하나도 없다");
            totalAttempts += records.size();
            rejectedAttempts += records.stream()
                    .filter(r -> r.verdict().equals("REJECTED")).count();
        }

        assertTrue(totalAttempts > 12,
                "모든 세대가 시도 1회만에 끝났다 — \"1~3회 시도\"라는 합성 이력의 전제가 깨졌다: " + totalAttempts);
        assertTrue(rejectedAttempts >= 3,
                "반려된 시도가 거의 없다 — 화면 3의 반려 이력이 빈약하다: " + rejectedAttempts);
    }

    private static double averageTurns(Bot bot, int n) {
        Bot opponent = FixtureCommand.demoBot(0);
        double total = 0;
        for (long seed = 1; seed <= n; seed++) {
            Replay r = Match.play(
                    bot.name(), bot::move,
                    opponent.name(), opponent::move,
                    seed, WIDTH, HEIGHT);
            total += r.result().turns();
        }
        return total / n;
    }
}
