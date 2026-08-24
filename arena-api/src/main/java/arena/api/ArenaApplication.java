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
 * 이 코드는 프로세스 종료 코드로도 나가고, 표준 출력의 마지막 줄
 * {@code ARENA_EXIT_CODE=<n>}으로도 나간다 — 문서화된 호출 경로인
 * {@code ./gradlew gate}가 종료 코드를 뭉개기 때문이다. 자세한 사정은
 * {@link #EXIT_CODE_LINE_PREFIX}에 있다.
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

    /**
     * 기계가 읽는 종료 코드 줄의 접두사. 이 줄은 표준 출력의 마지막
     * 줄로 정확히 한 번 나가며 {@code ARENA_EXIT_CODE=<0|1|2|3>} 형태다.
     *
     * <p>존재 이유는 프로세스 종료 코드가 문서화된 호출 경로에서
     * 살아남지 못하기 때문이다. 규칙서 §8이 에이전트에게 시키는 명령은
     * {@code ./gradlew gate -Pbot=Gen07Bot}인데, Gradle의 {@code JavaExec}은
     * 기본값이 {@code ignoreExitValue = false}라 0이 아닌 코드를 전부
     * 빌드 실패(=Gradle 종료 코드 1)로 뭉갠다. 그래서 1·2·3이 호출자에게
     * 똑같이 1로 보였다 — 이 클래스가 애써 나눠 놓은 "봇이 거부됐다"와
     * "하네스 자체가 깨졌다"의 구분이 인터페이스에서 사라진 것이다.
     * 코드를 표준 출력의 한 줄로도 내보내면 그 구분이 어떤 래퍼를
     * 거치든 살아남는다.
     *
     * <p>일부러 순수 ASCII {@code KEY=VALUE}로 만들었다. Gradle이 띄우는
     * 자식 JVM의 {@code System.out}은 UTF-8이 아닌 인코딩으로 떨어질 수
     * 있어(실측: 이 CLI의 한국어 메시지가 {@code ./gradlew gate} 출력에서
     * {@code ??}로 깨져 나온다) 한국어를 섞으면 기계 판독이 인코딩에
     * 의존하게 된다. 이 줄만은 어떤 인코딩에서도 바이트가 같다.
     *
     * <p>파싱하는 쪽은 마지막 일치를 취하면 된다:
     * {@code CODE=$(./gradlew gate -Pbot=Gen07Bot | grep -o 'ARENA_EXIT_CODE=[0-3]' | tail -1 | cut -d= -f2)}
     */
    public static final String EXIT_CODE_LINE_PREFIX = "ARENA_EXIT_CODE=";

    public static void main(String[] args) {
        System.exit(run(args));
    }

    /**
     * 프로덕션 진입점. {@code System.exit}을 부르지 않는다 — {@code main}만
     * 이 반환값으로 종료하고, 테스트는 프로세스를 죽이지 않고 이
     * 메서드를 안전하게 직접 부를 수 있다.
     *
     * 판정이 끝난 뒤 {@link #EXIT_CODE_LINE_PREFIX} 줄을 찍는 자리가
     * 여기인 이유가 둘이다. ① {@code main}에 두면 프로세스를 띄우지 않고는
     * 검증할 수 없다 — 여기 두면 단위 테스트가 표준 출력을 가로채
     * "코드가 실제로 그 줄에 실린다"를 증명할 수 있다. ② Gradle의
     * {@code doLast}에서 찍게 하면 {@code ./gradlew} 경로에서만 나온다 —
     * 애플리케이션이 직접 찍으면 {@code java -cp …} 직접 호출, 래퍼
     * 스크립트, 앞으로 생길 어떤 호출 경로에서도 똑같이 나온다. 판정
     * 로직을 담은 2-인자 오버로드는 이 줄을 찍지 않는다 — 그쪽은
     * 테스트가 코드 매핑만 보려고 부르는 시야이고, 출력 계약은
     * 프로덕션 진입점 하나에만 붙어 있어야 두 번 찍힐 여지가 없다.
     */
    static int run(String[] args) {
        return emitExitCode(run(args, BotRegistry::validateRegistration));
    }

    /**
     * 종료 코드를 기계가 읽는 줄로 찍고 그대로 돌려준다.
     *
     * 따로 뽑아 둔 이유는 시험 가능성이다. {@link #run(String[])}로는
     * 코드 2(호출 오류)만 값싸게 만들어 볼 수 있고 0·1은 관문 전체를,
     * 3은 하네스를 실제로 부숴야 나온다 — 그러면 "네 코드가 전부 이
     * 줄에 제대로 실리는가"를 증명할 길이 없다. 이 자리가 열려 있으면
     * 네 코드를 직접 먹여 렌더링을 확인하고, 배선(진짜 판정 결과가
     * 여기까지 온다는 것)은 {@link #run(String[])}를 통과시키는 테스트가
     * 따로 증명한다.
     */
    static int emitExitCode(int code) {
        System.out.println(EXIT_CODE_LINE_PREFIX + code);
        return code;
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
