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
            int mySeat = r.bot0Id().equals(subjectId) ? 0 : 1;

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

    public int total() { return wins + draws + losses; }
}
