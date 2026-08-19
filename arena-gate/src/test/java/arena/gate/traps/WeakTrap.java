package arena.gate.traps;

import arena.bots.Bot;
import arena.core.Direction;
import arena.core.GameView;

/** G7 위반: 위생 관문은 모두 통과하지만 벽회피봇에게 진다. */
public final class WeakTrap implements Bot {

    @Override
    public String name() { return "WeakTrap"; }

    @Override
    public Direction move(GameView view) {
        return view.myDir();   // 직진만 한다
    }
}
