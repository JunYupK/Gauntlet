package arena.bots;

import arena.bots.baseline.RandomBot;
import arena.bots.baseline.StraightBot;
import arena.bots.baseline.WallAvoidBot;
import arena.bots.gen.Gen00Bot;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 이름으로 봇을 찾는다.
 *
 * 새 세대를 추가할 때 GENERATIONS에 한 줄을 더한다. 리플렉션으로
 * 자동 탐색하지 않는 이유는 G3가 봇의 리플렉션 사용을 금지하는데
 * 하네스만 예외로 두면 규칙이 흐려지기 때문이다. 명시적인 목록이
 * 무엇이 챔피언 계보에 속하는지도 분명히 한다.
 *
 * {@link #byName(String)}은 이 클래스에 미리 등록된 인스턴스와의
 * 정확한 이름 일치만 돌려준다 — CLI가 넘기는 임의의 문자열은 이
 * 목록에 있는 이름 중 하나를 "고를" 뿐, 새 Bot이나 새 이름 문자열을
 * 만들어내지 않는다. 그래서 CLI 인자가 {@link arena.core.ReplayHash}의
 * 정규화 문자열이나 파일 경로에 임의 문자를 실어 나를 길이 없다 —
 * 거기 닿는 값은 항상 이 목록에 박힌 봇 자신의 {@code name()} 리터럴이다.
 *
 * 하지만 그 리터럴 자체가 안전하다는 보장은 어디에도 없었다 — 등록만
 * 하면 무엇이든 들어갔다. {@link #validate}가 등록된 모든 이름에 대해
 * 세 규칙을 강제한다: ① 세대 이름은 {@code Gen\d+Bot} 형식이어야 한다
 * (어기면 {@link #generationNumber}의 파싱이 개별 봇이 아니라
 * {@link #allGenerations} 전체를 무너뜨린다), ② 세대·베이스라인을
 * 통틀어 이름이 겹치면 안 된다(겹치면 {@link #byName}의
 * {@code findFirst()}가 세대 봇으로 베이스라인을 조용히 가려버린다 —
 * 예를 들어 어떤 세대 봇이 스스로를 "StraightBot"이라 부르면
 * {@code --bot=StraightBot}은 그 순간부터 진짜 베이스라인이 아니라
 * 그 세대 봇을 가리키게 된다), ③ 이름에 {@code "|"}가 들어가면 안 된다
 * ({@link arena.core.ReplayHash}의 정규화 문자열이 {@code "|"}로 필드를
 * 가르므로, 그 문자가 이름에 섞이면 필드 경계가 흐려진다 — {@link
 * arena.tournament.BundleBuilder#build}의 gallery 리플레이(G5의
 * DeterminismGate와는 별개로, {@code buildGallery}가 {@code bot.name()}을
 * 직접 {@code Match.play}에 넘기는 경로)와 G5 둘 다 이 위험에 노출된다).
 * 등록 시점에 막으면 "오늘 네 리터럴이 우연히 깨끗하다"가 아니라
 * "이 목록에 들어오는 모든 이름은 규칙을 지킨다"가 된다.
 *
 * 이 검증은 정적 초기화 블록에서 부르지 않는다. JLS 12.4.2에 따라
 * 정적 초기화를 빠져나가는 예외는 무엇이든 {@link ExceptionInInitializerError}
 * (Error의 하위 클래스)로 감싸이고, 같은 JVM에서 그 클래스를 다시
 * 건드리면 이번엔 원래 메시지를 담지 않는 {@link NoClassDefFoundError}가
 * 대신 나온다 — {@code arena-api}의 {@code ArenaApplication}이 판정
 * 거부(1)와 하네스 오류(3)를 가르려고 {@code catch (RuntimeException e)}로
 * 잡는데, 두 Error 서브클래스 다 RuntimeException이 아니라서 못 잡히고
 * JVM 기본 종료 코드(1 — 하필 "판정에 의한 거부"와 같은 값)로 새 나간다.
 * 그래서 {@link #validateRegistration()}을 평범한 정적 메서드로 열어
 * {@code ArenaApplication}이 명령을 처리하기 전에 직접 부르게 했다 —
 * 그러면 던지는 건 순수 {@link IllegalStateException}(RuntimeException)이라
 * 기존 catch가 그대로 3으로 잡고, 이 매핑은 같은 JVM 안에서 몇 번을
 * 다시 불러도 항상 똑같다(정적 초기화처럼 "첫 번째는 이렇고 두 번째부터는
 * 다르다"는 게 없다).
 */
public final class BotRegistry {

    private static final Pattern GENERATION_NAME = Pattern.compile("Gen\\d+Bot");

    private static final List<Bot> GENERATIONS = List.of(
            new Gen00Bot()
            // 세대가 승격될 때마다 여기에 추가한다.
    );

    private static final List<Bot> BASELINES = List.of(
            new StraightBot(), new RandomBot(), new WallAvoidBot());

    private BotRegistry() {}

    /**
     * 지금 등록된 {@code GENERATIONS}·{@code BASELINES}를 검증한다.
     * {@code ArenaApplication}이 명령을 처리하기 전에 매번 부른다 —
     * 정적 초기화가 아니라 평범한 메서드 호출이라, 실행할 때마다
     * 독립적으로 검사하고 독립적으로 실패한다.
     */
    public static void validateRegistration() {
        validate(GENERATIONS, BASELINES);
    }

    /**
     * 등록 규칙 셋을 강제한다. {@link #validateRegistration()}이
     * {@code GENERATIONS}·{@code BASELINES}로 부른다. public인 이유는
     * 임의의 목록으로 규칙 하나씩을 직접 찌르는 테스트뿐 아니라, 이
     * 검증이 {@code ArenaApplication}의 종료 코드 매핑까지 실제로
     * 흘러가는지를 다른 모듈(arena-api)의 테스트가 증명해야 하기
     * 때문이다 — 실제 {@code GENERATIONS}는 항상 유효해서 그 흐름을
     * 재현하려면 합성 목록을 이 메서드에 직접 넣을 길이 있어야 한다.
     */
    public static void validate(List<Bot> generations, List<Bot> baselines) {
        for (Bot g : generations) {
            if (!GENERATION_NAME.matcher(g.name()).matches()) {
                throw new IllegalStateException(
                        "세대 봇 이름이 \"Gen<숫자>Bot\" 형식이 아니다: \"" + g.name() + "\"");
            }
        }

        List<String> allNames = Stream.concat(generations.stream(), baselines.stream())
                .map(Bot::name).toList();

        Set<String> seen = new HashSet<>();
        for (String name : allNames) {
            if (!seen.add(name)) {
                throw new IllegalStateException(
                        "봇 이름이 중복 등록됐다: \"" + name + "\" — byName이 어느 쪽을 돌려줄지"
                                + " 조용히 정해버린다(세대가 베이스라인을 가릴 수 있다)");
            }
        }

        for (String name : allNames) {
            if (name.contains("|")) {
                throw new IllegalStateException(
                        "봇 이름에 '|'가 들어있다: \"" + name + "\" — ReplayHash의 정규화"
                                + " 문자열이 '|'로 필드를 가르므로 경계가 흐려진다");
            }
        }
    }

    public static Bot byName(String name) {
        return all().stream()
                .filter(b -> b.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "그런 봇이 없다: " + name + "\n등록된 봇: "
                                + all().stream().map(Bot::name).toList()));
    }

    /**
     * Gen 번호 오름차순. 갤러리 패널 순서가 이걸 그대로 따른다.
     * 실제 정렬 로직은 {@link #sortByGeneration}에 있다 — 등록된 세대가
     * 하나뿐인 지금은 이 메서드만으로 순서 역전을 재현할 수 없어서,
     * 임의의 목록을 받는 그 메서드를 따로 열어 테스트가 직접 찌른다.
     */
    public static List<Bot> allGenerations() {
        return sortByGeneration(GENERATIONS);
    }

    /**
     * {@code Bot::name} 문자열 그대로 정렬하지 않고 숫자를 뽑아 정렬한다
     * — "GenNNBot"이 두 자리로 고정된 동안은 문자열 정렬과 숫자 정렬이
     * 우연히 일치하지만, 언젠가 Gen100Bot이 나오면 문자열 정렬은
     * "Gen100Bot"을 "Gen20Bot"보다 앞에 놓아버린다("1" < "2"). 숫자를
     * 파싱해 비교하면 자릿수가 늘어나도 순서가 흔들리지 않는다.
     */
    static List<Bot> sortByGeneration(List<Bot> bots) {
        return bots.stream()
                .sorted(Comparator.comparingInt(BotRegistry::generationNumber))
                .toList();
    }

    /** "Gen07Bot" → 7. {@link #validate}가 이 형식을 등록 시점에 이미 강제한다. */
    private static int generationNumber(Bot bot) {
        String name = bot.name();
        return Integer.parseInt(name.substring(3, name.length() - 3));
    }

    /**
     * 숫자가 가장 큰(=가장 최신인) 세대. {@code allGenerations().get(size-1)}로
     * 쓰지 않는다 — 그러면 이 메서드가 {@link #sortByGeneration}의 오름차순
     * 정렬이 맞다는 걸 전제로 삼게 되어, "최신 세대"라는 계약을 정렬
     * 구현에 간접적으로 의존시킨다. 대신 숫자 최댓값을 직접 뽑아 두
     * 계약을 서로 독립적으로 유지한다(그리고 독립적으로 테스트할 수
     * 있게 한다).
     */
    public static Bot latestGeneration() {
        return highestGeneration(GENERATIONS);
    }

    static Bot highestGeneration(List<Bot> generations) {
        return generations.stream()
                .max(Comparator.comparingInt(BotRegistry::generationNumber))
                .orElseThrow(() -> new IllegalStateException("등록된 세대 봇이 없다"));
    }

    /**
     * 도전자 세대보다 번호가 낮은 최고 세대 = 이번 챔피언. {@code GENERATIONS}로
     * 부른다. {@code latestGeneration()}(=가장 높은 등록 세대)을 쓰지 않는
     * 이유는, 도전자 {@code GenN}이 붙으려면 그 자신이 등록돼 있어야 하고
     * 그러면 {@code latestGeneration()}도 {@code GenN}이 되어 도전자==챔피언이
     * 되기 때문이다. 실제 증분 루프에선 {@code GenN}이 {@code GenN-1}과
     * 붙어야 한다.
     */
    public static Bot championFor(Bot challenger) {
        return championFor(challenger, GENERATIONS);
    }

    static Bot championFor(Bot challenger, List<Bot> generations) {
        int challengerGen = generationNumber(challenger);
        return generations.stream()
                .filter(b -> generationNumber(b) < challengerGen)
                .max(Comparator.comparingInt(BotRegistry::generationNumber))
                .orElseThrow(() -> new IllegalArgumentException(
                        "챔피언이 없다 — " + challenger.name() + "보다 낮은 세대가 등록돼 있지 않다"));
    }

    public static List<Bot> baselines() {
        return BASELINES;
    }

    private static List<Bot> all() {
        return Stream.concat(GENERATIONS.stream(), BASELINES.stream()).toList();
    }
}
