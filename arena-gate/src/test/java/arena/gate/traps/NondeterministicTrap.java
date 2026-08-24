package arena.gate.traps;

import arena.bots.Bot;
import arena.core.Direction;
import arena.core.GameView;

/**
 * G5 위반: 객체 아이덴티티 해시에 의존한다.
 * 금지 API를 쓰지 않으므로 G3는 통과한다. 결정론 검사만이 이걸 잡는다.
 */
public final class NondeterministicTrap implements Bot {

    @Override
    public String name() { return "NondeterministicTrap"; }

    @Override
    public Direction move(GameView view) {
        int h = new Object().hashCode();   // ← G5가 잡아야 하는 것
        return Direction.values()[Math.floorMod(h, 4)];
    }
}
