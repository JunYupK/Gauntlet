# 세대 루프 (Generation Loop) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 하네스가 이미 갖춘 관문·챔피언전·기록·번들 위에서, 에이전트가 **직전 챔피언을 증분 개선한 실제 세대 봇**을 세대마다 새로 써서 관문과 챔피언전을 통과시키고 승격시키는 루프(C2)를 **실제로 돌 수 있게** 만든다. 데모 번들이 아니라 진짜 루프가 뽑은 세대로 개선 곡선(R3 ≥10×)을 그린다.

**Architecture:** 스펙 §5.1이 못박은 대로 **루프 오케스트레이션용 새 바이너리를 만들지 않는다** — 하네스는 `gate`·`challenge`·`record`·`record --verify` 넷이 전부이고, 에이전트가 봇을 쓰고 이 넷만 실행한다(LLM 호출은 하네스 밖 — 스펙 §3, R1). 이 계획은 두 부분이다. **(A) 인프라**: 계획 수립 중 드러난, 다세대 실제 경로에서만 나타나는 두 결함(챔피언 선택·attempt 결합)과 수렴 강제를 코드로 닫는다 — 데모가 이력을 합성해 이 경로를 밟지 않아 지금껏 잠복해 있었다. **(B) 실행 플레이북**: 한 세대를 어떻게 쓰고 통과시키고 기록·검증하는지의 반복 절차와, 다양한 전략의 세대 사다리, 그리고 멈추는 규칙.

**Tech Stack:** Java 21, Gradle 멀티모듈(`arena-core → arena-bots → arena-diagnostics → arena-gate → arena-tournament → arena-api`, 단방향). 봇은 `arena.bots.Bot` 구현. 판정은 `arena.gate`·`arena.tournament`. CLI는 `arena.api`.

**Spec:** `docs/superpowers/specs/2026-08-19-bot-arena-design.md` (특히 §5 루프, §6 승격 심사, §8 기록, §13 R3). 봇 규칙서는 `CLAUDE.md` §5~§12.

## Global Constraints

모든 태스크의 요구사항에 아래가 암묵적으로 포함된다. 값은 스펙·규칙서에서 그대로 옮긴 것이다.

- **봇은 무상태 순수 함수.** 인스턴스 필드 금지(G2). 같은 입력 → 같은 출력(G5). (`CLAUDE.md` §7)
- **관문 G2~G7을 모두 통과해야 한다.** 금지 API·가변 static 금지(G3), 어떤 국면에서도 예외·null 금지(G4), p99 ≤ 5ms(G6), 베이스라인 3종에 패배 0회(G7).
- **관문 기준값(5ms·패배 0회)과 승격 기준(승점 승률 60%)은 루프가 통과 못 한다는 이유로 낮추지 않는다.** 못 넘으면 봇이 부족한 것이다(BRIEF §11-4, 스펙 §6).
- **세대당 재시도 5회.** 초과하면 `CONVERGED`로 선언하고 그 세대에서 실험을 멈춘다(스펙 §5).
- **직전 챔피언에서 증분 개선.** 백지에서 다시 쓰지 않는다 — 세대별 diff가 작고 선명해야 한다(스펙 §5·§6).
- **반려된 시도는 코드째로 남긴다.** `records/gen-NN/attempt-M/`에서 지우지 않는다(스펙 §8.3, BRIEF §8).
- **봇 이름은 정확히 `Gen<숫자>Bot`**, 이름에 `"|"` 금지, 중복 금지(`BotRegistry.validateRegistration`).
- **Gen 0은 관문 대상이 아니다.** 사람이 심는 기준선이고 루프는 Gen 1부터 돈다(스펙 §4). 등록된 Gen 0 = `Gen00Bot`(StraightBot 수준, 평균 ~16턴).
- **베이스라인 3종(StraightBot·RandomBot·WallAvoidBot)은 동결.** 한번 커밋 후 수정 금지.
- **커밋 메시지는 한국어, "왜"를 적는다.** 마지막 줄 `Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>`. `log.md`는 append-only.
- **격자 30×30, 최대 900턴.** 시드는 `arena.api.Seeds`(JUDGING 1‥50, HOLDOUT 1001‥1050).
- **R1 재현.** `record` 뒤 `record --verify`가 리플레이 해시 대조를 통과해야 한다(`ARENA_EXIT_CODE=0`).

