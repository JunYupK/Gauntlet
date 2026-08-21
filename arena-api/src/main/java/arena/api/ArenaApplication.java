package arena.api;

import arena.api.cli.ChallengeCommand;
import arena.api.cli.GateCommand;
import arena.api.cli.RecordCommand;

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
 */
public final class ArenaApplication {

    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("""
                    사용법:
                      gate <BotName>        관문 G2~G7
                      challenge <BotName>   챔피언전 100경기
                      record [--verify]     발표 번들 생성 / 재현 검증
                    """);
            System.exit(2);
        }

        int code;
        try {
            code = switch (args[0]) {
                case "gate"      -> GateCommand.run(requireArg(args, "gate <BotName>"));
                case "challenge" -> ChallengeCommand.run(requireArg(args, "challenge <BotName>"));
                case "record"    -> RecordCommand.run(args.length > 1 && args[1].equals("--verify"));
                default -> {
                    System.out.println("알 수 없는 명령: " + args[0]);
                    yield 2;
                }
            };
        } catch (RuntimeException e) {
            System.err.println("하네스 오류 — 봇의 잘못이 아니다: " + e);
            e.printStackTrace();
            code = 3;
        }
        System.exit(code);
    }

    private static String requireArg(String[] args, String usage) {
        if (args.length < 2) {
            System.out.println("봇 이름이 필요하다: " + usage);
            System.exit(2);
        }
        return args[1];
    }
}
