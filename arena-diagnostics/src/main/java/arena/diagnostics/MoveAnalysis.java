package arena.diagnostics;

import arena.core.Direction;

/**
 * 한 수에 대한 판정.
 *
 * loss = 최선 대안의 reach − 실제 선택의 reach.
 * 실제 선택도 대안 후보에 포함되므로 loss는 항상 0 이상이다.
 *
 * {@code reachAfterChosen}·{@code loss}는 이 수가 치명적이었어도 값이
 * 바뀌지 않는다 — 네 방향 후보 모두 "그 방향으로 갔다면"이라는 같은
 * 반사실(counterfactual) 위에서 계산되고, 실제로 무슨 일이 일어났는지는
 * (자기 벽이든 상대 벽이든 정면 충돌이든) 그 대칭을 깨지 않는다. 예를
 * 들어 정면 충돌로 죽은 수는 {@code reachAfterChosen}이 양수로 나올 수
 * 있다 — 그 칸 자체는(상대가 같은 칸에 동시 진입하지만 않았다면) 안전한
 * 칸이었고, 그 이후에 얼마나 넓게 갈 수 있었는지를 재기 때문이다. 이
 * 값이 실제 생사와 어긋나 보이는 지점은 {@link #fatal}이 대신 메운다.
 *
 * @param fatal 엔진이 실제로 이 수를 사망으로 판정했는가. 재구성 루프가
 *              이미 계산해 둔 {@code dead0}/{@code dead1}을 그대로 옮긴
 *              것이라 자기 벽·상대 벽·격자 밖·정면 충돌을 전부 포함한다
 *              — 반사실이 필요 없다(리플레이가 상대의 실제 수를 담고
 *              있고, 엔진이 이미 그 수를 사망으로 판정했으므로). 반면
 *              {@link #suicide}는 정면 충돌을 포함하지 않는다: 정면
 *              충돌은 상대의 이번 턴 동시 선택에 달려 있고, 스펙 §2.1상
 *              어떤 봇도 그 선택을 미리 볼 수 없으므로 "자멸"로 돌리면
 *              스펙이 정의한 자멸률을 부풀리게 된다. {@code fatal}과
 *              {@code suicide}가 갈리는 경우가 바로 정면 충돌이다:
 *              {@code fatal=true}이면서 {@code suicide=false}일 수 있다.
 *              {@link LossAnalyzer#worstMoves}는 이 플래그를 손실보다
 *              먼저 정렬 키로 써서, 실제로 경기를 끝낸 수가 손실 상위
 *              목록에서 밀려나지 않게 한다.
 */
public record MoveAnalysis(
        int turn,
        Direction chose,
        Direction best,
        int reachAfterChosen,
        int reachAfterBest,
        int loss,
        boolean suicide,
        boolean fatal
) {}