---

## 파일 구조

이 계획이 만들거나 고치는 파일과 각자의 책임.

| 파일 | 책임 | 태스크 |
|---|---|---|
| `arena-bots/.../BotRegistry.java` | `championFor` 추가(도전자보다 한 세대 낮은 챔피언) + 세대 봇 등록 | 1, 4+ |
| `arena-api/.../cli/ChallengeCommand.java` | 챔피언을 `championFor`로 고르고, 챔피언전 결과를 **현재 열린 attempt**에 기록 | 1, 2 |
| `arena-tournament/.../RecordStore.java` | `latestAttempt` 추가(현재 열린 attempt 번호), 6번째 attempt 개방 거부 | 2, 3 |
| `arena-api/.../cli/GateCommand.java` | 6번째 attempt 개방 시 `CONVERGED` 반려 | 3 |
| `arena-bots/.../gen/GenNNBot.java` | 실제 세대 봇(세대마다 새 파일) | 4+ (실행) |
| `arena-gate/src/test/.../traps/` | 새 관문을 추가하지는 않으나, 수렴 가드의 함정 테스트 | 3 |
| `records/gen-NN/…`, `web/public/data/…` | 명령 실행의 산출물(직접 손으로 쓰지 않는다) | 4+ |
| `log.md` | 세대별 결정·측정 append | 4+ |

---

## Task 1: 챔피언 선택 — 도전자보다 한 세대 낮은 챔피언

**문제.** `ChallengeCommand`는 챔피언을 `BotRegistry.latestGeneration()`(=가장 높은 등록 세대)으로 고른다. 그런데 도전자 `GenN`을 붙이려면 그게 등록돼 있어야 하고(gate·`byName`), 그러면 `latestGeneration()`도 `GenN`이라 **도전자==챔피언**이 되어 종료 코드 2로 막힌다. 실제 증분 루프에선 `GenN`이 `GenN-1`과 붙어야 한다.

**Files:**
- Modify: `arena-bots/src/main/java/arena/bots/BotRegistry.java` (add `championFor`)
- Modify: `arena-api/src/main/java/arena/api/cli/ChallengeCommand.java:33` (`latestGeneration()` → `championFor(challenger)`)
- Test: `arena-bots/src/test/java/arena/bots/BotRegistryTest.java`

**Interfaces:**
- Consumes: `BotRegistry.generationNumber(Bot)` (private static, "Gen07Bot"→7 — 이미 존재), `highestGeneration(List<Bot>)` 패턴.
- Produces: `public static Bot BotRegistry.championFor(Bot challenger)` — 등록된 세대 중 **도전자 세대 번호보다 작은 것들의 최댓값**. 그런 세대가 없으면(도전자가 Gen 0) `IllegalArgumentException`.

- [ ] **Step 1: 실패하는 테스트를 쓴다**

`BotRegistryTest.java`에 (패키지 내부라 package-private 오버로드에 접근 가능하다고 가정 — `highestGeneration(List)`가 그렇게 테스트되고 있다):

