package arena.api;

import java.util.List;
import java.util.stream.LongStream;

/**
 * 시드 집합.
 *
 * 심사와 홀드아웃을 범위로 갈라 두어 겹칠 여지를 없앴다.
 * 홀드아웃은 에이전트에게 노출하지 않는다 — 두 승률의 격차가
 * 시드 과적합의 정도다.
 *
 * 네 집합의 값(1‥50, 1001‥1050, 1‥10, 1)은 스펙의 전역 제약과
 * 정확히 일치해야 하는 유일한 출처다. 다른 어디에서도(하네스든
 * 테스트든) 이 값을 문자 그대로 다시 적지 않는다 — 값이 하나뿐인
 * 출처에서 벗어나면 드리프트를 아무도 못 잡는다.
 */
public final class Seeds {

    public static final List<Long> JUDGING = range(1, 50);
    public static final List<Long> HOLDOUT = range(1001, 1050);
    public static final List<Long> ROUND_ROBIN = range(1, 10);

    /** 갤러리는 전 세대가 같은 시드를 써야 패널끼리 비교된다. */
    public static final long GALLERY = 1L;

    public static final int WIDTH = 30;
    public static final int HEIGHT = 30;

    private Seeds() {}

    private static List<Long> range(long from, long to) {
        return LongStream.rangeClosed(from, to).boxed().toList();
    }
}
