package arena.gate.traps;

import arena.bots.Bot;
import arena.core.Direction;
import arena.core.GameView;

/** G6 위반: 한 수에 수십 밀리초를 쓴다. 결과는 결정론적이라 G5는 통과한다. */
public final class SlowTrap implements Bot {

    @Override
    public String name() { return "SlowTrap"; }

    @Override
    public Direction move(GameView view) {
        long acc = 0;
        for (int i = 0; i < 40_000_000; i++) {
            acc += i % 7;
        }
        // acc를 결과에 반영해 JIT가 루프를 통째로 지워버리지 못하게 한다.
        for (Direction d : Direction.values()) {
            if (!view.isDeadly(d)) return d;
        }
        return Direction.values()[(int) Math.floorMod(acc, 4)];
    }
}
