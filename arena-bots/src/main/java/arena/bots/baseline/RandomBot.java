package arena.bots.baseline;

import arena.bots.Bot;
import arena.core.Direction;
import arena.core.GameView;

/**
 * 국면에서 유도한 시드로 방향을 고른다. 후진 자멸로 금방 죽는다.
 *
 * java.util.Random을 무인자로 생성하면 시계에 의존해 R1이 깨진다.
 * 국면을 시드로 삼으면 무작위처럼 보이면서도 완전히 결정론적이다.
 */
public final class RandomBot implements Bot {

    @Override
    public String name() { return "RandomBot"; }

    @Override
    public Direction move(GameView view) {
        int h = 17;
        h = h * 31 + view.myHead().x();
        h = h * 31 + view.myHead().y();
        h = h * 31 + view.turn();
        h = h * 31 + view.myDir().ordinal();
        return Direction.values()[Math.floorMod(h, 4)];
    }
}
