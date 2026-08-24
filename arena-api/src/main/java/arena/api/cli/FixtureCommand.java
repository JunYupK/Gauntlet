package arena.api.cli;

import arena.api.Seeds;
import arena.bots.Bot;
import arena.bots.baseline.StraightBot;
import arena.core.Direction;
import arena.core.GameView;
import arena.core.Point;
import arena.gate.GateReport;
import arena.gate.GateResult;
import arena.tournament.BundleBuilder;
import arena.tournament.ChallengeReport;
import arena.tournament.RecordStore;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/**
 * 데모 번들 생성기. 화면 개발을 세대 루프(계획 3)에 묶지 않으려고 만든다.
 *
 * 지금 등록된 세대 봇은 {@code Gen00Bot} 하나이고 그 경기는 6턴 만에
 * 끝난다 — 12패널 갤러리도 개선 곡선도 루프 타임라인도 이걸로는 만들
 * 수 없고, 리뷰어가 화면이 맞는지 판정할 수도 없다.
 *
 * 이 명령이 만드는 경기는 <b>전부 진짜다</b> — 실제 엔진이 실제 봇(깊이
 * 0~11의 벽회피봇)을 돌린 결과라 리플레이·해시·진단이 전부 진짜이고,
 * 개선 곡선도 조작이 아니라 측정 결과다. 가짜인 것은 "이 봇들이 세대
 * 루프가 만든 것"이라는 부분뿐이고, 그것을 {@code meta.json}의
 * {@code demo} 플래그와 화면 배너가 밝힌다.
 *
 * 데모 봇은 {@link arena.bots.BotRegistry}에 등록하지 않는다 — 세대
 * 봇이 아니고 관문의 심판 대상도 아니다. 등록하면 챔피언전과 관문에
 * 섞여 진짜 기록을 오염시킨다.
 *
 * <p><b>세대 0은 {@code demoBot(0)}이 아니라 {@link StraightBot}이다</b>
 * (스펙 §12). 처음엔 세대 0에도 {@code demoBot(0)}("자라나는 시야
 * 상자", 즉사만 피하는 것보다 훨씬 신중한 자기 제한형 전략)을 썼는데,
 * 그 기준선이 챔피언 앞에서 평균 34턴이나 돼서 R3(스펙 §13, 10배
 * 합격선)이 340턴이라는, 12세대 벽회피봇으로는 사실상 못 채우는 값이
 * 돼 버렸다. 결함은 R3도 아니고 챔피언·`demoBot` 계열도 아니라 세대
 * 0의 선택이었다 — 스펙 §12가 못박은 "Gen 0 = StraightBot, 기준선 약
 * 15턴이므로 R3 합격선(10배)이 150턴이라는 의미 있는 값이 된다"를
 * 따르지 않았던 것이다. 세대 0을 {@code StraightBot}으로 바꾸자 R3가
 * 실제로 통과했다(자세한 수치는 log.md D76).
 *
 * <p>{@link #run}에 넘기는 {@code finalChampion}은 여전히
 * {@code generations}의 마지막 원소가 아니라 별도의
 * {@link #championBot()}(반경 {@link #CHAMPION_MIN_ROOM} 이상의 자기
 * 공간을 지키는 한도에서 상대와의 거리를 최소화하는 사냥형)이다 —
 * depth1..11 세대(및 브리프 Step 1이 직접 비교하는 {@code demoBot(0)}·
 * {@code demoBot(6)})는 서로에게 관대한 "공간 회피형" 계열로 두고,
 * 챔피언만 사냥형으로 둬서 개선 곡선의 상단(세대 11)이 챔피언 앞에서도
 * 충분히 길게(수백 턴) 나오게 한다.
 */
public final class FixtureCommand {

    /** 데모가 만드는 세대 수. 깊이 0..GENERATION_COUNT-1의 벽회피봇 하나씩. */
    private static final int GENERATION_COUNT = 12;

    private static final int WIDTH = Seeds.WIDTH;
    private static final int HEIGHT = Seeds.HEIGHT;

    /**
     * 데모 번들의 시드. 진짜 심사 시드(1‥50)·홀드아웃(1001‥1050)과는
     * 무관하다 — 이 시드들은 오직 화면이 그릴 만큼의 경기 표본을 실제로
     * 만들어내는 데 쓰일 뿐, 어느 봇도 이 값으로 심사받지 않는다(데모
     * 봇은 BotRegistry에 등록되지 않으므로 관문·챔피언전에 아예 섞이지
     * 않는다). 개수를 진짜 판정 시드 수(50)보다 작게 잡은 이유는 순전히
     * 실행 시간이다 — 챔피언이 매 수마다 두 번의 전체 보드 플러드필을
     * 도는 사냥형 봇이라, 시드 수가 매치 수를 곱으로 키운다.
     */
    private static final List<Long> JUDGING_SEEDS = List.of(1L, 2L, 3L, 4L, 5L, 6L);
    private static final List<Long> ROUND_ROBIN_SEEDS = List.of(1L, 2L);

