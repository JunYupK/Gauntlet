package arena.gate.traps;

import arena.bots.Bot;
import arena.core.Direction;
import arena.core.GameView;

/** G2 위반: 인스턴스 필드를 갖는다. */
public final class StatefulTrap implements Bot {

    private int callCount = 0;   // ← G2가 잡아야 하는 것

    @Override
    public String name() { return "StatefulTrap"; }

    @Override
    public Direction move(GameView view) {
        callCount++;
        return Direction.values()[callCount % 4];
    }
}
