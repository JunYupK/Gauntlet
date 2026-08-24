package arena.bots;

import arena.core.Direction;
import arena.core.GameView;

/**
 * 봇은 무상태 순수 함수다.
 *
 * 구현체는 인스턴스 필드를 가질 수 없다. 이 제약이 "같은 입력 → 같은 출력"을
 * 인터페이스 수준에서 강제하며, G2가 리플렉션으로 기계 판정한다.
 */
public interface Bot {

    String name();

    Direction move(GameView view);
}
