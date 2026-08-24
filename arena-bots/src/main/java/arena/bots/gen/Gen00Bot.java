package arena.bots.gen;

import arena.bots.Bot;
import arena.core.Direction;
import arena.core.GameView;

/**
 * 챔피언 계보의 출발점. 동작은 StraightBot과 같다.
 *
 * Gen 0은 사람이 심는 기준선이며 관문 대상이 아니다. 루프는 Gen 1부터
 * 돈다. 처참하게 약해야 R3의 개선 곡선이 극적으로 나온다.
 */
public final class Gen00Bot implements Bot {

    @Override
    public String name() { return "Gen00Bot"; }

    @Override
    public Direction move(GameView view) {
        return view.myDir();
    }
}