```java
@Test
void championFor는_도전자보다_한_세대_낮은_최고_세대를_고른다() {
    Bot g0 = new StraightBot();               // Gen00Bot 대역이 아니라 실제 등록형이면 그대로
    Bot g1 = named("Gen01Bot");
    Bot g2 = named("Gen02Bot");
    List<Bot> gens = List.of(g0, g1, g2);
    assertEquals("Gen01Bot", BotRegistry.championFor(g2, gens).name());
    assertEquals("Gen00Bot", BotRegistry.championFor(g1, gens).name());
}

@Test
void championFor는_Gen0_아래_챔피언이_없으면_거부한다() {
    Bot g0 = named("Gen00Bot");
    assertThrows(IllegalArgumentException.class,
            () -> BotRegistry.championFor(g0, List.of(g0)));
}
```

`named(...)`는 이 테스트 파일에 이미 있는 이름-스텁 헬퍼를 쓰거나, 없으면 `name()`만 오버라이드하고 `move`는 `Direction.UP`을 내는 최소 익명 봇으로 만든다.

- [ ] **Step 2: 실패를 확인한다**

Run: `./gradlew :arena-bots:test --tests '*BotRegistryTest'`
Expected: FAIL — `championFor` 메서드 없음(컴파일 에러).

- [ ] **Step 3: 최소 구현**

`BotRegistry.java`에 추가:

```java
/** 도전자 세대보다 번호가 낮은 최고 세대 = 이번 챔피언. GENERATIONS로 부른다. */
public static Bot championFor(Bot challenger) {
    return championFor(challenger, GENERATIONS);
}

static Bot championFor(Bot challenger, List<Bot> generations) {
    int challengerGen = generationNumber(challenger);
    return generations.stream()
            .filter(b -> generationNumber(b) < challengerGen)
            .max(Comparator.comparingInt(BotRegistry::generationNumber))
            .orElseThrow(() -> new IllegalArgumentException(
                    "챔피언이 없다 — " + challenger.name() + "보다 낮은 세대가 등록돼 있지 않다"));
}
```

- [ ] **Step 4: 통과 확인**

Run: `./gradlew :arena-bots:test --tests '*BotRegistryTest'`
Expected: PASS.

- [ ] **Step 5: ChallengeCommand를 새 선택으로 잇는다**

`ChallengeCommand.java`에서 `Bot champion = BotRegistry.latestGeneration();`을
`Bot champion = BotRegistry.championFor(challenger);`로 바꾼다. `championFor`가 던지는 `IllegalArgumentException`을 도전자 조회와 같은 `try/catch`로 감싸 종료 코드 2를 내게 한다(Gen 0을 도전시키는 건 호출 오류다). 기존 "도전자==챔피언" 가드는 `championFor`가 구조적으로 그 경우를 없애므로 제거해도 되지만, 남겨도 무해하다.

- [ ] **Step 6: 회귀 확인**

Run: `./gradlew :arena-api:test`
Expected: PASS(기존 ChallengeCommand 테스트가 챔피언 주입 방식을 쓴다면 함께 갱신).

- [ ] **Step 7: 커밋**

```bash
git add arena-bots/src/main/java/arena/bots/BotRegistry.java \
        arena-bots/src/test/java/arena/bots/BotRegistryTest.java \
        arena-api/src/main/java/arena/api/cli/ChallengeCommand.java
git commit -m "수정: 챔피언전이 도전자보다 한 세대 낮은 챔피언과 붙도록 고친다

..."
```

---

## Task 2: attempt 연속성 — 챔피언전은 gate가 연 그 attempt에 기록한다

**문제.** `gate GenN`은 `saveGateReport(gen, nextAttempt(gen)=M)`로 `attempt-M`을 만든다. 이어 `challenge GenN`은 `saveChallengeReport(gen, nextAttempt(gen))`를 부르는데, 이미 `attempt-M`이 생겨 `nextAttempt`가 **M+1**을 돌려주므로 챔피언전 결과가 `attempt-(M+1)`에 홀로 저장된다. 스펙 §8.3은 한 attempt 디렉터리에 `gate-report.json`과 `championship.json`이 **함께** 있는 그림이고, `RecordStore.historyOf`의 `CHAMPIONSHIP` 갈래도 그 전제로 판정한다.

