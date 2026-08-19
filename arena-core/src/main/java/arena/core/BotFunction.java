package arena.core;

/**
 * 엔진이 보는 봇. arena-core는 arena-bots에 의존할 수 없으므로
 * (의존은 단방향) 인터페이스가 아니라 함수를 받는다.
 */
@FunctionalInterface
public interface BotFunction {
    Direction move(GameView view);
}
