package arena.gate;

import arena.bots.Bot;

import java.util.List;
import java.util.stream.LongStream;

/** 테스트에서 GateContext를 짧게 만들기 위한 헬퍼. */
final class GateContextFixture {

    private GateContextFixture() {}

    static GateContext of(Bot bot) {
        return new GateContext(bot, bot.getClass(), 30, 30,
                LongStream.rangeClosed(1, 50).boxed().toList());
    }

    /**
     * 심사 시드를 직접 지정하는 오버로드. 국면 표본 크기와 마찬가지로
     * "어느 관문에서 걸리는가"를 확인하는 테스트가 값비싼 함정 봇의
     * G5 리플레이-해시 겹(judgingSeeds 수만큼 실제 경기를 돈다)·G7
     * 회귀 겹(마찬가지) 비용을 줄이는 용도다 — 프로덕션 심사 시드
     * 1..50은 여기 손대지 않는다.
     */
    static GateContext of(Bot bot, List<Long> seeds) {
        return new GateContext(bot, bot.getClass(), 30, 30, seeds);
    }
}