**Files:**
- Modify: `arena-tournament/src/main/java/arena/tournament/RecordStore.java` (add `latestAttempt`)
- Modify: `arena-api/src/main/java/arena/api/cli/ChallengeCommand.java` (챔피언전을 `latestAttempt`에 기록)
- Test: `arena-tournament/src/test/java/arena/tournament/RecordStoreTest.java`

**Interfaces:**
- Consumes: `RecordStore.nextAttempt(int)` (이미 존재 — 다음 쓸 번호), `saveGateReport`, `saveChallengeReport`.
- Produces: `public int RecordStore.latestAttempt(int gen)` — 현재 존재하는 가장 큰 attempt 번호(`nextAttempt(gen) - 1`), 없으면 0.

- [ ] **Step 1: 실패하는 테스트**

`RecordStoreTest.java`(임시 디렉터리 사용):

```java
@Test
void gate_다음_challenge는_같은_attempt에_두_리포트를_남긴다(@TempDir Path tmp) {
    RecordStore store = new RecordStore(tmp);
    int attempt = store.nextAttempt(5);                 // 1
    store.saveGateReport(5, attempt, "class Gen05Bot{}", passedReport());
    store.saveChallengeReport(5, store.latestAttempt(5), rejectedChallenge());
    // 한 attempt 디렉터리에 두 파일이 함께 있어야 한다
    assertTrue(Files.exists(tmp.resolve("gen-5/attempt-1/gate-report.json")));
    assertTrue(Files.exists(tmp.resolve("gen-5/attempt-1/championship.json")));
    assertFalse(Files.exists(tmp.resolve("gen-5/attempt-2")));
}
```

`passedReport()`/`rejectedChallenge()`는 이 테스트 파일에 이미 있는 리포트 팩토리를 쓰거나, 없으면 최소 인스턴스를 만든다(`GateReport`·`ChallengeReport`의 기존 생성자 시그니처를 그대로).

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :arena-tournament:test --tests '*RecordStoreTest'`
Expected: FAIL — `latestAttempt` 없음.

- [ ] **Step 3: 최소 구현**

`RecordStore.java`에 추가(주석으로 `nextAttempt`와의 관계를 밝힌다):

```java
/** 현재 존재하는 가장 큰 attempt 번호. 아직 없으면 0. gate가 연 attempt에
 *  challenge가 이어 쓰기 위한 것이다 — nextAttempt는 "다음 쓸 번호"라 gate가
 *  이미 연 attempt를 가리키지 못한다. */
public int latestAttempt(int generation) {
    return nextAttempt(generation) - 1;
}
```

- [ ] **Step 4: 통과 확인**

Run: `./gradlew :arena-tournament:test --tests '*RecordStoreTest'`
Expected: PASS.

- [ ] **Step 5: ChallengeCommand를 잇는다**

`ChallengeCommand.java`에서 `store.saveChallengeReport(generation, store.nextAttempt(generation), report);`를
`store.saveChallengeReport(generation, store.latestAttempt(generation), report);`로 바꾼다. `latestAttempt`가 0이면(= 선행 gate가 없다) 챔피언전을 기록할 열린 attempt가 없다는 뜻이므로, 사람이 읽을 경고와 종료 코드 2(호출 오류 — gate를 먼저 돌려야 한다)를 낸다.

- [ ] **Step 6: 회귀 확인**

Run: `./gradlew :arena-tournament:test :arena-api:test`
Expected: PASS.

- [ ] **Step 7: 커밋**

```bash
git add arena-tournament/src/main/java/arena/tournament/RecordStore.java \
        arena-tournament/src/test/java/arena/tournament/RecordStoreTest.java \
        arena-api/src/main/java/arena/api/cli/ChallengeCommand.java
git commit -m "수정: 챔피언전 결과를 gate가 연 그 attempt에 이어 기록한다

