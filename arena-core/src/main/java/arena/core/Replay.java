package arena.core;

/**
 * 한 경기의 완전한 기록.
 *
 * moves는 턴당 2문자(먼저 봇0, 다음 봇1)이며 사망을 초래한 마지막
 * 이동까지 포함한다. 187턴 경기가 374바이트라, 리플레이를 선별하지
 * 않고 전부 남길 수 있다.
 *
 * metrics는 진단이 필요한 경기에만 채운다. null일 수 있다.
 */
public record Replay(
        int schema,
        String matchId,
        int width,
        int height,
        long seed,
        boolean swapped,
        String bot0Id, Point start0, Direction dir0,
        String bot1Id, Point start1, Direction dir1,
        String moves,
        MatchResult result,
        String hash
) {
    public static final int SCHEMA = 1;

    /** metrics를 붙인 사본. Replay 자체는 metrics를 모른다 (core는 진단에 의존하지 않는다). */
    public Replay withMatchId(String newId) {
        return new Replay(schema, newId, width, height, seed, swapped,
                bot0Id, start0, dir0, bot1Id, start1, dir1, moves, result, hash);
    }

    /** 턴 t(1-based)에서 봇 i가 낸 방향. */
    public Direction moveAt(int turn, int botIndex) {
        return Direction.fromCode(moves.charAt((turn - 1) * 2 + botIndex));
    }
}
