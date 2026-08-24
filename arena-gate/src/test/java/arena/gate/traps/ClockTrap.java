package arena.gate.traps;

import arena.bots.Bot;
import arena.core.Direction;
import arena.core.GameView;

/** G3 위반: 시계를 읽는다. */
public final class ClockTrap implements Bot {

    @Override
    public String name() { return "ClockTrap"; }

    @Override
    public Direction move(GameView view) {
        long t = System.nanoTime();   // ← G3가 잡아야 하는 것
        return Direction.values()[(int) Math.floorMod(t, 4)];
    }
}
