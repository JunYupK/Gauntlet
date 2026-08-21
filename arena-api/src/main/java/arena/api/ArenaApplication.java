package arena.api;

import arena.api.cli.ChallengeCommand;
import arena.api.cli.GateCommand;
import arena.api.cli.RecordCommand;
import arena.bots.BotRegistry;

/**
 * CLI 진입점.
 *
 * Spring Boot 애플리케이션이지만 하네스를 돌릴 때는 컨텍스트를
 * 띄우지 않는다. 엔진과 관문은 Spring을 모르고, 그래야 테스트가
 * 밀리초로 끝난다.
 *
 * 종료 코드 넷을 구분해서 쓴다 — 호출자(에이전트든 스크립트든)가
 * "봇이 거부됐다"와 "하네스 자체가 깨졌다"를 종료 코드만 보고
 * 갈라볼 수 있어야 한다:
 *   0  성공 — 관문 통과 / 챔피언 승격 / 재현 검증 통과
 *   1  판정에 의한 거부 — 관문 반려 / 승격 실패 / 재현 검증 실패(R1 위반)
 *   2  호출 오류 — 인자 누락, 알 수 없는 명령, 등록되지 않은 봇 이름,
 *      도전자==챔피언 같은 "애초에 실행되지 않은" 경우
 *   3  하네스 오류 — 위 세 경우 어디에도 속하지 않는 처리되지 않은
 *      예외. 코드 1과 절대 겹치지 않아야 "관문이 반려했다"와
 *      "관문 실행기 자체가 죽었다"를 구분할 수 있다. GateRunner는
 *      개별 관문의 예외를 이미 실패로 바꿔 삼키므로(GateRunner
 *      javadoc 참고) 여기까지 새어 나오는 예외는 대개 RecordStore의
 *      파일 I/O 실패 같은 진짜 하네스 결함이다.
 *
 * {@link BotRegistry#validateRegistration()}을 명령을 처리하기 전에
 * 매번 부른다 — 등록된 봇 이름이 규칙(형식·중복·"|")을 어기면 그
 * 자체가 하네스 결함(3)이지 판정 거부(1)가 아니다. 정적 초기화
 * 블록으로 이 검증을 걸지 않는 이유는 {@link BotRegistry}의 클래스
 * javadoc에 있다 — 요약하면, 정적 초기화가 던지는 예외는 JLS
 * 12.4.2에 따라 {@link ExceptionInInitializerError}로 감싸이고
 * ({@code RuntimeException}이 아니라 {@code Error}라 아래 catch가
 * 못 잡는다), 같은 JVM에서 재시도하면 원래 메시지도 없는
 * {@link NoClassDefFoundError}가 대신 나온다. 평범한 메서드 호출로
 * 바꾸면 매번 순수 {@link IllegalStateException}이라 아래 catch가
 * 항상 똑같이 3으로 잡는다.
 */
public final class ArenaApplication {

    private static final String USAGE = """
            사용법:
              gate <BotName>        관문 G2~G7
              challenge <BotName>   챔피언전 100경기
              record [--verify]     발표 번들 생성 / 재현 검증
            """;

    public static void main(String[] args) {
        System.exit(run(args));
    }

    /**
     * 실제 판정 로직. {@code System.exit}을 부르지 않는다 — {@code main}만
     * 이 반환값으로 종료하고, 테스트는 프로세스를 죽이지 않고 이
     * 메서드를 안전하게 직접 부를 수 있다.
     */
    static int run(String[] args) {
        return run(args, BotRegistry::validateRegistration);
    }

    /**
     * {@code registrationCheck}를 주입할 수 있는 시야. 프로덕션 경로
     * ({@link #run(String[])})는 언제나 {@link BotRegistry#validateRegistration}을
     * 그대로 써서 이 오버로드로 위임한다.
     *
     * 존재 이유는 순전히 테스트다: 실제 {@code BotRegistry}의 등록
     * 목록은 항상 유효해서(그리고 {@code private static final}이라
     * 테스트가 오염시킬 수도 없어서) "등록 검증이 실패하면 이 클래스의
     * catch가 그걸 종료 코드 3으로 정확히 매핑하는가"를 프로덕션
     * 경로만으로는 실측할 수 없다. 이 오버로드로 실패하는 검사를
     * 주입하면, 실제 {@code main}이 쓰는 것과 완전히 같은 try/catch를
     * 그대로 통과시켜 그 매핑을 증명할 수 있다.
     */
    static int run(String[] args, Runnable registrationCheck) {
        if (args.length == 0) {
            System.out.println(USAGE);
            return 2;
        }

        try {
            registrationCheck.run();

            return switch (args[0]) {
                case "gate"      -> GateCommand.run(requireArg(args, "gate <BotName>"));
                case "challenge" -> ChallengeCommand.run(requireArg(args, "challenge <BotName>"));
                case "record"    -> RecordCommand.run(args.length > 1 && args[1].equals("--verify"));
                default -> {
                    System.out.println("알 수 없는 명령: " + args[0]);
                    yield 2;
                }
            };
        } catch (UsageError e) {
            System.out.println(e.getMessage());
            return 2;
        } catch (RuntimeException e) {
            System.err.println("하네스 오류 — 봇의 잘못이 아니다: " + e);
            e.printStackTrace();
            return 3;
        }
    }

    private static String requireArg(String[] args, String usage) {
        if (args.length < 2) {
            throw new UsageError("봇 이름이 필요하다: " + usage);
        }
        return args[1];
    }

    /**
     * 인자 누락 같은 호출 오류(종료 코드 2)를 나타낸다. {@code System.exit}을
     * {@link #requireArg}에서 직접 부르지 않는 이유이기도 하다 — 직접
     * 불렀다면 {@link #run(String[], Runnable)}을 테스트가 호출할 때마다
     * 인자가 부족한 경로에서 테스트 JVM 자체가 죽어버린다. 예외로
     * 표현하면 {@link #run(String[], Runnable)}의 catch가 다른
     * {@code RuntimeException}(하네스 오류, 3)과 구분해 2로 매핑한다.
     */
    private static final class UsageError extends RuntimeException {
        UsageError(String message) { super(message); }
    }
}
