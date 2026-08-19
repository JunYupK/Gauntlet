package arena.gate;

import arena.bots.Bot;
import java.util.stream.LongStream;

/** 테스트에서 GateContext를 짧게 만들기 위한 헬퍼. */
final class GateContextFixture {

    private GateContextFixture() {}

    static GateContext of(Bot bot) {
        return new GateContext(bot, bot.getClass(), 30, 30,
                LongStream.rangeClosed(1, 50).boxed().toList());
    }
}
