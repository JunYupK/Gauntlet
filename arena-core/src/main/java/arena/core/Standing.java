package arena.core;

import java.util.List;

/**
 * 한 봇의 시리즈 성적.
 *
 * 좌석 교대 경기에서는 winner 인덱스가 좌석 기준이므로,
 * 봇 이름으로 귀속을 판단해야 한다.
 */
public record Standing(int wins, int draws, int losses, double scoreRate) {

    public static Standing of(List<Replay> replays, String subjectId) {
        int wins = 0, draws = 0, losses = 0;

        for (Replay r : replays) {
            int mySeat = seatOf(r, subjectId);

            if (r.result().isDraw()) {
                draws++;
            } else if (r.result().winner() == mySeat) {
                wins++;
            } else {
                losses++;
            }
        }

        int total = wins + draws + losses;
        double rate = total == 0 ? 0.0 : (wins + 0.5 * draws) / total;
        return new Standing(wins, draws, losses, rate);
    }

    /**
     * subjectId가 이 리플레이에서 앉은 좌석(0 또는 1)을 명시적으로 판정한다.
     *
     * {@code bot0Id().equals(subjectId) ? 0 : 1}처럼 "0번이 아니면 무조건
     * 1번"으로 넘겨짚으면, subjectId가 실제로는 이 리플레이에 등장조차
     * 하지 않는 경우(오타, 또는 봇 이름이 상대 이름과 우연히 같아서
     * bot0Id==bot1Id가 돼버린 경우)에도 조용히 0번으로 판정해버린다 —
     * 좌석 교대(swapped) 절반에서는 그게 승패를 통째로 뒤집는다. 그래서
     * bot0Id와도 bot1Id와도 맞지 않으면 조용히 넘어가지 않고 던진다.
     */
    public static int seatOf(Replay r, String subjectId) {
        if (r.bot0Id().equals(subjectId)) return 0;
        if (r.bot1Id().equals(subjectId)) return 1;
        throw new IllegalArgumentException(
                "subjectId가 이 리플레이에 없다: \"" + subjectId
                        + "\" (bot0Id=\"" + r.bot0Id() + "\", bot1Id=\"" + r.bot1Id() + "\")");
    }

    public int total() { return wins + draws + losses; }
}