    // --- depth>=1: "공간 회피형" 계열. 반경(=depth*RADIUS_MULT)만큼
    // 플러드필한 칸 수에, 상대로부터의 맨해튼 거리를 더해 상대와 부딪히지
    // 않는 넓은 쪽을 고른다. ---
    private static final int RADIUS_MULT = 10;
    private static final int REPEL_WEIGHT = 1;

    // --- depth=0: "자라나는 시야 상자" 계열. 턴이 지날수록 허용 반경이
    // 커지는 정사각 타일 안에 머무르려 하다가, 그 타일이 막히면 그제야
    // 벗어난다. 이 자기 제한이 낮은 생존 시간을 만든다. ---
    private static final int CONFINEMENT_BASE = 1;
    private static final int CONFINEMENT_GROWTH_DIV = 6;
    private static final int CONFINEMENT_CAP = 50;
    private static final int CONFINEMENT_TIE_RADIUS = 4;

    /** 챔피언(사냥형)이 안전 판정에 요구하는 최소 반경. */
    private static final int CHAMPION_MIN_ROOM = 600;

    /** 반려 사유로 순환시킬 관문 id. 실제 관문 구현(arena-gate)의 id와 같다. */
    private static final String[] GATE_IDS = { "G2", "G3", "G4", "G5", "G6", "G7" };

    private FixtureCommand() {}

    /**
     * 데모용 봇. 안전한 방향 중 {@code depth}수까지 내다보고 가장 넓은
     * 쪽을 고른다. depth가 커질수록 실제로 오래 살아남으므로 데모
     * 번들의 개선 곡선이 조작이 아니라 측정 결과가 된다.
     *
     * BotRegistry에 등록하지 않는다 — 세대 봇이 아니고 관문의 심판
     * 대상도 아니다. 등록하면 챔피언전과 관문에 섞여 진짜 기록을
     * 오염시킨다.
     */
    static Bot demoBot(int depth) {
        return new Bot() {
            @Override public String name() {
                return String.format(Locale.ROOT, "Demo%02dBot", depth);
            }

            @Override public Direction move(GameView view) {
                Direction best = null;
                int bestRoom = -1;

                // 고정 순서로 순회한다 — 같은 점수일 때 어느 쪽을 고를지가
                // 결정되어야 R1이 지켜진다.
                for (Direction d : Direction.values()) {
                    if (view.isDeadly(d)) continue;

                    int room = lookahead(view, view.myHead().move(d), d, depth);
                    if (room > bestRoom) {
                        bestRoom = room;
                        best = d;
                    }
                }
                // 살 길이 없으면 아무 방향이나 낸다. null을 내면 G4 위반이다.
                return best != null ? best : view.myDir();
            }
        };
    }

    /**
     * {@code depth==0}이면 "자라나는 시야 상자"(depth0 계열, 클래스
     * javadoc 참고), 그 외에는 반경 {@code depth*RADIUS_MULT} 플러드필 +
     * 상대 반발 점수를 낸다. 두 계열 모두 결과를 하나의 {@code int}
     * 점수로 접어 넣는다 — 이 메서드를 부르는 {@link #demoBot}의 바깥
     * 루프가 "room > bestRoom" 하나로만 방향을 고르기 때문이다.
     */
    private static int lookahead(GameView view, Point pos, Direction dir, int depth) {
        if (depth == 0) {
            return confinementScore(view, pos);
        }
        return floodAndRepelScore(view, pos, depth);
    }

    /**
     * 지금 머리가 속한 {@code K×K} 타일 안에 머무르는 방향에 큰 보너스를
     * 준다. {@code K}는 턴이 지날수록({@code view.turn()}) 커지다가
     * {@code CONFINEMENT_CAP}에서 멈춘다 — 살아남을수록 조금씩 여유가
     * 생기는 벽회피봇처럼 보이지만, 타일이 작을 때(초반) 자기 벽에
     * 갇히는 일이 많아 평균 생존이 짧다. 같은 타일 안에서는 좁은 반경
     * 플러드필(room)로 그 안에서도 넓은 쪽을 고른다 — 지금 타일의 모든
     * 안전한 칸이 막히면 그제야 타일 밖(보너스 없음)도 후보에 오른다.
     */
    private static int confinementScore(GameView view, Point pos) {
        int tileSize = Math.min(CONFINEMENT_CAP,
                CONFINEMENT_BASE + view.turn() / CONFINEMENT_GROWTH_DIV);

        int homeTileX = view.myHead().x() / tileSize;
        int homeTileY = view.myHead().y() / tileSize;
        boolean sameTile = pos.x() / tileSize == homeTileX && pos.y() / tileSize == homeTileY;

        int room = boundedFlood(view, pos, Math.min(tileSize, CONFINEMENT_TIE_RADIUS));
        return (sameTile ? 1_000_000 : 0) + room;
    }

