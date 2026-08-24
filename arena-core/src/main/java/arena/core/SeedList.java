package arena.core;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 시드 목록의 유효성 규칙. 정의는 여기 하나뿐이다.
 *
 * 시드 목록은 하네스 곳곳에서 인자로 흘러다닌다 —
 * {@link SeriesRunner#run}, {@code Championship.judge}(심사·홀드아웃),
 * {@code BundleBuilder}(심사·라운드로빈). 규칙이 필요한 자리마다 조건문을
 * 다시 적으면 사본이 갈라진다(같은 사고를 F1에서 이미 겪었다 — 벽 규칙의
 * 네 번째 사본). 그래서 규칙을 이 클래스에 한 번만 적고 모든 경계가
 * 이걸 부른다.
 *
 * <p><b>왜 빈 목록을 거부하는가.</b> 빈 시드로 시리즈를 돌리면 경기가
 * 0판이고, 그 0판에서 평균을 내는 소비자는 {@code 0/0 = NaN}을 얻는다.
 * 실제로 {@code BundleBuilder.buildStats}가 그 NaN을 조용히
 * {@code generations.json}에 써 넣고 있었다 — 화면은 빈 차트를 그리고,
 * 보는 사람은 "이 세대는 원래 성적이 없다"와 "호출자가 시드를 빠뜨렸다"를
 * 구분할 수 없다. 조용한 NaN보다 시끄러운 예외가 낫다.
 *
 * <p><b>왜 중복을 거부하는가.</b> 같은 시드가 두 번 들어오면 완전히 같은
 * 경기가 두 번 치러지고 승률·평균에 두 번 계산된다 — 결정론이라 결과가
 * 정확히 같기 때문에 그 중복이 통계를 조용히 편향시킨다. 승격 판정
 * (승점 승률 60%)이 걸린 자리에서 이건 실력이 아니라 인자 실수가 판정을
 * 바꾸는 길이다.
 */
public final class SeedList {

    private SeedList() {}

    /**
     * 시드 목록이 null이거나 비었거나 중복을 담고 있으면
     * {@link IllegalArgumentException}을 던진다.
     *
     * @param seeds 검사할 목록
     * @param name  오류 메시지에 실을 인자 이름 — 시드 목록을 둘 이상
     *              받는 호출자({@code judgingSeeds}/{@code holdoutSeeds})에서
     *              어느 쪽이 잘못됐는지 바로 알 수 있어야 한다
     */
    public static void validate(List<Long> seeds, String name) {
        if (seeds == null || seeds.isEmpty()) {
            throw new IllegalArgumentException(name + "가 비어 있다 — 시드 목록은 필수다");
        }

        Set<Long> seen = new HashSet<>();
        for (Long seed : seeds) {
            if (seed == null) {
                throw new IllegalArgumentException(name + "에 null 시드가 있다");
            }
            if (!seen.add(seed)) {
                throw new IllegalArgumentException(
                        name + "에 중복된 시드가 있다: " + seed + " (" + seeds + ")");
            }
        }
    }
}
