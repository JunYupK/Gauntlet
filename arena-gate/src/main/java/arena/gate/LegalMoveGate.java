package arena.gate;

import arena.core.Direction;
import arena.core.GameView;

import java.util.List;

/**
 * G4 — 어떤 국면에서도 유효한 방향을 예외 없이 반환해야 한다.
 *
 * "죽지 않는 수"를 요구하는 게 아니다. 자멸은 봇의 자유이며 지표로만
 * 남긴다. 여기서 보는 것은 오직 계약 준수다.
 *
 * {@code positions}는 이 관문 하나의 소유가 아니다 — G4·G5·G6가 같은
 * 리스트를 공유해서 재사용하고, 그 안의 각 국면도 여러 봇에게 반복해서
 * 먹여진다. {@link GameView}는 {@code wall}을 방어적으로 복사하지 않으므로,
 * 원본 국면을 봇에게 그대로 넘기면 그 봇이 {@code wall}을 고쳐 쓰는 순간
 * 리스트 안의 그 국면이 영구히 훼손되고, 이후 같은 리스트로 심사되는
 * 다른 봇이 오염된 국면을 보게 된다. 그래서 매 국면을 먹이기 직전에
 * {@link PositionSampler#copyOf(GameView)}로 복사본을 떠서 넘긴다 — 원본은
 * {@code positions}에 그대로 남는다.
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
            GameView original = positions.get(i);
            // 원본 표본을 훼손으로부터 지키려고 먹이기 직전에 복사한다.
            GameView view = PositionSampler.copyOf(original);
            try {
                Direction d = ctx.bot().move(view);
                if (d == null) {
                    return GateResult.fail(id(),
                            "국면 " + i + "에서 null을 반환했다\n" + describe(original));
                }
            } catch (RuntimeException | StackOverflowError e) {
                return GateResult.fail(id(),
                        "국면 " + i + "에서 " + e.getClass().getSimpleName()
                                + (e.getMessage() == null ? "" : ": " + e.getMessage())
                                + "\n" + describe(original));
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