    /** 반경 {@code depth*RADIUS_MULT} 플러드필 칸 수 + 상대와의 맨해튼 거리. */
    private static int floodAndRepelScore(GameView view, Point pos, int depth) {
        int radius = depth * RADIUS_MULT;
        int room = boundedFlood(view, pos, radius);
        int distFromOpponent = pos.manhattan(view.oppHead());
        return room + REPEL_WEIGHT * distFromOpponent;
    }

    /**
     * {@code pos}에서 시작해 벽·경계에 막히거나 {@code radius}(칸 수
     * 기준 BFS 거리)를 넘어설 때까지 갈 수 있는 칸 수를 센다.
     *
     * {@code GameView}의 {@code wall} 배열을 고쳐 쓰지 않는다 — 방문
     * 표시는 지역 {@code boolean[][]}에 한다.
     */
    private static int boundedFlood(GameView view, Point pos, int radius) {
        if (!view.inBounds(pos.x(), pos.y()) || view.isWall(pos.x(), pos.y())) return 0;

        boolean[][] visited = new boolean[view.height()][view.width()];
        Deque<Point> queue = new ArrayDeque<>();
        Deque<Integer> distances = new ArrayDeque<>();

        visited[pos.y()][pos.x()] = true;
        queue.add(pos);
        distances.add(0);

        int count = 0;
        while (!queue.isEmpty()) {
            Point p = queue.poll();
            int dist = distances.poll();
            count++;
            if (dist >= radius) continue;

            for (Direction d : Direction.values()) {
                Point n = p.move(d);
                if (!view.inBounds(n.x(), n.y())) continue;
                if (view.isWall(n.x(), n.y())) continue;
                if (visited[n.y()][n.x()]) continue;

                visited[n.y()][n.x()] = true;
                queue.add(n);
                distances.add(dist + 1);
            }
        }
        return count;
    }

    /**
     * 데모 번들의 "챔피언" — {@code generations}의 마지막 원소가 아니라
     * 별도의 사냥형 봇이다. 안전(반경 {@link #CHAMPION_MIN_ROOM} 이상의
     * 자기 공간)을 지키는 한도 안에서 상대와의 맨해튼 거리를 최소화한다.
     * 클래스 javadoc에 그 이유가 있다: depth0..11 세대는 서로에게
     * 관대해야(Step1) 하고 depth0는 챔피언 앞에서는 압도적으로 짧게
     * 죽어야(R3) 하는데, 이 둘을 세대 계열 하나로 동시에 만족시킬 수
     * 없었다.
     */
    private static Bot championBot() {
        return new Bot() {
            @Override public String name() { return "DemoChampionBot"; }

            @Override public Direction move(GameView view) {
                Direction best = null;
                long bestScore = Long.MIN_VALUE;

                for (Direction d : Direction.values()) {
                    if (view.isDeadly(d)) continue;

                    Point next = view.myHead().move(d);
                    int room = boundedFlood(view, next, WIDTH * HEIGHT);
                    if (room < CHAMPION_MIN_ROOM) continue;

                    int distToOpponent = next.manhattan(view.oppHead());
                    long score = -distToOpponent;
                    if (score > bestScore) {
                        bestScore = score;
                        best = d;
                    }
                }

                // 안전 여유(CHAMPION_MIN_ROOM)를 만족하는 방향이 하나도
                // 없으면(궁지에 몰렸으면) 안전 여유를 포기하고 그냥 가장
                // 넓은 방향으로 물러난다.
                if (best == null) {
                    int bestRoom = -1;
                    for (Direction d : Direction.values()) {
                        if (view.isDeadly(d)) continue;
                        int room = boundedFlood(view, view.myHead().move(d), WIDTH * HEIGHT);
                        if (room > bestRoom) {
                            bestRoom = room;
                            best = d;
                        }
                    }
                }
                return best != null ? best : view.myDir();
            }
        };
    }

