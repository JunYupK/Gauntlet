package arena.bots.baseline;

import arena.bots.Bot;
import arena.core.Direction;
import arena.core.GameView;

/**
 * 한 수 앞만 본다. 즉시 죽지 않는 방향 중 고정 우선순위로 고른다.
 *
 * 위 둘을 압도하지만 공간을 못 읽어서 자기 영역에 갇혀 죽는다.
 * G7 회귀 방지의 최상단 기준선이다.
 */
public final class WallAvoidBot implements Bot {

    private static final Direction[] PRIORITY = {
            Direction.RIGHT, Direction.DOWN, Direction.LEFT, Direction.UP
    };

    @Override
    public String name() { return "WallAvoidBot"; }

    @Override
    public Direction move(GameView view) {
        for (Direction d : PRIORITY) {
            if (!view.isDeadly(d)) {
                return d;
            }
        }
        // 사방이 막혔다. 어차피 죽지만 유효한 방향은 내야 한다.
        return view.myDir();
    }
}
