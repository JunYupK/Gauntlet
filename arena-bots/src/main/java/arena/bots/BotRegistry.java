package arena.bots;

import arena.bots.baseline.RandomBot;
import arena.bots.baseline.StraightBot;
import arena.bots.baseline.WallAvoidBot;
import arena.bots.gen.Gen00Bot;

import java.util.Comparator;
import java.util.List;

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
 */
public final class BotRegistry {

    private static final List<Bot> GENERATIONS = List.of(
            new Gen00Bot()
            // 세대가 승격될 때마다 여기에 추가한다.
    );

    private static final List<Bot> BASELINES = List.of(
            new StraightBot(), new RandomBot(), new WallAvoidBot());

    private BotRegistry() {}

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
     *
     * {@code Bot::name} 문자열 그대로 정렬하지 않고 숫자를 뽑아 정렬한다
     * — "GenNNBot"이 두 자리로 고정된 동안은 문자열 정렬과 숫자 정렬이
     * 우연히 일치하지만, 언젠가 Gen100Bot이 나오면 문자열 정렬은
     * "Gen100Bot"을 "Gen20Bot"보다 앞에 놓아버린다("1" < "2"). 숫자를
     * 파싱해 비교하면 자릿수가 늘어나도 순서가 흔들리지 않는다.
     */
    public static List<Bot> allGenerations() {
        return GENERATIONS.stream()
                .sorted(Comparator.comparingInt(BotRegistry::generationNumber))
                .toList();
    }

    /** "Gen07Bot" → 7. GENERATIONS는 이 형식만 담는다는 내부 규약을 전제한다. */
    private static int generationNumber(Bot bot) {
        String name = bot.name();
        return Integer.parseInt(name.substring(3, name.length() - 3));
    }

    public static Bot latestGeneration() {
        List<Bot> generations = allGenerations();
        return generations.get(generations.size() - 1);
    }

    public static List<Bot> baselines() {
        return BASELINES;
    }

    private static List<Bot> all() {
        return java.util.stream.Stream.concat(GENERATIONS.stream(), BASELINES.stream()).toList();
    }
}