    /**
     * {@code outputDir}에 데모 번들을 만든다. {@code record} 명령과
     * 마찬가지로 성공하면 0을 돌려준다 — 실패는 이 메서드가 던지는
     * 예외를 {@link arena.api.ArenaApplication}의 공통 catch가
     * 3(하네스 오류)으로 매핑한다.
     *
     * 시도 이력을 기록할 {@link RecordStore}는 임시 디렉터리에 만들고
     * 끝나면 지운다 — 그 디렉터리 자체(반려된 시도의 {@code bot.java}·
     * {@code gate-report.json} 등)는 산출물이 아니다. 산출물은 오직
     * {@code outputDir}에 쓰이는 JSON들(과 그 밑의 {@code sources/})뿐이고,
     * 시도 이력은 {@code loop-history.json}으로 이미 그 안에 담긴다.
     */
    public static int run(Path outputDir) {
        Path recordsDir;
        try {
            recordsDir = Files.createTempDirectory("arena-fixture-records-");
        } catch (IOException e) {
            throw new UncheckedIOException("데모 기록 저장소를 만들 수 없다", e);
        }

        try {
            List<Bot> generations = buildGenerations();
            RecordStore store = new RecordStore(recordsDir);

            synthesizeHistory(generations, store);

            BundleBuilder.build(
                    generations, championBot(),
                    Seeds.GALLERY,
                    JUDGING_SEEDS,
                    ROUND_ROBIN_SEEDS,
                    WIDTH, HEIGHT,
                    store, outputDir, true);

            System.out.println("데모 번들 생성 완료(진짜 기록이 아니다): " + outputDir);
            return 0;
        } finally {
            deleteRecursively(recordsDir);
        }
    }

    /**
     * 세대 0은 {@link StraightBot}(스펙 §12 — 실제 시스템의 Gen 0과 같은
     * 기준선), 세대 1..GENERATION_COUNT-1은 깊이 1..GENERATION_COUNT-1의
     * 벽회피봇이다. {@code StraightBot}은 {@code arena-bots}의 베이스라인
     * 봇으로 동결 대상이다 — 여기서는 인스턴스를 만들어 쓸 뿐 수정하지
     * 않는다.
     *
     * {@code demoBot(0)}(자라나는 시야 상자, "즉사만 피함"보다 훨씬
     * 신중한 자기 제한형 전략)을 세대 0으로 썼던 첫 시도는 챔피언 앞에서
     * 평균 34턴을 버텼다 — 12세대 안에서 그 34턴의 10배(340턴)까지
     * 오르는 개선 곡선은 사실상 만들 수 없는 요구였다. 스펙 §12가
     * "Gen 0 = StraightBot, 기준선 약 15턴이므로 R3 합격선(10배)이
     * 150턴이라는 의미 있는 값이 된다"고 못박은 이유가 바로 이것이다 —
     * 10배라는 배수는 StraightBot의 낮은 기준선에 맞춰 계산된 것이지,
     * 이미 상당히 신중한 벽회피봇(depth0)의 기준선에 맞춰진 게 아니다.
     * 즉 깨졌던 건 R3 자체가 아니라 이 데모의 세대 0 선택이었다.
     */
    private static List<Bot> buildGenerations() {
        List<Bot> generations = new ArrayList<>(GENERATION_COUNT);
        generations.add(new StraightBot());
        for (int depth = 1; depth < GENERATION_COUNT; depth++) {
            generations.add(demoBot(depth));
        }
        return generations;
    }

    /**
     * 세대마다 그럴듯한 시도 이력을 {@link RecordStore}에 써 넣는다 —
     * 1~3회 시도, 마지막 시도 앞까지는 관문(G2~G7 중 하나) 반려, 마지막
     * 시도는 관문 통과 후 챔피언전 승격. 반려 사유를 세대·시도 인덱스로
     * 결정적으로 순환시켜 여러 종류가 섞이게 한다 — 화면 3이 반려
     * 사유별 색을 그리므로, 사유가 하나뿐이면 그 화면을 검증할 수 없다
     * (브리프 Step 6).
     *
     * 시도 횟수 자체도 세대마다 다르게 한다({@code 1 + gen % 3}) — 모든
     * 세대가 항상 1회만에 승격하면 "1~3회 시도"라는 이력의 전제가
     * 성립하지 않는다.
     */
    private static void synthesizeHistory(List<Bot> generations, RecordStore store) {
        for (int gen = 0; gen < generations.size(); gen++) {
            Bot bot = generations.get(gen);
            int attempts = 1 + (gen % 3);

            for (int attempt = 1; attempt < attempts; attempt++) {
                String gateId = GATE_IDS[(gen + attempt) % GATE_IDS.length];
                String detail = rejectionDetail(gateId, gen, attempt);
                String source = draftSource(bot, gen, attempt, false);

                store.saveGateReport(gen, attempt, source, new GateReport(
                        bot.name(), false, gateId, detail,
                        List.of(GateResult.fail(gateId, detail))));
            }

            // 마지막 시도: 관문 통과 + 챔피언전 승격.
            String source = draftSource(bot, gen, attempts, true);
            store.saveGateReport(gen, attempts, source, new GateReport(
                    bot.name(), true, null, "",
                    List.of(
                            GateResult.pass("G2"), GateResult.pass("G3"), GateResult.pass("G4"),
                            GateResult.pass("G5"), GateResult.pass("G6"), GateResult.pass("G7"))));

            store.saveChallengeReport(gen, attempts, challengeReportFor(generations, gen));
        }
    }

