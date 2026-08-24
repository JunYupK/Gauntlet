# CLAUDE.md — 이 저장소에서 일하는 방법

## 0. 작업 전에 스킬부터 본다

작업을 시작하기 전에 `.claude/skills/`의 스킬 목록을 먼저 확인하고, 해당하는 스킬이 있으면 그 절차를 따른다. 질문을 되묻기 전에도 확인한다.

스킬은 [obra/superpowers](https://github.com/obra/superpowers) v6.3.0을 벤더링한 것이다. **이름에 `superpowers:` 접두사가 없다.** 문서가 `superpowers:executing-plans`라고 적었으면 실제 이름은 `executing-plans`다. 출처와 갱신 절차는 `.claude/README.md`에 있다.

| 상황 | 스킬 |
|---|---|
| 무엇을 만들지 아직 정해지지 않았다 | `brainstorming` |
| 스펙은 있고 계획이 없다 | `writing-plans` |
| 계획을 실행한다 | `subagent-driven-development` (권장) 또는 `executing-plans` |
| 기능·버그를 코드로 짠다 | `test-driven-development` |
| 버그·테스트 실패·이상 동작을 만났다 | `systematic-debugging` |
| 다 됐다고 말하기 직전이다 | `verification-before-completion` |

## 1. 문서 셋의 역할

| 문서 | 역할 |
|---|---|
| `BRIEF.md` | **입력.** 사람이 갖고 들어온 재료다. 설계가 아니다. 이 문서와 충돌하는 제안은 채택하지 않는다 |
| `log.md` | **결정 로그.** 무엇을 왜 그렇게 정했는지 |
| `docs/superpowers/specs/` | 설계 스펙 |
| `docs/superpowers/plans/` | 구현 계획. 태스크·스텝 단위 체크박스 |

계획을 실행할 때는 계획 문서의 **Global Constraints를 모든 태스크의 요구사항으로** 취급한다.

## 2. 기록 규칙

BRIEF §8이 요구하는 발표 재료는 개발 중에 자연히 쌓여야 한다. 나중에 재구성하지 않는다.

- **결정이 나오는 즉시 `log.md`에 append 한다.** 세션이 끝난 뒤 몰아 쓰지 않는다
- **기각한 대안과 그 사유를 지우지 않는다.** 무엇을 고르지 않았는지가 발표의 재료다
- **관문 반려 이력을 지우지 않는다.** 실패 횟수가 보이는 편이 유리하다
- 뒤집힌 판단은 지우지 말고 측정 결과와 함께 다시 쓴다 (사례: D34)

## 3. 판단할 때 지킬 것

BRIEF §11에서 온다. 대화가 대신해주지 않는 부분이다.

1. 요구사항이 나올 때마다 **"그건 어떻게 판정하죠?"** 를 묻는다. 재미·자연스러움·보기 좋음 같은 말이 나오면 반드시 묻는다 (R2)
2. **동시에 일어나는 일의 처리 순서**를 얼버무리지 않는다
3. **재현 가능성을 먼저 요구한다.** 알아서 나오지 않는다 (R1)
4. **관문 기준값은 루프가 통과하지 못한다는 이유로 낮추지 않는다.** 통과 못 하면 봇이 부족한 것이다

## 4. 커밋

- 커밋 메시지는 **한국어**로 쓴다. 무엇을 했는지가 아니라 **왜 그렇게 했는지**를 적는다
- 마지막 줄에 `Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>`
- 베이스라인 봇 3종은 한번 커밋한 뒤 수정하지 않는다

---

## 5. 봇 작성 규칙서

이 저장소에서 새 세대 봇을 쓸 때 지켜야 할 계약이다. C1(스펙)은 규칙과 관문이
**문서와 코드**로 함께 존재할 것을 요구한다 — 코드 쪽은 `arena-gate`이고,
이 절(§5~§12)이 문서 쪽이다. **§7 "반드시 지킬 것" 표에 실린 규칙은 전부
관문이 기계로 판정한다.** 사람의 판단이 개입하는 항목은 없다. 이 절 자체가
`arena-gate`의 소스를 읽고 검증해서 쓴 것이며, 관문이 바뀌면 이 절도 같이
바뀌어야 한다 — §12 "관문을 추가할 때"를 볼 것.

## 6. 봇의 계약

```java
public interface Bot {
    String name();
    Direction move(GameView view);
}
```
(`arena-bots/src/main/java/arena/bots/Bot.java`)

`arena-bots/src/main/java/arena/bots/gen/Gen<NN>Bot.java`에 만들고,
`BotRegistry.GENERATIONS`(`arena-bots/src/main/java/arena/bots/BotRegistry.java`)에
한 줄을 추가한다. 격자는 30 × 30, 최대 턴은 900(`width * height`,
`arena-core/src/main/java/arena/core/Match.java`)이다.

`GameView`가 실제로 무엇을 들려주는지(내 머리·방향, 상대 머리·방향, 벽,
턴, 너비·높이)는 `arena-core/src/main/java/arena/core/GameView.java`를
직접 볼 것 — 여기서 되풀이하지 않는다.

## 7. 반드시 지킬 것 — 관문 G2~G7

관문은 **G2 → G7 순서로 돌고 첫 실패에서 멈춘다**
(`arena-gate/src/main/java/arena/gate/GateRunner.java`). G1(컴파일)은
Gradle이 이 코드에 도달하기 전에 이미 판정하므로 여기 없다.

| | 규칙 | 판정 | 근거 |
|---|---|---|---|
| 1 | **인스턴스 필드를 갖지 않는다.** 봇은 무상태 순수 함수다 | G2 | `StatelessGate` |
| 2 | **아래 API를 쓰지 않는다** | G3 | `ForbiddenApiGate` |
| 3 | **어떤 국면에서도 예외를 던지지 않고 null을 반환하지 않는다** | G4 | `LegalMoveGate` |
| 4 | **같은 입력에는 항상 같은 출력을 낸다** | G5 | `DeterminismGate` |
| 5 | **한 수를 p99 5ms 안에 결정한다** | G6 | `TimeBudgetGate` |
| 6 | **베이스라인 3종에게 한 번도 지지 않는다**(패배 0회 — 전승이 아니다) | G7 | `RegressionGate` |

**G2 무상태.** 리플렉션으로 클래스 계층을 `Object`까지 걸어 올라가며 매
단계에서 검사한다(`getDeclaredFields()`가 그 클래스 자신에 선언된 필드만
주기 때문). static·synthetic 필드는 건너뛴다 — `static final` 상수는
허용하고, **가변 static 필드는 G2가 아니라 G3가 잡는다**(바로 아래).
G2가 그 필드 앞에서 침묵한다고 해서 안전하다는 뜻이 아니다. 인스턴스
필드는 어느 조상 클래스에 있어도 반려다.

**G3 금지 API.** 바이트코드(ASM) 스캔이다. 두 가지를 잡는다.

① **가변 static 필드.** `static`이면서 `final`이 아닌 필드는 그 자체로
반려다 — 이름·타입과 무관하게 걸린다. 전역 가변 상태는 경기 사이를
오염시키므로, G2가 인스턴스 필드만 보고 넘어가는 구멍을 여기서 막는다.
`private static final`(상수)만 허용, 그 외 모든 `static` 필드는 반려다.

> **`static final`은 참조만 얼린다 — 가리키는 객체는 얼지 않는다.**
> `static final` 배열·컬렉션·그 밖의 가변 객체는 **쓰기 가능한 전역
> 변수**이고, 계약상 금지다(스펙 §4.1이 "전역 가변 상태: non-final static
> 필드, **final이어도 가변 객체**"라고 명시한다). 그러나 **이건 현재
> 기계로 검사되지 않는다** — 별도 최상위 클래스 구멍(아래 ②)에 쓴 것과
> 같은 솔직한 표현이다. `ForbiddenApiGate.visitField`는 `isStatic &&
> !isFinal`만 보고, `StatelessGate`는 static을 통째로 건너뛴다. 그래서
> 아래는 G2도 G3도 통과한다:
>
> ```java
> private static final long[] N = new long[1];
> public Direction move(GameView v) { N[0]++; ... }
> ```
>
> 효과가 관문의 호출 예산 밖에서 드러나면 G5도 넘긴다. 그리고 `gate`와
> `challenge`는 서로 다른 JVM이라 `challenge`가 돌 때 카운터는 새것이다 —
> 그런데 `SeriesRunner`는 100경기를 **병렬로** 돌리므로, 승격 판정이 스레드
> 인터리빙에 달리게 된다. 관문이 막으라고 존재하는 바로 그 R1 위반이다.
> 관문이 잡아주지 않으니 **작성자가 지켜야 한다.** 상수로 두려면 불변이어야
> 한다 — 원시값, `String`, `enum`, 또는 `List.of(...)`처럼 진짜 불변인
> 컬렉션. 배열은 어떤 경우에도 상수가 아니다.

② **금지된 API 호출·생성·필드 접근.** 봇의 최상위 클래스뿐 아니라
**중첩 클래스·익명 클래스·로컬 클래스까지 재귀적으로 따라간다**(봇의
최상위 클래스 이름을 접두어로 삼아 `InnerClasses` 애트리뷰트를 재귀
탐색) — 하지만 이 재귀는 **봇 자신의 자손 클래스**로만 뻗는다.
`Gen07Bot` 옆에 나란히 놓인 별도의 최상위 클래스(예: `Gen07Helper`)는
`Gen07Bot`의 자손이 아니므로 스캔 대상에 들지 않는다 — 금지 호출을
그런 **별도 최상위 클래스**로 옮기면 이 관문을 통과해버린다. "헬퍼
클래스로 옮겨도 피할 수 없다"가 맞는 건 그 헬퍼가 봇 클래스의 중첩·
익명·로컬 클래스일 때뿐이다.

```
FORBIDDEN_PREFIXES (owner가 이 접두사로 시작하면 통째로 금지):
  java/io/                 java/nio/file/            java/net/
  java/util/concurrent/    java/time/                java/lang/reflect/
  sun/misc/Unsafe          java/lang/Thread          java/lang/ProcessBuilder

FORBIDDEN_METHODS (owner.name 정확 일치):
  java/lang/Math.random             java/lang/System.currentTimeMillis
  java/lang/System.nanoTime         java/lang/System.identityHashCode
  java/lang/System.getenv           java/lang/System.getProperty
```

`java/lang/Thread` 접두사는 구분자 없이 매치되므로 `Thread` 자신뿐 아니라
**`ThreadLocal`·`ThreadGroup`·`ThreadDeath`까지 `java.lang.Thread*` 계열 전체가
금지**다 — 셋 다 상태나 스레드 기계장치를 끌고 들어오므로 무상태 봇이
건드릴 일이 없다는 판단이다.

시드 없는 `new Random()`(생성자 디스크립터 `()V`)만 콕 집어 금지한다.
`new Random(seed)`(`(J)V`)는 허용이다 — 재현 가능성(R1)이 걸린 건 시드
없는 난수뿐이기 때문이다.

`java.lang.Object.hashCode()`는 금지 목록에 **없다.** 한때 올라 있었지만,
G3는 *호출* 차단이 임무이고 결정론 위반을 잡는 건 G5의 임무라는 것이
테스트로 증명되면서(G3가 G5보다 먼저 같은 함정을 잡아내고 있었다) 빠졌다.
`hashCode()`를 오용해 비결정적으로 움직이는 봇은 여전히 걸리지만, 잡는
관문은 G5다.

**G4 합법 수.** 표본 10,000개(`GateRunner.SAMPLE_SIZE`) 전부에서 예외를
던지지 않고 null을 반환하지 않아야 한다. 표본은 심사 시드가 아니라
**베이스라인 3종(WallAvoidBot·RandomBot·StraightBot)끼리 시드 1‥10,000을
돌며 실제로 벌어진 대전**에서 뽑는다(`PositionSampler.sample`) — 무작위로
격자를 채워 만들지 않는 이유는 도달 불가능한 국면으로 시험하면 실전에서
안 일어날 실패를 잡아 루프를 헛돌게 하기 때문이다. 이 표본은 G4·G5·G6가
공유해서 재사용한다. "죽지 않는 수"를 요구하는 게 아니다 — 자멸은 봇의
자유이며 지표로만 남는다.

세 관문(G4·G5·G6) 모두 `move()` 호출을 `RuntimeException | StackOverflowError`로
감싸 잡는다 — "예외를 던지지 않는다"는 재귀 깊이로 스택을 넘치는 것도
포함해서 하는 말이다. 그리고 G4가 한 국면을 통과시켰다고 그 국면이
끝난 게 아니다 — 같은 국면이 G5(반복 호출)·G6(시간 측정) 안에서
다시 먹여지므로, G4를 어쩌다 통과한 예외가 G5·G6에서 다시 걸릴 수
있다.

**G5 결정론.** 두 층으로 본다: ① 같은 국면을 반복해서 물으면 항상 같은
방향이 나오는가, ② 베이스라인 3종 상대 경기를 두 번 돌리면 리플레이 해시가
같은가. ②는 봇 내부 비결정론이 아니라 경기 전체에 걸쳐 누적되는 비결정론을
잡는다.

**G6 시간 예산.** p99 ≤ 5ms(`GateRunner.P99_LIMIT_MILLIS`). **시간을 재는
곳은 이 관문 하나뿐이다** — 대전 중에는 어떤 시간 제한도 걸지 않는다.
시간 기반 판정이 대전 안에 섞이면 같은 조건에 다른 결과가 나와 R1(재현
가능성)을 깨뜨리기 때문이다.

**G7 회귀 방지.** 베이스라인 3종(StraightBot, RandomBot, WallAvoidBot)
상대로 심사 시드 50개 × 교대 좌석에서 **패배가 0회**여야 한다. "전승"을
요구하지 않는 이유가 있다 — RandomBot 상대로는 정면 충돌 무승부가 구조적으로
발생하므로, 전승을 기준으로 삼으면 실력과 무관하게 반려된다.

## 8. 제출 절차와 종료 코드

```bash
./gradlew gate      -Pbot=Gen07Bot   # 관문 G2~G7
./gradlew challenge -Pbot=Gen07Bot   # 챔피언전
```

CLI(`arena.api.ArenaApplication`)는 종료 코드 넷을 구분한다 — 호출자가
코드만 보고 "봇이 거부됐다"와 "하네스 자체가 깨졌다"를 갈라볼 수 있어야
한다:

| 코드 | 의미 |
|---|---|
| 0 | 성공 — 관문 통과 / 챔피언 승격 / 재현 검증 통과 |
| 1 | 판정에 의한 거부 — 관문 반려 / 승격 실패 / 재현 검증 실패 |
| 2 | 호출 오류 — 인자 누락, 알 수 없는 명령, 등록되지 않은 봇 이름, 도전자==챔피언 |
| 3 | 하네스 오류 — 위 세 경우 어디에도 속하지 않는 처리되지 않은 예외 |

**이 코드를 `./gradlew`의 종료 코드에서 읽으려 하지 말 것.** Gradle의
`JavaExec`은 0이 아닌 코드를 전부 빌드 실패로 뭉갠다 — 그대로 두면
1·2·3이 호출자에게 똑같이 1로 보여서, 이 표가 나눠 놓은 구분이 정작
위에 적힌 명령으로는 관측되지 않는다. 그래서 CLI가 진짜 코드를 표준
출력의 **마지막 줄**에 한 번 더 싣는다:

```
ARENA_EXIT_CODE=<0|1|2|3>
```

이 줄은 `./gradlew`로 부르든 `java -cp … arena.api.ArenaApplication`으로
직접 부르든 똑같이 나온다(`ArenaApplication.run`이 찍는다). 순수 ASCII인
이유는 Gradle이 띄운 자식 JVM의 표준 출력이 UTF-8이 아닐 수 있기
때문이다 — 실제로 이 CLI의 한국어 메시지는 `./gradlew gate` 출력에서
`??`로 깨져 나온다. 스크립트는 이렇게 읽는다:

```bash
CODE=$(./gradlew gate -Pbot=Gen07Bot | grep -o 'ARENA_EXIT_CODE=[0-3]' | tail -1 | cut -d= -f2)
```

**Gradle 태스크 자체는 하네스 오류(3)일 때만 실패한다.** 관문 반려(1)는
세대 루프의 정상적이고 흔한 결과이지 빌드 고장이 아니다 — 그걸
`BUILD FAILED`로 만들면 "봇이 거부됐다"와 "하네스가 깨졌다"가 다시 한
신호로 합쳐진다. 이렇게 두면 Gradle의 종료 코드마저 그 구분의 충실한
이진 투영이 된다: **0이 아니면 하네스가 깨진 것이다.**

그 대가로 **호출 오류(2)도 `BUILD SUCCESSFUL`로 끝난다.** 봇 이름을 잘못
준 호출자는 초록 불을 본다 — 종료 코드만 보고 성공으로 판단하지 말고
반드시 `ARENA_EXIT_CODE` 줄을 읽어야 한다.

그리고 이 투영은 **한 방향으로만** 성립한다. `ARENA_EXIT_CODE` 줄을 찍는
것은 `ArenaApplication.run` 자신이므로, 그 지점에 닿기 전에 JVM이 죽으면
(잡히지 않은 `Error`, `OutOfMemoryError`, 강제 종료) 줄이 아예 나오지
않는다 — 그런데 Gradle은 3이 아니면 실패시키지 않으므로 이 경우가
`BUILD SUCCESSFUL`로 보인다. 그래서 **`ARENA_EXIT_CODE` 줄이 아예 없으면
그것도 하네스가 죽은 것으로 읽어야 한다.** 위의 `grep` 예시는 그때 빈
문자열을 내놓는다 — 스크립트는 빈 값을 성공이 아니라 3과 같이 다뤄야
한다.

**절차상** `gate`를 먼저 통과시키고 나서 `challenge`를 돌린다 — 이건
관문이 강제하는 게 아니다. `ChallengeCommand.run`은 gate 리포트를 전혀
참조하지 않고 곧바로 `Championship.judge`를 부르므로, gate를 안 돌리고
`challenge`만 실행해도 기계적으로 막히지 않는다. 그래도 순서를 지키는
이유는 단순하다 — G2~G7을 안 거친 봇은 애초에 예외를 던지거나 무상태가
아닐 수 있고, `challenge`는 그런 결함을 잡게 설계돼 있지 않다.
`challenge`는 심사 시드 1‥50에서 챔피언과 교대 좌석으로 붙는다.
**승점 승률이 60% 이상**
(무승부는 0.5점, `Championship.PROMOTION_THRESHOLD`, 경계값 60%
포함)이면 승격이다. 승격한 도전자만 홀드아웃 시드 1001‥1050을 추가로
돌려 그 승률을 함께 보고한다 — 홀드아웃은 승격 여부를 **결정하지 않는다**,
심사 승률과의 격차가 시드 과적합의 신호일 뿐이다.

## 9. 봇 이름

`BotRegistry.validateRegistration()`이 등록 시점에 기계로 강제한다
(`arena-bots/src/main/java/arena/bots/BotRegistry.java`):

- 세대 봇 이름은 정확히 `Gen<숫자>Bot` 형식이어야 한다(예: `Gen07Bot`)
- 세대·베이스라인을 통틀어 이름이 중복되면 안 된다
- 이름에 `"|"`가 들어가면 안 된다(`ReplayHash`의 정규화 문자열이
  `"|"`로 필드를 가르기 때문)

이 셋을 어기면 판정 거부(1)가 아니라 **하네스 오류(3)**로 떨어진다 — 이름
규칙은 봇의 실력과 무관한 등록 결함이기 때문이다.

## 10. 알아둘 것 — 가이드라인 (관문이 기계로 판정하지 않는 것)

아래는 관문이 통과/실패로 판정하지 않는 항목이다. 지키지 않아도 G2~G7을
통과할 수 있지만, 승격 절차와 발표 재료의 취지를 지키려면 따라야 한다.

- **직전 챔피언을 출발점으로 증분 개선한다.** 백지에서 다시 쓰지 않는다.
  세대별 diff가 작고 선명해야 "이번 세대는 무엇을 배웠는가"가 한 문장으로
  설명된다
- **심사 시드(1‥50)에서만 통하는 수를 짜지 말 것.** 홀드아웃 시드
  (1001‥1050)로 별도 검증하며, 두 승률의 격차는 기록에 남는다
- **자멸은 반려 사유가 아니다.** 다만 자멸률은 세대별로 측정되어 기록에
  남는다. 자멸률은 **정면 충돌을 포함하지 않는다** — 정면 충돌은 상대의
  동시 선택에 달려 있고, 스펙 §2.1상 어떤 봇도 상대의 이번 턴 선택을
  미리 볼 수 없으므로, 정면 충돌을 자멸로 세면 자멸률이 부풀려진다
  (`arena-diagnostics/src/main/java/arena/diagnostics/MoveAnalysis.java`)
- **반려당한 코드는 지우지 않는다.** `records/gen-NN/attempt-M/`에 그대로
  남는다
- **한 세대에 5회까지 시도할 수 있다.** 초과하면 실험을 종료하고 수렴으로
  선언한다(스펙 §5 — `RecordStore.nextAttempt`는 시도 번호를 셀 뿐 이
  한도 자체를 강제하지 않는다. 세대 루프가 강제할 몫이다)
- **베이스라인 3종(StraightBot, RandomBot, WallAvoidBot)은 동결이다.**
  한번 커밋한 뒤 수정하지 않는다(§4 참고)

## 11. 기준값에 대해

**관문 기준값(G6의 5ms, G7의 패배 0회)과 승격 기준(60%)은 통과하지
못한다는 이유로 낮추지 않는다.** 통과하지 못하면 봇이 부족한 것이다
(BRIEF §11-4, §3-4 참고).

## 12. 관문을 추가할 때

관문을 새로 만들면 **그 관문을 일부러 위반하는 함정 봇을 함께 추가한다**
(`arena-gate/src/test/java/arena/gate/traps/`). 관문이 정말 잡아내는지
증명되지 않은 관문은 관문이 아니다.

그리고 **이 절(§5~§12)도 함께 고친다.** 문서와 관문이 서로 다른 것을
말하기 시작하면 하네스가 무너진다 — 관문에서 반려된 항목은 이 문서에도
반영해서, 문서가 "관문에서 통과하지만 문서는 금지라고 말하는" 또는
"문서는 허용하지만 관문은 반려하는" 상태에 빠지지 않게 한다.