..."
```

---

## Task 3: 수렴 가드 — 6번째 attempt 개방을 거부한다

**문제.** 스펙 §5는 세대당 재시도 5회를 넘기면 `CONVERGED`로 선언하라 하고, `CLAUDE.md` §10은 "`RecordStore.nextAttempt`는 시도 번호를 셀 뿐 한도를 강제하지 않는다 — 세대 루프가 강제할 몫이다"라고 명시한다. 기계 판정(R2)에 맞게, 6번째 attempt를 열려는 `gate`를 거부한다.

**Files:**
- Modify: `arena-api/src/main/java/arena/api/cli/GateCommand.java` (attempt 개방 전 한도 검사)
- Test(함정): `arena-gate/src/test/java/arena/gate/traps/` 또는 `arena-api` 테스트 — 5회를 채운 기록에 6번째 gate를 돌리면 `CONVERGED` 반려(코드 1).

**Interfaces:**
- Consumes: `RecordStore.nextAttempt(int)`, `GateCommand.generationOf(String)`.
- Produces: 없음(동작 변경). 6번째 개방 시 표준출력에 `CONVERGED — 세대 N은 재시도 5회를 소진했다`를 찍고 종료 코드 **1**(판정에 의한 거부 — 그 세대는 승격 없이 수렴).

- [ ] **Step 1: 실패하는 테스트**

`records`를 주입하는 `GateCommand.run(botName, recordsRoot, judge)` 오버로드로(이미 존재), 임시 디렉터리에 attempt 1‥5를 미리 채우고 6번째 gate를 돌린다:

```java
@Test
void 여섯번째_시도는_CONVERGED로_거부된다(@TempDir Path tmp) {
    RecordStore store = new RecordStore(tmp);
    for (int i = 1; i <= 5; i++)
        store.saveGateReport(5, i, "class Gen05Bot{}", anyReport());
    int code = GateCommand.run("Gen05Bot", tmp, bot -> passedReport());
    assertEquals(1, code);                       // 판정 거부
    assertFalse(Files.exists(tmp.resolve("gen-5/attempt-6")));  // 열리지 않았다
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :arena-api:test --tests '*GateCommand*'`
Expected: FAIL — 현재는 attempt-6을 열고 통과/반려를 낸다.

- [ ] **Step 3: 최소 구현**

`GateCommand.run(botName, recordsRoot, judge)`에서 봇 조회 직후, 판정·기록 전에:

```java
int generation = generationOf(botName);
if (generation >= 0) {
    RecordStore store = new RecordStore(recordsRoot);
    if (store.nextAttempt(generation) > 5) {          // 이미 5회를 채웠다
        System.out.println("CONVERGED — 세대 " + generation + "은 재시도 5회를 소진했다");
        return 1;
    }
}
```

기존 판정·기록 코드는 이 검사 뒤에 그대로 둔다. (주의: `generationOf`가 두 번 불리지 않게 지역변수를 재사용한다.)

- [ ] **Step 4: 통과 확인**

Run: `./gradlew :arena-api:test --tests '*GateCommand*'`
Expected: PASS.

- [ ] **Step 5: 문서 동기화**

`CLAUDE.md` §10의 "`RecordStore.nextAttempt`는 시도 번호를 셀 뿐 이 한도 자체를 강제하지 않는다. 세대 루프가 강제할 몫이다" 문장을, 이제 `gate`가 6번째 개방을 거부한다는 사실로 갱신한다(§12 "관문을 추가할 때"의 문서-코드 동기화 원칙). loop-history 렌더링은 5개까지의 attempt만 보게 된다.

- [ ] **Step 6: 커밋**

```bash
git add arena-api/src/main/java/arena/api/cli/GateCommand.java \
        arena-api/src/test/java/arena/api/cli/GateCommandTest.java CLAUDE.md
git commit -m "수정: 세대당 6번째 시도를 CONVERGED로 거부해 5회 한도를 기계가 강제한다

..."
```

---

## Task 4: 첫 실제 세대 — Gen01Bot 엔드투엔드 + R1 검증

인프라(Task 1~3)가 닫혔으니, **첫 진짜 세대**를 써서 파이프라인 전체가 실제로 도는지 증명한다. 이 태스크가 통과하면 이후 세대는 실행 플레이북의 반복이다.

**전략(Gen 1):** 가장 단순한 벽 회피 — `GameView.isDeadly(d)`가 거짓인 방향 중 하나를 결정론적으로 고른다(예: `UP→RIGHT→DOWN→LEFT` 고정 우선순위에서 첫 안전 방향). Gen 0(직진)은 벽에 처박혀 ~16턴에 죽으므로, "안 죽는 방향으로 돈다"만으로 생존 턴이 크게 뛴다.

**Files:**
- Create: `arena-bots/src/main/java/arena/bots/gen/Gen01Bot.java`
- Modify: `arena-bots/src/main/java/arena/bots/BotRegistry.java` (`GENERATIONS`에 `new Gen01Bot()` 추가)
- (실행 산출물) `records/gen-1/…`, `web/public/data/…`

**Interfaces:**
- Consumes: `arena.core.GameView`(`isDeadly(Direction)`, `myHead`, `wall`, `inBounds`, `turn`, `width`, `height`), `arena.core.Direction`(UP/DOWN/LEFT/RIGHT).
- Produces: 등록된 `Gen01Bot`(무상태). 이후 세대는 이 소스를 출발점으로 증분한다.

- [ ] **Step 1: Gen01Bot을 쓴다**

`GameView.java`를 먼저 열어 실제 접근자(`myHead`, `isDeadly`, `isWall`, `inBounds`)를 확인한 뒤:

```java
package arena.bots.gen;

import arena.bots.Bot;
import arena.core.Direction;
import arena.core.GameView;

// Gen 1 — 직진(Gen 0)에서 한 걸음: 즉사하지 않는 방향으로 돈다.
public final class Gen01Bot implements Bot {
    private static final Direction[] PREFERENCE = { Direction.UP, Direction.RIGHT, Direction.DOWN, Direction.LEFT };

    @Override public String name() { return "Gen01Bot"; }

    @Override public Direction move(GameView view) {
        for (Direction d : PREFERENCE) {
            if (!view.isDeadly(d)) return d;
        }
        return PREFERENCE[0];   // 사방이 막혔다 — 자멸은 자유(G4는 "예외·null 금지"만 본다)
    }
}
```

- [ ] **Step 2: 등록하고 이름 검증을 통과시킨다**

`BotRegistry.GENERATIONS`를 `List.of(new Gen00Bot(), new Gen01Bot())`로 바꾼다.
Run: `./gradlew :arena-bots:test`
Expected: PASS(`validateRegistration`이 이름·중복을 통과).

- [ ] **Step 3: 관문 G2~G7을 실제로 돌린다**

Run: `./gradlew gate -Pbot=Gen01Bot`
`ARENA_EXIT_CODE`를 읽는다:
```bash
./gradlew gate -Pbot=Gen01Bot | grep -o 'ARENA_EXIT_CODE=[0-3]' | tail -1
```
Expected: `ARENA_EXIT_CODE=0`. 반려면(1) 반려 사유(어느 관문)를 보고 Gen01Bot을 고쳐 재시도(최대 5회). `records/gen-1/attempt-M/`에 시도가 쌓이는지 확인한다.

- [ ] **Step 4: 챔피언전을 돌린다**

Run: `./gradlew challenge -Pbot=Gen01Bot | grep -o 'ARENA_EXIT_CODE=[0-3]' | tail -1`
Expected: `ARENA_EXIT_CODE=0`(승점 승률 ≥60%로 Gen00 챔피언을 이긴다). Task 1·2 덕에 챔피언은 Gen00이고 챔피언전 결과가 gate와 같은 attempt에 기록된다. 60% 미만이면 Gen01Bot을 개선해 재시도.

- [ ] **Step 5: 번들 생성 + R1 재현 검증**

```bash
./gradlew record | grep -o 'ARENA_EXIT_CODE=[0-3]' | tail -1        # 0
./gradlew record -Pverify | grep -o 'ARENA_EXIT_CODE=[0-3]' | tail -1   # 0
```
Expected: 둘 다 `=0`. `web/public/data/generations.json`에 세대 0·1의 **실측** 생존 턴이, `loop-history.json`에 실제 attempt 이력이 들어간다(합성이 아니다).

- [ ] **Step 6: 프론트엔드가 진짜 번들을 읽는지 스모크**

```bash
cd web && npm run build && npm run test:e2e
```
Expected: 스모크 통과. 갤러리 패널이 **2개**(세대 0·1). 데모 배너가 **뜨지 않아야 한다**(`meta.demo`가 false — 진짜 번들).

- [ ] **Step 7: 기록하고 커밋한다**

`log.md`에 Gen 1의 전략·측정(생존 턴, 승점 승률, 시도 횟수)을 append. 그다음:
```bash
git add arena-bots/src/main/java/arena/bots/gen/Gen01Bot.java \
        arena-bots/src/main/java/arena/bots/BotRegistry.java \
        records/ web/public/data/ log.md
git commit -m "세대1: 즉사 회피로 직진 기준선을 넘어선다

..."
```

---

## 실행 플레이북 — 세대 2 이후 (반복)

Task 4가 파이프라인을 증명했다. 이후 세대는 **아래 절차의 반복**이다. 각 세대는 독립적으로 리뷰 가능한 하나의 태스크로 취급한다(subagent-driven).

### 세대 태스크 템플릿 (GenN, N ≥ 2)

1. **직전 챔피언 소스에서 출발.** `records/gen-(N-1)/`의 채택된 `bot.java`(또는 등록된 `Gen(N-1)Bot.java`)를 복사해 `GenNBot`으로 이름을 바꾸고, **하나의 전략 아이디어**만 더한다(아래 사다리). diff가 작고 한 문장으로 설명돼야 한다(스펙 §6).
2. **등록.** `BotRegistry.GENERATIONS`에 `new GenNBot()` 추가 → `./gradlew :arena-bots:test`로 이름 검증.
3. **관문.** `./gradlew gate -Pbot=GenNBot` → `ARENA_EXIT_CODE` 확인. 반려면 **반려 사유(기계 판정)를 근거로** 고쳐 재시도. **5회까지.** 6번째는 Task 3이 `CONVERGED`로 막는다.
4. **챔피언전.** `./gradlew challenge -Pbot=GenNBot` → 승점 승률 ≥60%면 승격. 미만이면 손실 상위 3개 수(진단)를 근거로 개선해 재시도(같은 5회 예산 안).
5. **승격 시:** 번들 재생성 + 검증(`record` → `record -Pverify`, 둘 다 `=0`), `log.md` append, 커밋.
6. **미승격·수렴 시(5회 소진):** 그 세대를 `CONVERGED`로 남기고(반려 기록은 지우지 않는다) **멈춘다** — 아래 "멈추는 규칙".

### 전략 사다리 (다양한 전략 — 세대별 diff 서사)

각 세대가 "무엇을 배웠나"를 한 문장으로 갖도록, 질적으로 다른 아이디어를 세대마다 하나씩 얹는다. 아래는 **목표 사다리**이며, 정확한 세대 수는 R3와 수렴에 따라 유연하다(관문·60% 기준은 절대 낮추지 않는다).

| 세대 | 새 아이디어 (한 문장) | 기대 효과 |
|---|---|---|
| 1 | 즉사하지 않는 방향으로 돈다 | 직진 기준선 돌파 |
| 2 | **공간 최대화** — 각 방향 선택 후 flood-fill 도달 칸이 가장 많은 쪽을 고른다(§7의 reach 재사용) | 자기 벽에 갇히지 않음 |
| 3 | **막다른 길 회피** — 도달 칸이 임계 이하인 방향을 후순위로 | 챔버 함정 탈출 |
| 4 | **상대 견제** — 상대 머리와의 거리·상대의 도달 공간을 고려해 공간을 나눠 갖는다 | 정면 우위 |
| 5 | **2수 예측** — 내 선택 + 상대 최선 응수까지 내다본 reach | 근시안 실수 감소 |
| 6+ | 관절점(articulation)·벽 따라가기(wall-hugging)·챔버 분할 등 | 후반 안정 수렴 |

> **주의(스펙 §4·§13).** 초기 세대가 처참하게 약해야 R3의 극적 구간이 산다. "자멸 금지" 같은 품질 관문을 세대 봇 안에 스스로 걸지 않는다 — 자멸은 지표로만 남는다. 그리고 심사 시드(1‥50)에만 통하는 수를 짜지 않는다. 홀드아웃(1001‥1050) 승률과의 격차가 기록에 남는다.

### 멈추는 규칙 (R2 — 기계로 판정 가능)

루프는 다음 중 **먼저 오는 것**에서 멈춘다:

1. **R3 달성 후 수렴** — 최신 세대 평균 생존 턴이 Gen 0의 **10배 이상**(`generations.json`의 `avgSurvivalTurns` 비교)이고, 그 뒤 어떤 세대가 5회 안에 챔피언을 60%로 못 이겨 `CONVERGED`가 되면 실험을 종료한다. 이게 정상 종료다.
2. **조기 수렴** — R3에 못 미쳤는데 한 세대가 `CONVERGED`면, 기준을 낮추지 말고(BRIEF §11) 멈춰 그 벽을 기록한다. 다음 착수 때 새 전략 계열로 재개한다.

**R3 판정은 화면이 아니라 번들이 낸다** — `web/src/lib/curve.ts`의 `r3Ratio = 최신 avgSurvivalTurns ÷ Gen0 avgSurvivalTurns`, `r3Passed = r3Ratio >= 10`. 루프는 이 값을 읽기만 한다.

### 최종 산출

- `web/public/data/`에 **진짜 번들**(`meta.demo` = false). 발표는 `npm run build`(데모가 아니라 `public/data`)로 나간다.
- 슬라이드 14(정직한 고지)의 "다음 단계 — 진짜 세대로 곡선을 다시 그린다"가 현실이 된다. 데모 번들(`web/fixtures/data/`)은 백업으로 남긴다.

---

## Self-Review (계획 대 스펙)

- **§5 루프**: Task 1(챔피언 선택)·Task 2(attempt 연속성)·Task 3(5회 수렴)·플레이북(증분 개선·재시도)로 덮음. 새 오케스트레이션 바이너리를 만들지 않는다는 §5.1 제약 준수(LLM은 하네스 밖).
- **§6 승격 심사**: 기존 `Championship.judge`(60%, 홀드아웃) 재사용 — 변경 없음. 기준 불변(BRIEF §11).
- **§8 기록**: Task 2가 attempt 디렉터리 구조(§8.3)를 실제 경로에서 바로잡는다. 번들(§8.4)은 기존 `record`가 생성.
- **§13 R3**: 플레이북의 멈추는 규칙이 `curve.ts`의 `r3Passed(≥10×)`를 종료 조건으로 읽는다.
- **미확정/실행 의존**: 세대 수와 각 세대의 정확한 전략은 실행 중 측정으로 정해진다(계획이 미리 못 박지 않는다 — 관문·60%는 절대 낮추지 않음). 이건 루프의 본질이지 계획의 공백이 아니다.
