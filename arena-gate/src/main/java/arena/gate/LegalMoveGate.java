package arena.gate;

import arena.core.Direction;
import arena.core.GameView;

import java.util.List;

/**
 * G4 — 어떤 국면에서도 유효한 방향을 예외 없이 반환해야 한다.
 *
 * "죽지 않는 수"를 요구하는 게 아니다. 자멸은 봇의 자유이며 지표로만
 * 남긴다. 여기서 보는 것은 오직 계약 준수다.
 */
public final class LegalMoveGate implements Gate {

    private final List<GameView> positions;

    public LegalMoveGate(List<GameView> positions) {
        this.positions = positions;
    }

    @Override
    public String id() { return "G4"; }

    @Override
    public GateResult check(GateContext ctx) {
        for (int i = 0; i < positions.size(); i++) {
            GameView view = positions.get(i);
            try {
                Direction d = ctx.bot().move(view);
                if (d == null) {
                    return GateResult.fail(id(),
                            "국면 " + i + "에서 null을 반환했다\n" + describe(view));
                }
            } catch (RuntimeException | StackOverflowError e) {
                return GateResult.fail(id(),
                        "국면 " + i + "에서 " + e.getClass().getSimpleName()
                                + (e.getMessage() == null ? "" : ": " + e.getMessage())
                                + "\n" + describe(view));
            }
        }
        return GateResult.pass(id());
    }

    /** 에이전트가 그대로 재현할 수 있도록 반례 국면을 적는다. */
    private static String describe(GameView v) {
        return "  turn=" + v.turn()
                + " myHead=" + v.myHead() + " myDir=" + v.myDir()
                + " oppHead=" + v.oppHead() + " oppDir=" + v.oppDir();
    }
}
