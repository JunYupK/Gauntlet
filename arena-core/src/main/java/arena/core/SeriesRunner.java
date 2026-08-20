package arena.core;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * 시드 목록에 대해 좌석을 교대해가며 경기를 돌린다.
 *
 * 시드마다 2경기를 만든다: 정방향(0번 자리에 bot0, 1번 자리에 bot1)과
 * 좌석 교대(같은 시드의 같은 두 시작 위치에, 앉는 봇만 뒤바꾼 경기).
 * 좌석 교대는 미러링이 아니다 — 0번과 1번의 시작 위치·방향은 시드로부터
 * 각자 독립적으로 뽑히므로(스펙 §2.2) 두 좌석은 대칭이 아니다. 위치는
 * 그대로 두고 그 자리에 앉는 봇만 바꾸는 것이 좌석 교대이며, 이래야
 * 시리즈 전체로 어느 좌석이 유리한지가 상쇄된다. 미러링(격자를 뒤집는
 * 방식)은 격자가 비대칭일 때 그 이점을 상쇄하지 못한다.
 *
 * 교대 경기는 {@link Match#play}를 (id1, bot1, id0, bot0) 순서로 그대로
 * 불러서 만든다 — 그 결과 {@code winner}는 항상 "그 경기에서 실제로
 * 0번 자리에 앉은 쪽이 이겼는가"를 뜻하고, {@code bot0Id}/{@code bot1Id}는
 * 실제로 앉은 좌석과 항상 일치한다. 승자를 봇 이름으로 알고 싶은
 * 소비자는 {@code winner == 0 ? bot0Id : bot1Id}만 보면 되고, 교대
 * 여부를 따로 뒤집어 계산할 필요가 없다.
 *
 * 병렬 실행이 순차 실행과 같은 결과를 내는 이유:
 *   1) 각 경기가 완전히 독립적이다 — 격자·이동 기록은 매 경기마다 새로
 *      만들어지고, 다른 경기와 아무것도 공유하지 않는다.
 *   2) 봇은 무상태 순수 함수다({@link BotFunction}) — 같은 {@code bot0}과
 *      {@code bot1} 인스턴스를 모든 스레드가 동시에 호출해도 서로 간섭할
 *      상태가 없다.
 *   3) 결과는 완료 순서가 아니라 경기 인덱스로 모은다.
 *      {@link ExecutorService#invokeAll}은 넘긴 태스크 리스트와 같은
 *      순서의 {@code Future} 리스트를 돌려주므로(완료 순서가 아니라
 *      제출 순서), 그 순서 그대로 순회하며 결과를 채운다 — 어느 스레드가
 *      먼저 끝나든 반환되는 리스트의 순서는 항상 시드 순서 그대로다.
 *   4) 공유 가변 누산기가 없다 — 각 태스크가 자기 몫의 {@link Replay}를
 *      만들어 돌려줄 뿐, 여러 스레드가 같은 자료구조에 동시에 쓰지 않는다.
 */
public final class SeriesRunner {

    private SeriesRunner() {}

    public static List<Replay> run(
            String id0, BotFunction bot0,
            String id1, BotFunction bot1,
            List<Long> seeds, int width, int height, boolean parallel) {

        int total = seeds.size() * 2;
        List<Callable<Replay>> tasks = new ArrayList<>(total);
        for (int i = 0; i < total; i++) {
            int index = i;
            tasks.add(() -> playOne(id0, bot0, id1, bot1, seeds, width, height, index));
        }

        return parallel ? runParallel(tasks) : runSequential(tasks);
    }

    private static List<Replay> runSequential(List<Callable<Replay>> tasks) {
        List<Replay> results = new ArrayList<>(tasks.size());
        for (Callable<Replay> task : tasks) {
            try {
                results.add(task.call());
            } catch (Exception e) {
                throw new RuntimeException("시리즈 실행 중 경기가 실패했다", e);
            }
        }
        return results;
    }

    /**
     * 대전 자체에는 어떤 시간 제한도 걸지 않는다(성능 판정은 G6의 몫이다).
     * 여기서 도는 스레드 풀도 마찬가지로 타임아웃 없이 끝까지 기다린다.
     *
     * 어느 경기가 예외를 던지면 그 결과를 조용히 빠뜨리지 않고 시리즈
     * 전체를 실패시킨다 — 리플레이 개수가 요청한 것보다 적게 나오는
     * 상황(예: 관문이 "50경기를 다 봤다"고 착각하는 상황)을 원천 차단한다.
     * executor는 정상 종료·예외·인터럽트 등 어떤 경로로 빠져나가든
     * finally에서 반드시 shutdown한다.
     */
    private static List<Replay> runParallel(List<Callable<Replay>> tasks) {
        // 최소 2스레드를 강제한다. availableProcessors()가 1인 머신(CI
        // 컨테이너에서 흔하다)에서 풀 크기가 1이면 모든 태스크가 제출
        // 순서 그대로 한 스레드에서 순차 실행되므로, 병렬·순차 동일성
        // 테스트가 사실상 "순차 대 순차"를 비교하는 셈이 되어 스케줄링
        // 의존성을 전혀 잡아내지 못한 채 항상 GREEN이 나온다.
        int threads = Math.max(2, Runtime.getRuntime().availableProcessors());
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Future<Replay>> futures = executor.invokeAll(tasks);
            List<Replay> results = new ArrayList<>(tasks.size());
            for (Future<Replay> future : futures) {
                results.add(future.get());
            }
            return results;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("시리즈 실행이 중단됐다", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("시리즈 실행 중 경기가 실패했다", e.getCause());
        } finally {
            executor.shutdown();
        }
    }

    private static Replay playOne(
            String id0, BotFunction bot0, String id1, BotFunction bot1,
            List<Long> seeds, int width, int height, int index) {

        long seed = seeds.get(index / 2);
        boolean swapped = (index % 2) == 1;

        Replay replay = swapped
                ? Match.play(id1, bot1, id0, bot0, seed, width, height)
                : Match.play(id0, bot0, id1, bot1, seed, width, height);

        return withSwapFlag(replay, swapped);
    }

    private static Replay withSwapFlag(Replay r, boolean swapped) {
        if (!swapped) {
            return r;
        }
        return new Replay(
                r.schema(), r.matchId() + "-swapped",
                r.width(), r.height(), r.seed(), true,
                r.bot0Id(), r.start0(), r.dir0(),
                r.bot1Id(), r.start1(), r.dir1(),
                r.moves(), r.result(), r.hash());
    }
}
