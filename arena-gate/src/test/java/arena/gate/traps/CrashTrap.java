package arena.gate.traps;

import arena.bots.Bot;
import arena.core.Direction;
import arena.core.GameView;

/** G4 위반: 특정 국면에서 배열 범위를 벗어난다. */
public final class CrashTrap implements Bot {

    @Override
    public String name() { return "CrashTrap"; }

    @Override
    public Direction move(GameView view) {
        // 경계 검사를 빠뜨렸다. 머리가 아래 가장자리에 닿는 순간 터진다.
        boolean blocked = view.wall()[view.myHead().y() + 1][view.myHead().x()];
        return blocked ? Direction.LEFT : Direction.RIGHT;
    }
}
