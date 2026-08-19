package arena.bots.baseline;

import arena.bots.Bot;
import arena.core.Direction;
import arena.core.GameView;

/**
 * 항상 가던 방향으로 간다. 벽에 박아 죽는다.
 *
 * 챔피언 계보의 출발점(Gen 0)이기도 하다. 직선 하나가 그려지다 멈추는
 * 그림은 "얘는 아무 생각이 없다"가 설명 없이 전달된다.
 *
 * 베이스라인 봇은 한번 커밋한 뒤 수정하지 않는다. 기준이 움직이면
 * 세대 간 비교가 무의미해진다.
 */
public final class StraightBot implements Bot {

    @Override
    public String name() { return "StraightBot"; }

    @Override
    public Direction move(GameView view) {
        return view.myDir();
    }
}