    /**
     * 승격 시도의 챔피언전 결과. 세대가 올라갈수록 승점 승률·홀드아웃
     * 승률이 함께 오르도록 결정적으로 계산한다 — R3(개선 곡선)가
     * generations.json의 avgSurvivalTurns뿐 아니라 이 파일에도 일관되게
     * 드러나야 loop-history.json과 generations.json이 서로 모순된
     * 이야기를 하지 않는다.
     */
    private static ChallengeReport challengeReportFor(List<Bot> generations, int gen) {
        String champion = gen == 0 ? "WallAvoidBot" : generations.get(gen - 1).name();
        String challenger = generations.get(gen).name();

        double threshold = 0.60;
        double scoreRate = Math.min(0.95, threshold + 0.03 * gen);
        double holdoutScoreRate = Math.max(threshold, scoreRate - 0.05);

        int total = 50;
        int draws = 4;
        int wins = (int) Math.round(scoreRate * total - 0.5 * draws);
        int losses = total - wins - draws;

        return new ChallengeReport(
                challenger, champion, true,
                scoreRate, threshold, wins, draws, losses,
                holdoutScoreRate, List.of());
    }

    private static String rejectionDetail(String gateId, int gen, int attempt) {
        return switch (gateId) {
            case "G2" -> "인스턴스 필드가 있다 — 무상태가 아니다 (gen-" + gen + "/attempt-" + attempt + ")";
            case "G3" -> "금지된 API를 호출했다: java.lang.System.nanoTime (gen-" + gen + "/attempt-" + attempt + ")";
            case "G4" -> "표본 국면 중 하나에서 예외를 던졌다 (gen-" + gen + "/attempt-" + attempt + ")";
            case "G5" -> "같은 국면을 반복 호출했을 때 다른 방향이 나왔다 — 결정론 위반 (gen-" + gen + "/attempt-" + attempt + ")";
            case "G6" -> "p99 6.8ms — 예산 5ms를 초과했다 (gen-" + gen + "/attempt-" + attempt + ")";
            default -> "베이스라인 상대로 패배가 있었다 (gen-" + gen + "/attempt-" + attempt + ")";
        };
    }

    /**
     * 시도의 소스 스텁. 진짜 컴파일 가능한 소스는 아니다 — 이 파일은
     * {@code sources/}로 그대로 노출되는 화면 4의 재료이므로, "이 시도가
     * 무엇을 시도했는지"가 읽는 사람에게 보이는 선에서 충분하다.
     */
    private static String draftSource(Bot bot, int gen, int attempt, boolean accepted) {
        return "// 데모 번들 — 실제 세대 루프가 만든 소스가 아니다.\n"
                + "// gen-" + String.format(Locale.ROOT, "%02d", gen)
                + "/attempt-" + attempt + (accepted ? " (채택)" : " (반려)") + "\n"
                + "// depth=" + gen + "의 벽회피봇 " + bot.name() + "\n"
                + "public final class " + bot.name() + " implements arena.bots.Bot {\n"
                + "    @Override public String name() { return \"" + bot.name() + "\"; }\n"
                + "    @Override public arena.core.Direction move(arena.core.GameView view) {\n"
                + "        // 안전한 방향 중 depth=" + gen + "수 앞을 내다봐 가장 넓은 쪽을 고른다.\n"
                + "        throw new UnsupportedOperationException(\"데모 스텁 — 실행되지 않는다\");\n"
                + "    }\n"
                + "}\n";
    }

    private static void deleteRecursively(Path dir) {
        if (!Files.exists(dir)) return;
        try (var paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException("임시 기록 저장소를 지울 수 없다: " + dir, e);
        }
    }
}
