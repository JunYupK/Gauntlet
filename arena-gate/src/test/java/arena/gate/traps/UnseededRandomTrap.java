package arena.gate.traps;

import arena.bots.Bot;
import arena.core.Direction;
import arena.core.GameView;

/** G3 위반: 시드 없는 난수. 시드 있는 Random은 허용되므로 무인자 생성자만 잡혀야 한다. */
public final class UnseededRandomTrap implements Bot {

    @Override
    public String name() { return "UnseededRandomTrap"; }

    @Override
    public Direction move(GameView view) {
        return Direction.values()[new java.util.Random().nextInt(4)];   // ← G3
    }
}
