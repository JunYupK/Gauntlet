package arena.gate;

import arena.bots.Bot;
import java.util.List;

public record GateContext(
        Bot bot,
        Class<?> botClass,
        int width,
        int height,
        List<Long> judgingSeeds
) {}
