# 발표 화면 (시각화) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 하네스가 만든 정적 번들만 읽어서 스펙 §9.2의 여섯 화면을 그리는 Next.js 앱을 만든다 — 발표 당일 백엔드도 네트워크도 필요 없이.

**Architecture:** 백엔드는 이미 `web/public/data/`에 정적 JSON을 쓴다(`./gradlew record`). 프론트엔드는 그 파일들을 빌드 타임에 읽어 정적 HTML로 export 한다(`output: 'export'`). 서버가 없으므로 발표 중 장애 범주 자체가 사라진다(R4). 화면에서 유일하게 "계산"하는 것은 리플레이의 `moves` 문자열을 격자 상태로 되돌리는 디코더 하나이고, 그것이 이 계획 전체의 핵심 위험이므로 엔진과의 대조를 기계로 강제한다.

**Tech Stack:** Next.js 15 (App Router, static export) + TypeScript 5 + Tailwind CSS 4 + Canvas 2D + Zod (스키마 계약) + Vitest (단위·계약 테스트). 백엔드 보강분은 기존 Java 21 / Gradle 모듈에 그대로 들어간다.

**Spec:** `docs/superpowers/specs/2026-08-19-bot-arena-design.md` — 특히 §8.4(번들), §9(화면), §10.1(스택), §11 T5(테스트 전략), §13(파라미터), §14(비목표).

---

## Global Constraints

이 절은 **모든 태스크의 요구사항**이다. 태스크별로 다시 적지 않는다.

- **R4 — 발표 중 안정성.** `next.config.ts`는 `output: 'export'`다. API 라우트·서버 액션·`fetch`를 통한 외부 호출·런타임 데이터 요청을 **어느 태스크도 추가하지 않는다.** 데이터는 빌드 타임에 파일시스템에서 읽는다.
- **R1 — 재현 가능성.** 프론트엔드는 번들의 수치를 **재계산하지 않는다.** `avgSurvivalTurns`·`scoreRate`·`occupancy`·`suicideRate`는 백엔드가 판정한 값을 그대로 그린다. 유일한 예외가 리플레이 디코더(Task 4)이고, 그래서 그 태스크에 엔진 대조 테스트가 붙는다.
- **R2 — 기계 판정 가능성.** "보기 좋다"는 합격 기준이 아니다. 각 태스크의 합격 기준은 전부 `npm test` 또는 `./gradlew test`가 O/X를 낸다. 스펙 §11 T5대로 **시각 회귀 테스트는 두지 않는다.**
- **R3 — 육안 식별 가능성.** 화면 1(갤러리)이 R3의 증거다. 합격선은 스펙 §13의 "Gen 0 대비 평균 생존 턴 수 10배"이고, 그 판정은 백엔드의 `generations.json`이 이미 들고 있다.
- **격자 30 × 30, 최대 턴 900.** 프론트엔드에 이 값을 리터럴로 박지 않는다 — 모든 리플레이가 `width`·`height`를 들고 있으므로 그걸 읽는다.
- **색은 청/주황** (스펙 §9.3). 봇0 = 청(`#38bdf8`), 봇1 = 주황(`#fb923c`). 색각 이상에서 구분되고 프로젝터 대비에 강하다는 것이 채택 사유이므로, 다른 색으로 바꾸지 않는다.
- **비목표** (스펙 §14): 모바일 대응 없음, 시각 회귀 테스트 없음, 실시간 관전자 모드 없음, 발표 중 라이브 계산 없음.
- **작업 디렉터리는 `web/`.** `web/public/data/`는 `.gitignore`에 있다 — **번들 JSON을 커밋하지 않는다.** 커밋되는 데이터는 `web/fixtures/`의 데모 번들뿐이고, 그것은 이름과 화면 배너로 가짜임을 밝힌다.
- **커밋 메시지는 한국어**로 쓰고 무엇이 아니라 **왜**를 적는다. 마지막 줄에 `Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>`.
- **`log.md`는 append-only.** 판단이 나오면 그 자리에서 새 항목으로 덧붙인다(현재 마지막은 D72).

---

## 지금 있는 것과 없는 것

계획을 짜기 전에 실제로 확인한 상태다. 실행자는 이걸 다시 확인할 필요가 없다.

`./gradlew record`는 **이미 돌아간다.** 깨끗한 체크아웃에서 실행하면
`web/public/data/`에 파일 넷이 생기고 `ARENA_EXIT_CODE=0`이 나온다. 지금
저장소에 등록된 세대 봇이 `Gen00Bot` 하나뿐이라 **번들에 세대가 하나밖에
없다** — 12세대짜리 화면은 계획 3(세대 루프)이 돌아야 나온다. 그래서 이
계획은 화면을 세대 루프에 묶지 않고, **가짜임이 명시된 데모 번들**(Task 2)
위에서 개발·검증한다.

실측한 wire 스키마다. 프론트엔드의 Zod 스키마는 이걸 그대로 옮긴 것이어야 한다.

```jsonc
// generations.json — GenerationStat[]
[{ "generation": 0, "botName": "Gen00Bot", "avgSurvivalTurns": 11.3,
   "occupancy": 0.0142, "suicideRate": 0.0559, "scoreRate": 0.5, "attempts": 0 }]

// roundrobin.json — 대각선은 JSON null (Task 18의 판정: "NaN" 문자열이 아니다)
{ "bots": ["Gen00Bot"], "matrix": [[null]] }

// loop-history.json — 세대 번호 문자열 → AttemptRecord[]
{ "0": [] }
// AttemptRecord: { generation, attempt, verdict, stage, failedGate, detail }
//   verdict: "PASSED" | "PROMOTED" | "REJECTED"
//   stage:   "GATE" | "CHAMPIONSHIP"
//   failedGate: "G2".."G7" | null   (GATE 반려일 때만)

// gallery.json — Replay[]
[{ "schema": 1, "matchId": "Gen00Bot-vs-Gen00Bot-seed1",
   "width": 30, "height": 30, "seed": 1, "swapped": false,
   "bot0Id": "Gen00Bot", "start0": { "x": 17, "y": 19 }, "dir0": "RIGHT",
   "bot1Id": "Gen00Bot", "start1": { "x": 5,  "y": 25 }, "dir1": "LEFT",
   "moves": "RLRLRLRLRLRL",
   "result": { "winner": 0, "turns": 6, "reason": "P1_OUT_OF_BOUNDS" },
   "hash": "sha256:a4c8..." }]
```

`moves`는 턴당 2글자다. 턴 t(1-based)에서 봇 i의 방향은
`moves[(t-1)*2 + i]`이고 문자는 `U`/`D`/`L`/`R`이다(`Direction.code()`가
enum 이름의 첫 글자를 쓴다). `winner`는 좌석 인덱스이고 `-1`이 무승부다.
`reason`은 `DeathReason` enum: `P0_OUT_OF_BOUNDS`, `P0_HIT_OWN_WALL`,
`P0_HIT_OPPONENT_WALL`, `P1_*` 셋, `HEAD_ON_COLLISION`, `BOTH_DIED`,
`MAX_TURNS`.

**번들에 아직 없는 것 셋** — 스펙이 요구하지만 `BundleBuilder`가 안 만든다.
Task 1이 셋 다 채운다.

| 없는 것 | 어느 화면이 필요로 하나 | 스펙 근거 |
|---|---|---|
| `sources/` (세대별 `bot.java` + 직전 세대 대비 diff) | 화면 4 — 코드 diff | §8.4 |
| 세대별 홀드아웃 승률 | 화면 6 — 과적합 격차 | §9.2, §6 |
| 갤러리 경기의 턴별 진단(`reach`·`loss`·`worstMoves`) | 화면 5 — 단일 경기 + 진단 | §9.2, §7 |

## 파일 구조

```
.github/workflows/ci.yml          (수정) 프론트 테스트·빌드 잡 추가 — Task 12

arena-tournament/src/main/java/arena/tournament/
  BundleBuilder.java              (수정) sources/·diagnosis.json 출력, holdout 필드 — Task 1
  GenerationStat.java             (수정) holdoutScoreRate 추가 — Task 1
  SourceBundle.java               (생성) 세대별 소스와 diff를 뽑는다 — Task 1
  MatchDiagnosis.java             (생성) 갤러리 경기의 턴별 진단 wire 타입 — Task 1
arena-api/src/main/java/arena/api/cli/
  FixtureCommand.java             (생성) 데모 번들 생성 — Task 2

web/
  package.json  next.config.ts  tsconfig.json  vitest.config.ts  tailwind.config.ts
  fixtures/data/                  커밋되는 12세대 데모 번들 — Task 2
  public/data/                    진짜 번들 (gitignore) — ./gradlew record가 쓴다
  src/
    lib/
      schema.ts                   Zod 스키마 — 번들의 유일한 타입 출처 — Task 3
      bundle.ts                   빌드 타임 로더 + 출처(진짜/데모) 표시 — Task 3
      replay.ts                   moves → 격자 상태 디코더 — Task 4
      colors.ts                   청/주황 팔레트 한 곳 — Task 5
    components/
      ArenaCanvas.tsx             격자 한 판을 그린다 — Task 5
      GalleryPanel.tsx            패널 하나 (캔버스 + 생존 턴 카운터) — Task 6
      PlaybackControls.tsx        발표자 컨트롤 — Task 6
      DataSourceBanner.tsx        데모 번들일 때 눈에 띄게 알린다 — Task 3
    app/
      layout.tsx  page.tsx        발표 셸 + 화면 전환 — Task 12
      gallery/page.tsx            화면 1 — Task 6
      curve/page.tsx              화면 2 — Task 7
      loop/page.tsx               화면 3 — Task 8
      diff/page.tsx               화면 4 — Task 9
      match/page.tsx              화면 5 — Task 10
      heatmap/page.tsx            화면 6 — Task 11
    test/
      schema.contract.test.ts     번들 스키마 계약 — Task 3
      replay.test.ts              디코더 단위 테스트 — Task 4
      replay.conformance.test.ts  디코더 ↔ 엔진 대조 — Task 4
```

---

### Task 1: 세대별 홀드아웃 승률을 번들에 싣는다

화면 6은 "심사 승률과 홀드아웃 승률의 격차"를 그린다. 그 격차가 시드
과적합의 신호이고, 스펙 §6이 홀드아웃을 그 목적으로만 둔 것이기 때문이다.
그런데 지금 `holdoutScoreRate`는 `records/gen-NN/attempt-M/championship.json`
안에만 있고 번들 어디에도 나오지 않는다 — 화면이 읽을 수 없다.

**Files:**
- Modify: `arena-tournament/src/main/java/arena/tournament/GenerationStat.java`
- Modify: `arena-tournament/src/main/java/arena/tournament/RecordStore.java`
- Modify: `arena-tournament/src/main/java/arena/tournament/BundleBuilder.java` (`buildStats`)
- Test: `arena-tournament/src/test/java/arena/tournament/RecordStoreTest.java`
- Test: `arena-tournament/src/test/java/arena/tournament/BundleBuilderTest.java`

**Interfaces:**
- Consumes: `ChallengeReport.holdoutScoreRate()` (승격 시에만 채워지고 반려 시 NaN), `RecordStore.nextAttempt(int)`
- Produces: `RecordStore.holdoutOf(int generation) -> double`, `GenerationStat`의 8번째 컴포넌트 `double holdoutScoreRate`

- [ ] **Step 1: `RecordStore.holdoutOf`의 실패하는 테스트를 쓴다**

`RecordStoreTest.java`에 붙인다. 픽스처는 이 클래스의 기존 테스트가 쓰는
`@TempDir` 패턴을 그대로 따른다.

```java
@Test
void 승격한_시도의_홀드아웃_승률을_읽는다(@TempDir Path tmp) {
    RecordStore store = new RecordStore(tmp);
    store.saveChallengeReport(3, 1, new ChallengeReport(
            "Gen03Bot", "Gen02Bot", false, 0.48, 0.60, 20, 8, 22, Double.NaN, List.of()));
    store.saveChallengeReport(3, 2, new ChallengeReport(
            "Gen03Bot", "Gen02Bot", true, 0.71, 0.60, 65, 12, 23, 0.63, List.of()));

    assertEquals(0.63, store.holdoutOf(3), 1e-9);
}

@Test
void 승격한_시도가_없으면_홀드아웃은_NaN이다(@TempDir Path tmp) {
    RecordStore store = new RecordStore(tmp);
    store.saveChallengeReport(4, 1, new ChallengeReport(
            "Gen04Bot", "Gen03Bot", false, 0.48, 0.60, 20, 8, 22, Double.NaN, List.of()));

    assertTrue(Double.isNaN(store.holdoutOf(4)), "반려만 있는 세대의 홀드아웃은 NaN이어야 한다");
}

@Test
void 기록이_아예_없는_세대의_홀드아웃도_NaN이다(@TempDir Path tmp) {
    assertTrue(Double.isNaN(new RecordStore(tmp).holdoutOf(9)));
}
```

- [ ] **Step 2: 실패를 확인한다**

Run: `./gradlew :arena-tournament:test --tests 'arena.tournament.RecordStoreTest'`
Expected: 컴파일 실패 — `cannot find symbol: method holdoutOf(int)`

- [ ] **Step 3: `RecordStore.holdoutOf`를 구현한다**

`historyOf` 바로 아래에 넣는다. `historyOf`가 이미 `championship.json`을
`ChallengeReport`로 읽고 있으므로 그 방식을 그대로 쓴다.

```java
/**
 * 세대의 홀드아웃 승률. 승격한 시도의 값이며, 승격한 시도가 없거나
 * 기록이 없으면 {@link Double#NaN}이다.
 *
 * 승격 시도는 세대당 최대 하나다(승격하는 순간 그 세대가 끝난다).
 * 그래도 마지막 것을 취하도록 쓴 이유는, 기록 디렉터리가 사람이
 * 손대는 곳이라 둘이 들어있는 상태를 예외가 아니라 "가장 나중 것이
 * 맞다"로 처리하는 편이 안전하기 때문이다 — 여기서 터지면 번들
 * 생성 전체가 하네스 오류(3)로 죽는다.
 */
public double holdoutOf(int generation) {
    double holdout = Double.NaN;

    for (int attempt = 1; attempt < nextAttempt(generation); attempt++) {
        Path championship = attemptPath(generation, attempt).resolve("championship.json");
        if (!Files.exists(championship)) continue;

        ChallengeReport r = readJson(championship, ChallengeReport.class);
        if (r.promoted()) holdout = r.holdoutScoreRate();
    }
    return holdout;
}
```

- [ ] **Step 4: 통과를 확인한다**

Run: `./gradlew :arena-tournament:test --tests 'arena.tournament.RecordStoreTest'`
Expected: PASS (신규 3개 포함)

- [ ] **Step 5: `GenerationStat`에 필드를 추가하고 `buildStats`가 채우게 한다**

```java
public record GenerationStat(
        int generation,
        String botName,
        double avgSurvivalTurns,
        double occupancy,
        double suicideRate,
        double scoreRate,
        double holdoutScoreRate,
        int attempts
) {}
```

`BundleBuilder.buildStats`의 `stats.add(...)` 호출을 고친다 — `scoreRate`
다음, `attempts` 앞에 한 줄이 들어간다.

```java
stats.add(new GenerationStat(
        gen, bot.name(),
        totalTurns / n,
        totalOccupancy / n,
        totalSuicide / n,
        Standing.of(replays, GEN_ID).scoreRate(),
        store.holdoutOf(gen),
        store.nextAttempt(gen) - 1));
```

- [ ] **Step 6: 번들에 실제로 실리는지 검증하는 테스트를 쓴다**

`BundleBuilderTest.java`에 붙인다. **JSON 문자열을 검색하지 말고 역직렬화해서
본다** — 이 저장소는 "pretty printer가 절대 내보내지 않는 문자열을 찾는
단언"에 이미 한 번 당했다.

```java
@Test
void 세대_통계에_홀드아웃_승률이_실린다(@TempDir Path tmp) throws Exception {
    Path records = tmp.resolve("records");
    Path out = tmp.resolve("data");
    RecordStore store = new RecordStore(records);
    store.saveChallengeReport(0, 1, new ChallengeReport(
            "Gen00Bot", "Gen00Bot", true, 0.72, 0.60, 66, 12, 22, 0.58, List.of()));

    BundleBuilder.build(List.of(new Gen00Bot()), new Gen00Bot(),
            1L, List.of(1L, 2L), List.of(1L, 2L), 30, 30, store, out);

    List<GenerationStat> stats = new ObjectMapper().readValue(
            out.resolve("generations.json").toFile(),
            new TypeReference<List<GenerationStat>>() {});

    assertEquals(0.58, stats.get(0).holdoutScoreRate(), 1e-9);
}

@Test
void 승격_기록이_없는_세대의_홀드아웃은_NaN으로_실린다(@TempDir Path tmp) throws Exception {
    Path out = tmp.resolve("data");
    BundleBuilder.build(List.of(new Gen00Bot()), new Gen00Bot(),
            1L, List.of(1L, 2L), List.of(1L, 2L), 30, 30,
            new RecordStore(tmp.resolve("records")), out);

    List<GenerationStat> stats = new ObjectMapper().readValue(
            out.resolve("generations.json").toFile(),
            new TypeReference<List<GenerationStat>>() {});

    assertTrue(Double.isNaN(stats.get(0).holdoutScoreRate()));
}
```

- [ ] **Step 7: 통과를 확인하고, 되돌려서 무는지 본다**

Run: `./gradlew :arena-tournament:test --tests 'arena.tournament.BundleBuilderTest'`
Expected: PASS

그 다음 `store.holdoutOf(gen)`을 `Double.NaN`으로 되돌리고 같은 명령을
돌려 `세대_통계에_홀드아웃_승률이_실린다`가 실패하는지 확인한다. 실패하지
않으면 그 테스트는 아무것도 지키지 않는 것이다. 확인 뒤 즉시 원복한다.

- [ ] **Step 8: 전체 스위트를 돌린다**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL. `GenerationStat`의 컴포넌트가 늘었으므로
`HarnessSmokeTest`나 다른 픽스처가 생성자를 부르고 있다면 함께 고친다 —
**단언을 지우지 말고 인자를 채운다.**

- [ ] **Step 9: `log.md`에 기록하고 커밋한다**

D73으로 붙인다: 홀드아웃이 `championship.json`에만 있어 화면 6이 그릴 수
없었다는 것, 승격 시도가 없으면 NaN이 그대로 실린다는 것(화면이 "격차
없음"과 "아직 승격 못 함"을 구분해야 한다는 뜻이다).

```bash
git add arena-tournament log.md
git commit -m "$(cat <<'EOF'
feat: 세대별 홀드아웃 승률을 번들에 싣는다 — 과적합 격차를 화면이 못 읽고 있었다

스펙 §6은 홀드아웃을 "심사 승률과의 격차가 시드 과적합의 신호"라는
목적으로만 둔다. 그런데 그 값이 records/gen-NN/attempt-M/championship.json
안에만 있어 화면 6이 그릴 데이터가 없었다.

승격 시도가 없는 세대는 NaN으로 싣는다. 0으로 채우면 "홀드아웃에서
0% 승률"과 "아직 승격한 시도가 없다"가 같은 그림이 된다.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: 갤러리 경기의 턴별 진단을 번들에 싣는다

화면 5는 "기계가 실수를 어떻게 짚었나"를 보여준다 — 그 경기의 어느 턴이
가장 나쁜 수였고, 왜 나빴는지(`loss` = 최선 대비 잃은 공간). 지금 번들의
`gallery.json`은 리플레이만 담고 진단 수치가 없어서 그 화면을 그릴 수 없다.

`LossAnalyzer`는 이미 필요한 것을 전부 계산한다. 이 태스크는 계산을 새로
만드는 것이 아니라 **이미 있는 계산을 wire로 내보내는 것**이다.

**Files:**
- Create: `arena-tournament/src/main/java/arena/tournament/MatchDiagnosis.java`
- Modify: `arena-tournament/src/main/java/arena/tournament/BundleBuilder.java`
- Test: `arena-tournament/src/test/java/arena/tournament/BundleBuilderTest.java`

**Interfaces:**
- Consumes: `LossAnalyzer.analyze(Replay) -> MatchMetrics` (`int[][] reach`, `int[][] loss`, `double[] occupancy`, `double[] suicideRate`; 바깥 인덱스가 봇, 안쪽이 턴), `LossAnalyzer.worstMoves(Replay, int botIndex, int limit) -> List<MoveAnalysis>` (`turn`, `chose`, `best`, `reachAfterChosen`, `reachAfterBest`, `loss`, `suicide`, `fatal`)
- Produces: `web/public/data/diagnosis.json` — `gallery.json`과 **같은 순서·같은 길이**의 배열

- [ ] **Step 1: wire 타입을 만든다**

`arena-tournament/src/main/java/arena/tournament/MatchDiagnosis.java`:

```java
package arena.tournament;

import arena.diagnostics.MoveAnalysis;

import java.util.List;

/**
 * 갤러리 경기 하나의 진단. {@code diagnosis.json}의 원소이며
 * {@code gallery.json}과 같은 순서로 같은 개수가 나간다 — 화면이
 * 인덱스로 짝지을 수 있어야 하기 때문이다.
 *
 * {@code matchId}를 함께 싣는 이유는 그 짝짓기를 화면이 검증할 수
 * 있게 하려는 것이다. 순서만 약속으로 두면 한쪽 배열이 어긋났을 때
 * 화면이 조용히 엉뚱한 경기의 진단을 그린다.
 *
 * reach·loss는 {@code [봇][턴]}이다. 턴 인덱스는 0-based이고 리플레이의
 * 턴 1이 인덱스 0이다 — {@link arena.diagnostics.MatchMetrics}의 배열을
 * 그대로 내보내므로 그쪽 규약을 따른다. 반면 {@link MoveAnalysis#turn()}은
 * 1-based다. 두 규약이 한 파일에 섞여 나가므로 화면 쪽에서 반드시
 * 구분해야 하고, 그래서 이 javadoc이 그것을 명시한다.
 */
public record MatchDiagnosis(
        String matchId,
        int[][] reach,
        int[][] loss,
        double[] occupancy,
        double[] suicideRate,
        List<MoveAnalysis> worstMoves0,
        List<MoveAnalysis> worstMoves1
) {}
```

- [ ] **Step 2: 실패하는 테스트를 쓴다**

```java
@Test
void 진단_파일은_갤러리와_같은_순서로_짝지어진다(@TempDir Path tmp) throws Exception {
    Path out = tmp.resolve("data");
    BundleBuilder.build(List.of(new Gen00Bot()), new Gen00Bot(),
            1L, List.of(1L, 2L), List.of(1L, 2L), 30, 30,
            new RecordStore(tmp.resolve("records")), out);

    ObjectMapper mapper = new ObjectMapper();
    List<Replay> gallery = mapper.readValue(out.resolve("gallery.json").toFile(),
            new TypeReference<List<Replay>>() {});
    List<MatchDiagnosis> diagnosis = mapper.readValue(out.resolve("diagnosis.json").toFile(),
            new TypeReference<List<MatchDiagnosis>>() {});

    assertEquals(gallery.size(), diagnosis.size(), "진단이 갤러리와 개수가 다르다");
    for (int i = 0; i < gallery.size(); i++) {
        assertEquals(gallery.get(i).matchId(), diagnosis.get(i).matchId(),
                i + "번째 진단이 다른 경기의 것이다");
    }
}

@Test
void 진단의_reach는_봇당_턴수만큼_있다(@TempDir Path tmp) throws Exception {
    Path out = tmp.resolve("data");
    BundleBuilder.build(List.of(new Gen00Bot()), new Gen00Bot(),
            1L, List.of(1L, 2L), List.of(1L, 2L), 30, 30,
            new RecordStore(tmp.resolve("records")), out);

    ObjectMapper mapper = new ObjectMapper();
    Replay r = mapper.readValue(out.resolve("gallery.json").toFile(),
            new TypeReference<List<Replay>>() {}).get(0);
    MatchDiagnosis d = mapper.readValue(out.resolve("diagnosis.json").toFile(),
            new TypeReference<List<MatchDiagnosis>>() {}).get(0);

    assertEquals(2, d.reach().length, "reach의 바깥 인덱스는 봇이어야 한다");
    assertEquals(r.result().turns(), d.reach()[0].length,
            "reach의 안쪽 길이가 경기 턴 수와 다르다");
    assertEquals(r.result().turns(), d.loss()[1].length);
}
```

- [ ] **Step 3: 실패를 확인한다**

Run: `./gradlew :arena-tournament:test --tests 'arena.tournament.BundleBuilderTest'`
Expected: FAIL — `diagnosis.json` 파일이 없어 `FileNotFoundException`

- [ ] **Step 4: `BundleBuilder`가 진단을 쓰게 한다**

`build`의 `writeJson(outputDir.resolve("gallery.json"), gallery);` 바로 뒤에
한 줄을 넣는다.

```java
writeJson(outputDir.resolve("diagnosis.json"), buildDiagnosis(gallery));
```

그리고 `buildGallery` 아래에 메서드를 더한다.

```java
/**
 * 갤러리 경기의 진단. {@code gallery}를 그대로 순회하므로 순서와
 * 개수가 필연적으로 같아진다 — 두 배열을 각각 만들어 "같은 순서일
 * 것"을 약속으로 두면 언젠가 어긋난다.
 *
 * worstMoves의 limit 3은 화면이 쓰는 값이다. 화면 5가 "가장 나쁜 수
 * 몇 개"를 짚어 보여주므로 전부 실을 이유가 없고, 전부 실으면
 * 900턴짜리 경기에서 이 파일이 리플레이보다 커진다.
 */
private static List<MatchDiagnosis> buildDiagnosis(List<Replay> gallery) {
    List<MatchDiagnosis> diagnosis = new ArrayList<>();
    for (Replay r : gallery) {
        MatchMetrics m = LossAnalyzer.analyze(r);
        diagnosis.add(new MatchDiagnosis(
                r.matchId(),
                m.reach(), m.loss(), m.occupancy(), m.suicideRate(),
                LossAnalyzer.worstMoves(r, 0, 3),
                LossAnalyzer.worstMoves(r, 1, 3)));
    }
    return diagnosis;
}
```

- [ ] **Step 5: 통과를 확인한다**

Run: `./gradlew :arena-tournament:test --tests 'arena.tournament.BundleBuilderTest'`
Expected: PASS

- [ ] **Step 6: 재현 검증이 새 파일까지 덮는지 확인한다**

`record --verify`의 ② 층은 `outputDir` 전체를 다이제스트로 대조하므로
새 파일도 자동으로 들어간다. 그게 정말인지 실제로 확인한다.

Run:
```bash
./gradlew record
./gradlew record -Pverify | grep -o 'ARENA_EXIT_CODE=[0-3]'
```
Expected: `ARENA_EXIT_CODE=0`

그 다음 `web/public/data/diagnosis.json`의 숫자 하나를 손으로 고치고 다시
`./gradlew record -Pverify`를 돌린다.
Expected: `ARENA_EXIT_CODE=1` — 새 파일이 재현 검증 범위 안에 있다는 증거다.
**Gradle은 `BUILD SUCCESSFUL`로 끝난다**(규칙서 §8) — 반드시 그 줄을 읽을 것.
확인 뒤 `./gradlew record`로 원복한다.

- [ ] **Step 7: 전체 스위트와 커밋**

Run: `./gradlew test` → BUILD SUCCESSFUL

`log.md`에 D74로 남긴다: 진단을 갤러리와 별도 배열로 내보내되 `matchId`를
함께 실어 화면이 짝짓기를 검증할 수 있게 한 이유, 그리고 reach/loss의 턴
인덱스가 0-based인데 `MoveAnalysis.turn`은 1-based라 한 파일에 두 규약이
섞여 있다는 것.

```bash
git add arena-tournament log.md
git commit -m "$(cat <<'EOF'
feat: 갤러리 경기의 턴별 진단을 번들에 싣는다 — 화면 5가 읽을 것이 없었다

LossAnalyzer가 이미 계산하던 것을 wire로 내보낼 뿐, 계산을 새로 만들지
않는다. 갤러리 배열을 그대로 순회해 만들므로 순서와 개수가 필연적으로
같아진다 — 두 배열을 각각 만들고 "같은 순서일 것"을 약속으로 두면
언젠가 어긋나고, 그때 화면은 조용히 엉뚱한 경기의 진단을 그린다.
matchId를 함께 실어 화면이 그 짝짓기를 검증할 수 있게 했다.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: 세대별 채택 소스를 번들에 싣는다

화면 4("이번 세대는 무엇을 배웠나")가 읽을 것이 지금 아무것도 없다.
스펙 §8.4는 `sources/`에 "세대별 `bot.java` 원문 + 직전 세대 대비 diff"를
두라고 한다.

**설계 판단 — diff는 백엔드가 만들지 않고 원문 둘만 내보낸다.** 실행자는
이 판정을 뒤집지 말고 그대로 따른다. 사유: (a) diff는 하네스가 *판정한*
수치가 아니라 두 텍스트의 표현이므로 R1이 말하는 "프론트가 재계산하면
안 되는 값"에 해당하지 않는다, (b) 자바에 LCS diff를 손으로 짜 넣으면
검증되지 않은 알고리즘이 번들의 바이트 동일성 경로에 들어온다,
(c) 프론트엔드에는 검증된 diff 라이브러리가 있다. **틀렸다면 비용은**
diff 결과가 `record --verify`의 대조 범위 밖에 남는 것이다 — 대신 원문
두 개는 범위 안에 있으므로, 원문이 같으면 diff도 같다.

**Files:**
- Create: `arena-tournament/src/main/java/arena/tournament/SourceBundle.java`
- Modify: `arena-tournament/src/main/java/arena/tournament/BundleBuilder.java`
- Modify: `arena-tournament/src/main/java/arena/tournament/RecordStore.java`
- Test: `arena-tournament/src/test/java/arena/tournament/SourceBundleTest.java`

**Interfaces:**
- Consumes: `RecordStore.historyOf(int) -> List<AttemptRecord>` (`verdict`는 `"PASSED"`/`"PROMOTED"`/`"REJECTED"`), `RecordStore` 안의 `records/gen-NN/attempt-M/bot.java`
- Produces: `RecordStore.acceptedSourceOf(int generation) -> Optional<String>`, `SourceBundle.write(List<Bot>, RecordStore, Path outputDir)`, 산출물 `sources/gen-NN.java`와 `sources/index.json`

- [ ] **Step 1: `acceptedSourceOf`의 실패하는 테스트를 쓴다**

`RecordStoreTest.java`에 붙인다. 요점은 **반려된 시도의 소스를 집으면 안
된다**는 것이다 — 반려 코드는 보존하되(BRIEF §8) 화면 4가 "이 세대의
코드"로 보여줄 것은 채택된 쪽이다.

```java
@Test
void 채택된_시도의_소스를_고른다(@TempDir Path tmp) {
    RecordStore store = new RecordStore(tmp);
    store.saveGateReport(2, 1, "class 반려된놈 {}",
            new GateReport("Gen02Bot", false, "G4", "예외를 던졌다", List.of()));
    store.saveGateReport(2, 2, "class 채택된놈 {}",
            new GateReport("Gen02Bot", true, null, "", List.of()));

    assertEquals("class 채택된놈 {}", store.acceptedSourceOf(2).orElseThrow());
}

@Test
void 채택된_시도가_없으면_비어있다(@TempDir Path tmp) {
    RecordStore store = new RecordStore(tmp);
    store.saveGateReport(2, 1, "class 반려된놈 {}",
            new GateReport("Gen02Bot", false, "G4", "예외를 던졌다", List.of()));

    assertTrue(store.acceptedSourceOf(2).isEmpty(),
            "반려만 있는 세대는 채택된 소스가 없어야 한다");
}
```

> `GateReport`의 실제 컴포넌트 순서는 `arena-gate`의 정의를 열어 확인하고
> 맞춘다. 위 호출이 컴파일되지 않으면 **테스트를 지우지 말고 인자를
> 실제 시그니처에 맞춘다.**

- [ ] **Step 2: 실패를 확인한다**

Run: `./gradlew :arena-tournament:test --tests 'arena.tournament.RecordStoreTest'`
Expected: 컴파일 실패 — `cannot find symbol: method acceptedSourceOf(int)`

- [ ] **Step 3: `acceptedSourceOf`를 구현한다**

```java
/**
 * 세대가 채택한 봇의 소스. 관문을 통과했거나 승격한 시도의
 * {@code bot.java}이며, 그런 시도가 없으면 비어 있다.
 *
 * 반려된 시도의 소스는 디스크에 그대로 남는다(BRIEF §8 — 실패
 * 횟수가 보이는 편이 발표에 유리하다). 여기서 고르지 않을 뿐이다:
 * 화면 4가 "이 세대의 코드"로 보여줄 것은 채택된 쪽이고, 반려된
 * 코드는 화면 3(루프 타임라인)의 소관이다.
 */
public Optional<String> acceptedSourceOf(int generation) {
    for (AttemptRecord record : historyOf(generation)) {
        if (record.verdict().equals("REJECTED")) continue;

        Path source = attemptPath(generation, record.attempt()).resolve("bot.java");
        if (Files.exists(source)) return Optional.of(read(source));
    }
    return Optional.empty();
}
```

`read(Path)`가 아직 없으면 `write`의 짝으로 만든다 — **UTF-8을 명시한다.**
이 저장소는 이미 기본 문자셋 의존으로 한 번 지적받았다(D71-②).

```java
private static String read(Path path) {
    try {
        return Files.readString(path, StandardCharsets.UTF_8);
    } catch (IOException e) {
        throw new UncheckedIOException("소스를 읽을 수 없다: " + path, e);
    }
}
```

- [ ] **Step 4: 통과를 확인한다**

Run: `./gradlew :arena-tournament:test --tests 'arena.tournament.RecordStoreTest'`
Expected: PASS

- [ ] **Step 5: `SourceBundle`의 실패하는 테스트를 쓴다**

`arena-tournament/src/test/java/arena/tournament/SourceBundleTest.java`:

```java
package arena.tournament;

import arena.bots.Bot;
import arena.bots.gen.Gen00Bot;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SourceBundleTest {

    @Test
    void 채택된_소스를_세대별_파일로_쓴다(@TempDir Path tmp) throws Exception {
        RecordStore store = new RecordStore(tmp.resolve("records"));
        store.saveGateReport(0, 1, "class Gen00Bot {}",
                new GateReport("Gen00Bot", true, null, "", List.of()));

        Path out = tmp.resolve("data");
        SourceBundle.write(List.of(new Gen00Bot()), store, out);

        assertEquals("class Gen00Bot {}",
                Files.readString(out.resolve("sources/gen-00.java"), StandardCharsets.UTF_8));
    }

    @Test
    void 소스가_없는_세대는_인덱스에_available_false로_남는다(@TempDir Path tmp) throws Exception {
        Path out = tmp.resolve("data");
        SourceBundle.write(List.of(new Gen00Bot()), new RecordStore(tmp.resolve("records")), out);

        List<Map<String, Object>> index = new ObjectMapper().readValue(
                out.resolve("sources/index.json").toFile(),
                new TypeReference<List<Map<String, Object>>>() {});

        assertEquals(1, index.size());
        assertEquals(Boolean.FALSE, index.get(0).get("available"));
        assertFalse(Files.exists(out.resolve("sources/gen-00.java")),
                "소스가 없으면 빈 파일을 만들지 않는다 — 화면이 '빈 코드'와 '기록 없음'을 구분해야 한다");
    }

    @Test
    void 인덱스는_세대_순서를_그대로_따른다(@TempDir Path tmp) throws Exception {
        RecordStore store = new RecordStore(tmp.resolve("records"));
        for (int gen = 0; gen < 3; gen++) {
            store.saveGateReport(gen, 1, "class G" + gen + " {}",
                    new GateReport("Gen0" + gen + "Bot", true, null, "", List.of()));
        }

        Path out = tmp.resolve("data");
        SourceBundle.write(List.of(new Gen00Bot(), new Gen00Bot(), new Gen00Bot()), store, out);

        List<Map<String, Object>> index = new ObjectMapper().readValue(
                out.resolve("sources/index.json").toFile(),
                new TypeReference<List<Map<String, Object>>>() {});

        assertEquals(List.of(0, 1, 2), index.stream().map(e -> e.get("generation")).toList());
    }
}
```

- [ ] **Step 6: 실패를 확인한다**

Run: `./gradlew :arena-tournament:test --tests 'arena.tournament.SourceBundleTest'`
Expected: 컴파일 실패 — `cannot find symbol: class SourceBundle`

- [ ] **Step 7: `SourceBundle`을 구현한다**

```java
package arena.tournament;

import arena.bots.Bot;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 세대별 채택 소스를 번들로 내보낸다 (스펙 §8.4의 {@code sources/}).
 *
 * 직전 세대 대비 diff는 여기서 만들지 않는다 — 원문 둘을 내보내고
 * 화면이 빌드 타임에 계산한다. diff는 하네스가 판정한 수치가 아니라
 * 두 텍스트의 표현이고, 검증되지 않은 LCS 구현을 번들의 바이트 동일성
 * 경로에 들이지 않기 위해서다. 원문이 같으면 diff도 같으므로
 * {@code record --verify}가 원문을 지키는 것으로 충분하다.
 */
public final class SourceBundle {

    private SourceBundle() {}

    public static void write(List<Bot> generations, RecordStore store, Path outputDir) {
        Path sources = outputDir.resolve("sources");
        createDirectories(sources);

        List<Map<String, Object>> index = new ArrayList<>();

        for (int gen = 0; gen < generations.size(); gen++) {
            // Locale.ROOT: 이 문자열이 파일 이름이 된다. 기본 숫자 체계가
            // latn이 아닌 로케일에서는 %02d가 비ASCII 숫자를 내고, 그러면
            // 파일 이름 자체가 달라져 화면이 소스를 못 찾는다.
            String name = String.format(Locale.ROOT, "gen-%02d", gen);
            Optional<String> source = store.acceptedSourceOf(gen);

            // LinkedHashMap: 키 순서를 고정해 이 파일이 실행마다 바이트
            // 단위로 같게 나오도록 한다 (BundleBuilder.buildHistory와 같은 이유).
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("generation", gen);
            entry.put("botName", generations.get(gen).name());
            entry.put("available", source.isPresent());
            entry.put("file", source.isPresent() ? "sources/" + name + ".java" : null);
            index.add(entry);

            // 소스가 없으면 빈 파일을 만들지 않는다. 만들면 화면이
            // "코드가 비어 있다"와 "기록이 없다"를 구분할 수 없다.
            source.ifPresent(text -> writeString(sources.resolve(name + ".java"), text));
        }
        BundleBuilder.writeJson(sources.resolve("index.json"), index);
    }

    private static void createDirectories(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException("디렉터리를 만들 수 없다: " + dir, e);
        }
    }

    private static void writeString(Path path, String text) {
        try {
            Files.writeString(path, text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("소스를 쓸 수 없다: " + path, e);
        }
    }
}
```

`BundleBuilder.writeJson`은 지금 private이다. **public으로 열지 말고
package-private으로 낮춘다** — 같은 패키지의 `SourceBundle`만 쓰면 되고,
공개하면 pretty printer·MAPPER 설정을 우회하는 다른 호출자가 생길 수 있다.

- [ ] **Step 8: 통과를 확인한다**

Run: `./gradlew :arena-tournament:test --tests 'arena.tournament.SourceBundleTest'`
Expected: PASS (3개)

- [ ] **Step 9: `BundleBuilder.build`가 부르게 한다**

`build`의 마지막 `writeJson(...roundrobin.json...)` 뒤에:

```java
SourceBundle.write(generations, store, outputDir);
```

- [ ] **Step 10: 전체 스위트 + 재현 검증 + 커밋**

Run: `./gradlew test` → BUILD SUCCESSFUL
Run: `./gradlew record && ./gradlew record -Pverify | grep -o 'ARENA_EXIT_CODE=[0-3]'` → `ARENA_EXIT_CODE=0`

`log.md`에 D75로 남긴다 — 특히 **diff를 백엔드에서 만들지 않기로 한 판정과
그 비용**, 그리고 소스가 없는 세대에 빈 파일을 만들지 않는 이유.

```bash
git add arena-tournament log.md
git commit -m "$(cat <<'EOF'
feat: 세대별 채택 소스를 번들에 싣는다 — 화면 4가 읽을 것이 없었다

스펙 §8.4의 sources/를 채운다. 채택된 시도(PASSED·PROMOTED)의 bot.java만
고른다 — 반려 코드는 디스크에 그대로 남지만(BRIEF §8) 그건 화면 3의
소관이고, 화면 4가 "이 세대의 코드"로 보여줄 것은 채택된 쪽이다.

diff는 만들지 않고 원문만 내보낸다. diff는 하네스가 판정한 수치가 아니라
두 텍스트의 표현이고, 검증되지 않은 LCS 구현을 번들의 바이트 동일성 경로에
들이지 않으려는 것이다. 원문이 같으면 diff도 같다.

소스가 없는 세대에 빈 파일을 만들지 않는다. 만들면 화면이 "코드가 비어
있다"와 "기록이 없다"를 구분할 수 없다.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 4: 데모 번들 생성기 — 화면을 세대 루프에 묶지 않는다

지금 등록된 세대 봇은 `Gen00Bot` 하나이고, 그 경기는 **6턴 만에 끝난다.**
그 번들로는 12패널 갤러리도, 개선 곡선도, 루프 타임라인도 만들 수 없고
**리뷰어가 화면이 맞는지 판정할 수도 없다.** 계획 3(세대 루프)이 끝나야
화면을 만들 수 있다면 두 계획이 직렬로 묶인다.

그래서 데모 번들을 만든다. **경기 자체는 진짜다** — 실제 엔진이 실제 봇을
돌린 결과이므로 리플레이·해시·진단이 전부 진짜다. 가짜인 것은 "이 봇들이
세대 루프가 만든 것"이라는 부분뿐이고, 그것을 `meta.json`과 화면 배너로
명시한다.

**Files:**
- Create: `arena-api/src/main/java/arena/api/cli/FixtureCommand.java`
- Modify: `arena-api/src/main/java/arena/api/ArenaApplication.java` (`fixture` 명령 분기)
- Modify: `build.gradle` (`fixture` 태스크)
- Modify: `arena-tournament/src/main/java/arena/tournament/BundleBuilder.java` (`meta.json`)
- Test: `arena-api/src/test/java/arena/api/cli/FixtureCommandTest.java`

**Interfaces:**
- Consumes: `BundleBuilder.build(List<Bot>, Bot, long gallerySeed, List<Long> judgingSeeds, List<Long> roundRobinSeeds, int width, int height, RecordStore, Path)`, `RecordStore.saveGateReport(int, int, String, GateReport)`, `RecordStore.saveChallengeReport(int, int, ChallengeReport)`
- Produces: `FixtureCommand.run(Path outputDir) -> int` (종료 코드), 산출물 `web/fixtures/data/` 전체

- [ ] **Step 1: 데모 봇을 정의하는 실패하는 테스트를 쓴다**

데모 봇은 "안전한 방향 중 앞을 N수까지 내다보고 고르는" 벽회피봇이다.
N을 0..11로 두면 실제로 생존 턴이 단조롭게 늘어난다 — 개선 곡선이
조작이 아니라 **측정 결과**가 된다.

```java
@Test
void 데모_봇은_깊이가_깊을수록_오래_산다(@TempDir Path tmp) {
    // 깊이 0(즉사만 피함)과 깊이 6을 같은 시드 20개에서 붙여, 깊은 쪽이
    // 평균 생존 턴에서 앞서는지 본다. 이게 거짓이면 데모 번들의
    // 개선 곡선은 우연이고, 화면이 증명하는 것이 아무것도 없다.
    double shallow = averageTurns(FixtureCommand.demoBot(0), 20);
    double deep = averageTurns(FixtureCommand.demoBot(6), 20);

    assertTrue(deep > shallow * 1.5,
            "깊이 6(" + deep + ")이 깊이 0(" + shallow + ")보다 확실히 오래 살아야 한다");
}
```

`averageTurns`는 이 테스트 클래스의 private 헬퍼로 둔다 — `Match.play`로
같은 상대(깊이 0)와 시드 1..n을 돌려 `result().turns()`의 평균을 낸다.

- [ ] **Step 2: 실패를 확인한다**

Run: `./gradlew :arena-api:test --tests 'arena.api.cli.FixtureCommandTest'`
Expected: 컴파일 실패 — `cannot find symbol: method demoBot(int)`

- [ ] **Step 3: 데모 봇을 구현한다**

`FixtureCommand` 안에 둔다. **`BotRegistry`에 등록하지 않는다** — 이건
세대 봇이 아니고 관문의 심판 대상도 아니다. 등록하면 챔피언전과 관문에
섞여 들어가 진짜 기록을 오염시킨다.

```java
/**
 * 데모용 봇. 안전한 방향 중 {@code depth}수까지 내다보고 가장 넓은
 * 쪽을 고른다. depth가 커질수록 실제로 오래 살아남으므로 데모
 * 번들의 개선 곡선이 조작이 아니라 측정 결과가 된다.
 *
 * BotRegistry에 등록하지 않는다 — 세대 봇이 아니고 관문의 심판
 * 대상도 아니다. 등록하면 챔피언전과 관문에 섞여 진짜 기록을
 * 오염시킨다.
 */
static Bot demoBot(int depth) {
    return new Bot() {
        @Override public String name() {
            return String.format(Locale.ROOT, "Demo%02dBot", depth);
        }

        @Override public Direction move(GameView view) {
            Direction best = null;
            int bestRoom = -1;

            // 고정 순서로 순회한다 — 같은 점수일 때 어느 쪽을 고를지가
            // 결정되어야 R1이 지켜진다.
            for (Direction d : Direction.values()) {
                if (view.isDeadly(d)) continue;

                int room = lookahead(view, view.myHead().move(d), d, depth);
                if (room > bestRoom) {
                    bestRoom = room;
                    best = d;
                }
            }
            // 살 길이 없으면 아무 방향이나 낸다. null을 내면 G4 위반이다.
            return best != null ? best : view.myDir();
        }
    };
}
```

`lookahead`는 `FixtureCommand`의 private static 메서드로, 벽·경계만 보고
`depth`만큼 재귀적으로 "갈 수 있는 칸 수"를 센다. **`GameView`의 `wall`
배열을 고쳐 쓰지 않는다** — 방문 표시는 지역 `boolean[][]`에 한다.

- [ ] **Step 4: 통과를 확인한다**

Run: `./gradlew :arena-api:test --tests 'arena.api.cli.FixtureCommandTest'`
Expected: PASS — 출력에 실제 평균 생존 턴이 찍힌다. **이 수치를 리포트에 적는다.**

- [ ] **Step 5: `meta.json`을 추가한다**

`BundleBuilder.build`에 파라미터를 하나 더한다: `boolean demo`. 그리고

```java
Map<String, Object> meta = new LinkedHashMap<>();
meta.put("demo", demo);
meta.put("generations", generations.size());
meta.put("gallerySeed", gallerySeed);
writeJson(outputDir.resolve("meta.json"), meta);
```

기존 호출자(`RecordCommand`)는 `false`를 넘긴다. **기본값 오버로드를 만들지
않는다** — 이 값을 빠뜨렸을 때 데모 번들이 진짜로 표시되는 쪽으로 조용히
기울면 안 된다. 호출자가 매번 명시하게 한다.

> **이 변경은 Task 1~3이 쓴 테스트를 컴파일 실패로 만든다.** 그 테스트들이
> `BundleBuilder.build(...)`를 옛 시그니처로 부르기 때문이다. **단언을
> 지우거나 테스트를 삭제하지 말고 인자에 `false`를 더한다** — 그 테스트들은
> 홀드아웃·진단·소스가 번들에 실린다는 것을 지키고 있고, 이 태스크는 그
> 성질과 아무 관계가 없다. 고친 뒤 `./gradlew test`가 다시 초록인지
> 확인하고 리포트에 몇 개를 고쳤는지 적는다.

- [ ] **Step 6: `FixtureCommand.run`을 구현하고 테스트한다**

12세대(깊이 0..11)를 만들고, 각 세대에 **그럴듯한 시도 이력**을 `RecordStore`로
써 넣은 뒤 `BundleBuilder.build(..., demo=true, ...)`를 부른다. 이력은
합성이다 — 세대마다 1~3회 시도, 일부는 `G4`/`G5`/`G6` 반려, 마지막이 승격.
반려 사유를 골고루 섞는 이유는 화면 3이 **반려 사유별 색**을 그리기 때문에
색이 하나뿐이면 그 화면을 검증할 수 없어서다.

```java
@Test
void 데모_번들은_12세대를_담고_스스로_데모라고_밝힌다(@TempDir Path tmp) throws Exception {
    assertEquals(0, FixtureCommand.run(tmp));

    ObjectMapper mapper = new ObjectMapper();
    Map<String, Object> meta = mapper.readValue(
            tmp.resolve("meta.json").toFile(), new TypeReference<Map<String, Object>>() {});
    assertEquals(Boolean.TRUE, meta.get("demo"),
            "데모 번들이 스스로를 진짜라고 말하면 안 된다");
    assertEquals(12, meta.get("generations"));

    List<GenerationStat> stats = mapper.readValue(
            tmp.resolve("generations.json").toFile(),
            new TypeReference<List<GenerationStat>>() {});
    assertEquals(12, stats.size());
}

@Test
void 데모_번들의_개선_곡선은_실제로_올라간다(@TempDir Path tmp) throws Exception {
    FixtureCommand.run(tmp);
    List<GenerationStat> stats = new ObjectMapper().readValue(
            tmp.resolve("generations.json").toFile(),
            new TypeReference<List<GenerationStat>>() {});

    // R3의 합격선(스펙 §13)은 Gen 0 대비 10배다. 데모가 그 선을 넘지
    // 못하면 갤러리 화면이 R3을 증명하는 그림을 못 만든다.
    double gen0 = stats.get(0).avgSurvivalTurns();
    double last = stats.get(11).avgSurvivalTurns();
    assertTrue(last >= gen0 * 10,
            "데모 곡선이 R3 합격선(10배)에 못 미친다: " + gen0 + " → " + last);
}

@Test
void 루프_이력에_반려_사유가_여러_종류_들어있다(@TempDir Path tmp) throws Exception {
    FixtureCommand.run(tmp);
    Map<String, List<AttemptRecord>> history = new ObjectMapper().readValue(
            tmp.resolve("loop-history.json").toFile(),
            new TypeReference<Map<String, List<AttemptRecord>>>() {});

    Set<String> gates = history.values().stream()
            .flatMap(List::stream)
            .map(AttemptRecord::failedGate)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

    assertTrue(gates.size() >= 3,
            "반려 사유가 " + gates + " 뿐이면 화면 3의 사유별 색을 검증할 수 없다");
}
```

> `데모_번들의_개선_곡선은_실제로_올라간다`가 실패하면 **단언을 낮추지
> 말고 깊이 범위를 넓히거나 데모 봇을 강하게 만든다.** 이 저장소의
> 규칙(§11)은 기준값을 통과하지 못한다는 이유로 낮추지 않는 것이고,
> 여기서 10배는 스펙 §13이 정한 R3 합격선이다.

- [ ] **Step 7: CLI와 Gradle에 붙인다**

`ArenaApplication`에 `fixture` 분기를 더하고(출력 디렉터리는
`web/fixtures/data`), `build.gradle`에 태스크를 등록한다.

```groovy
tasks.register('fixture', JavaExec) {
    arenaExec(it)
    description = 'fixture — web/fixtures/data 에 데모 번들 생성 (진짜 기록이 아니다). ' +
            '진짜 종료 코드는 출력의 ARENA_EXIT_CODE=<n> 줄에 있다'
    args = ['fixture']
}
```

- [ ] **Step 8: 실제로 돌리고 커밋한다**

Run: `./gradlew fixture | grep -o 'ARENA_EXIT_CODE=[0-3]'` → `ARENA_EXIT_CODE=0`
Run: `./gradlew test` → BUILD SUCCESSFUL

**`web/fixtures/data/`는 커밋한다** — 프론트엔드 테스트가 이 파일들을 읽고,
CI에서 `./gradlew fixture`를 먼저 돌리지 않아도 프론트 테스트가 돌게
하기 위해서다. `.gitignore`가 `web/public/data/`만 무시하는지 확인한다.

`log.md`에 D76: 화면을 세대 루프에 묶지 않기로 한 판정, 경기는 진짜이고
가짜인 것은 "세대 루프가 만들었다"는 부분뿐이라는 것, `meta.json`의
`demo` 플래그에 기본값 오버로드를 두지 않은 이유.

```bash
git add arena-api arena-tournament build.gradle web/fixtures log.md
git commit -m "$(cat <<'EOF'
feat: 데모 번들 생성기 — 화면 개발을 세대 루프에 묶지 않는다

지금 등록된 세대 봇은 Gen00Bot 하나이고 그 경기는 6턴에 끝난다. 그
번들로는 12패널 갤러리도 개선 곡선도 만들 수 없고, 리뷰어가 화면이 맞는지
판정할 수도 없다. 계획 3이 끝나야 화면을 만들 수 있다면 두 계획이 직렬로
묶인다.

경기 자체는 진짜다 — 실제 엔진이 실제 봇(깊이 0~11의 벽회피봇)을 돌린
결과라 리플레이·해시·진단이 전부 진짜이고, 개선 곡선도 조작이 아니라
측정 결과다. 가짜인 것은 "이 봇들이 세대 루프가 만든 것"이라는 부분뿐이고
meta.json의 demo 플래그와 화면 배너가 그것을 밝힌다.

demo 플래그에 기본값 오버로드를 두지 않았다 — 빠뜨렸을 때 데모 번들이
진짜로 표시되는 쪽으로 조용히 기울면 안 된다.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 5: Next.js 스캐폴딩 + 번들 스키마 계약

프론트엔드의 기반을 놓는다. 이 태스크가 끝나면 화면은 아직 없지만
**번들이 예상한 모양인지 기계가 판정한다.** 스펙 §11 T5가 프론트엔드에
요구하는 테스트가 정확히 이것 하나다.

**Files:**
- Create: `web/package.json`, `web/next.config.ts`, `web/tsconfig.json`, `web/vitest.config.ts`, `web/tailwind.config.ts`, `web/postcss.config.mjs`, `web/.gitignore`
- Create: `web/src/lib/schema.ts`, `web/src/lib/bundle.ts`, `web/src/components/DataSourceBanner.tsx`
- Create: `web/src/app/layout.tsx`, `web/src/app/page.tsx`, `web/src/app/globals.css`
- Test: `web/src/test/schema.contract.test.ts`

**Interfaces:**
- Consumes: Task 4가 만든 `web/fixtures/data/` (전 파일), Task 1~3이 더한 필드
- Produces: `loadBundle() -> Bundle`, 타입 `Bundle`·`Replay`·`GenerationStat`·`AttemptRecord`·`MatchDiagnosis`·`RoundRobinData`·`SourceIndexEntry`·`BundleMeta` (전부 `schema.ts`가 Zod로 정의하고 `z.infer`로 뽑는다 — **손으로 쓴 interface를 따로 두지 않는다**)

- [ ] **Step 1: 프로젝트를 만들고 의존성을 넣는다**

```bash
cd web
npm init -y
npm i next@15 react@19 react-dom@19 zod
npm i -D typescript @types/react @types/node vitest @vitejs/plugin-react tailwindcss @tailwindcss/postcss postcss
```

`web/package.json`의 `scripts`:

```json
{
  "scripts": {
    "dev": "ARENA_BUNDLE=fixtures/data next dev",
    "build": "ARENA_BUNDLE=public/data next build",
    "build:demo": "ARENA_BUNDLE=fixtures/data next build",
    "test": "ARENA_BUNDLE=fixtures/data vitest run"
  }
}
```

**`ARENA_BUNDLE`에 기본값을 두지 않는다.** 기본값을 두면 발표용 빌드가
조용히 데모 번들로 나갈 수 있다. 스크립트가 매번 명시하고, 값이 없으면
빌드가 즉시 실패한다.

`web/next.config.ts`:

```ts
import type { NextConfig } from 'next';

// R4: 발표 당일 백엔드가 없다. 정적 export만 만든다 —
// API 라우트도 서버 액션도 런타임 데이터 요청도 두지 않는다.
const nextConfig: NextConfig = {
  output: 'export',
  images: { unoptimized: true },
};

export default nextConfig;
```

`web/.gitignore`: `node_modules/`, `.next/`, `out/`.
루트 `.gitignore`에 이미 `web/node_modules/`·`web/.next/`·`web/public/data/`·
`web/public/data-verify/`가 있다 — **`web/fixtures/`는 무시하지 않는다.**

- [ ] **Step 2: 실패하는 계약 테스트를 쓴다**

`web/src/test/schema.contract.test.ts`. 이 테스트가 이 태스크의 존재 이유다.

```ts
import { describe, it, expect } from 'vitest';
import { loadBundle } from '../lib/bundle';

describe('번들 스키마 계약', () => {
  it('데모 번들 전체가 스키마를 통과한다', () => {
    // loadBundle이 Zod로 parse 하므로, 통과한다는 것 자체가 계약이
    // 지켜졌다는 뜻이다. 백엔드가 필드를 바꾸면 여기서 죽는다.
    const bundle = loadBundle();
    expect(bundle.generations.length).toBeGreaterThan(0);
  });

  it('갤러리와 진단이 matchId로 짝지어진다', () => {
    const { gallery, diagnosis } = loadBundle();
    expect(diagnosis.length).toBe(gallery.length);
    gallery.forEach((replay, i) => {
      expect(diagnosis[i].matchId).toBe(replay.matchId);
    });
  });

  it('라운드로빈 행렬은 정사각이고 대각선이 null이다', () => {
    const { roundRobin } = loadBundle();
    const n = roundRobin.bots.length;
    expect(roundRobin.matrix.length).toBe(n);
    roundRobin.matrix.forEach((row, i) => {
      expect(row.length).toBe(n);
      expect(row[i]).toBeNull();
    });
  });

  it('루프 이력의 키가 세대 번호를 빠짐없이 덮는다', () => {
    const { generations, loopHistory } = loadBundle();
    generations.forEach((g) => {
      expect(loopHistory[String(g.generation)]).toBeDefined();
    });
  });

  it('소스 인덱스가 세대와 같은 길이다', () => {
    const { generations, sources } = loadBundle();
    expect(sources.length).toBe(generations.length);
  });

  it('알 수 없는 필드가 들어오면 거부한다', () => {
    // 스키마가 strict가 아니면 백엔드가 필드 이름을 바꿔도 조용히
    // undefined가 흘러 화면이 빈 값을 그린다. 그건 계약 테스트가 아니다.
    const { GenerationStatSchema } = require('../lib/schema');
    expect(() =>
      GenerationStatSchema.parse({
        generation: 0, botName: 'X', avgSurvivalTurns: 1, occupancy: 0,
        suicideRate: 0, scoreRate: 0, holdoutScoreRate: 0, attempts: 0,
        오타필드: 1,
      }),
    ).toThrow();
  });
});
```

- [ ] **Step 3: 실패를 확인한다**

Run: `cd web && npm test`
Expected: FAIL — `Cannot find module '../lib/bundle'`

- [ ] **Step 4: 스키마를 쓴다**

`web/src/lib/schema.ts`. **모든 오브젝트 스키마는 `.strict()`다** — 위
마지막 테스트가 요구하는 성질이고, 백엔드의 필드 개명을 조용히 넘기지
않기 위해서다.

```ts
import { z } from 'zod';

export const PointSchema = z.object({ x: z.number().int(), y: z.number().int() }).strict();

export const DirectionSchema = z.enum(['UP', 'DOWN', 'LEFT', 'RIGHT']);

export const DeathReasonSchema = z.enum([
  'P0_OUT_OF_BOUNDS', 'P0_HIT_OWN_WALL', 'P0_HIT_OPPONENT_WALL',
  'P1_OUT_OF_BOUNDS', 'P1_HIT_OWN_WALL', 'P1_HIT_OPPONENT_WALL',
  'HEAD_ON_COLLISION', 'BOTH_DIED', 'MAX_TURNS',
]);

export const MatchResultSchema = z.object({
  winner: z.number().int(),   // 좌석 인덱스. -1이 무승부
  turns: z.number().int().positive(),
  reason: DeathReasonSchema,
}).strict();

export const ReplaySchema = z.object({
  schema: z.literal(1),
  matchId: z.string(),
  width: z.number().int().positive(),
  height: z.number().int().positive(),
  seed: z.number().int(),
  swapped: z.boolean(),
  bot0Id: z.string(), start0: PointSchema, dir0: DirectionSchema,
  bot1Id: z.string(), start1: PointSchema, dir1: DirectionSchema,
  moves: z.string(),
  result: MatchResultSchema,
  hash: z.string().startsWith('sha256:'),
}).strict()
  // moves는 턴당 2글자다. 이게 깨지면 디코더가 조용히 어긋난 방향을
  // 읽으므로, 데이터를 받는 자리에서 잡는다.
  .refine((r) => r.moves.length === r.result.turns * 2,
    { message: 'moves 길이가 턴 수 × 2가 아니다' });

export const GenerationStatSchema = z.object({
  generation: z.number().int().nonnegative(),
  botName: z.string(),
  avgSurvivalTurns: z.number(),
  occupancy: z.number(),
  suicideRate: z.number(),
  scoreRate: z.number(),
  // 승격한 시도가 없으면 NaN이 실린다. Jackson이 따옴표 붙은 "NaN"
  // 문자열로 내보내므로 두 형태를 다 받아 number로 정규화한다 —
  // 화면은 Number.isNaN()으로 "아직 승격 못 함"을 판정한다.
  holdoutScoreRate: z.union([z.number(), z.literal('NaN').transform(() => NaN)]),
  attempts: z.number().int().nonnegative(),
}).strict();

export const AttemptRecordSchema = z.object({
  generation: z.number().int().nonnegative(),
  attempt: z.number().int().positive(),
  verdict: z.enum(['PASSED', 'PROMOTED', 'REJECTED']),
  stage: z.enum(['GATE', 'CHAMPIONSHIP']),
  failedGate: z.string().nullable(),
  detail: z.string(),
}).strict();

export const MoveAnalysisSchema = z.object({
  turn: z.number().int().positive(),        // 1-based
  chose: DirectionSchema,
  best: DirectionSchema,
  reachAfterChosen: z.number().int(),
  reachAfterBest: z.number().int(),
  loss: z.number().int(),
  suicide: z.boolean(),
  fatal: z.boolean(),
}).strict();

export const MatchDiagnosisSchema = z.object({
  matchId: z.string(),
  reach: z.array(z.array(z.number().int())),   // [봇][턴], 턴은 0-based
  loss: z.array(z.array(z.number().int())),
  occupancy: z.array(z.number()),
  suicideRate: z.array(z.number()),
  worstMoves0: z.array(MoveAnalysisSchema),
  worstMoves1: z.array(MoveAnalysisSchema),
}).strict();

export const RoundRobinSchema = z.object({
  bots: z.array(z.string()),
  matrix: z.array(z.array(z.number().nullable())),  // 대각선은 null
}).strict();

export const SourceIndexEntrySchema = z.object({
  generation: z.number().int().nonnegative(),
  botName: z.string(),
  available: z.boolean(),
  file: z.string().nullable(),
}).strict();

export const BundleMetaSchema = z.object({
  demo: z.boolean(),
  generations: z.number().int().nonnegative(),
  gallerySeed: z.number().int(),
}).strict();

export type Replay = z.infer<typeof ReplaySchema>;
export type GenerationStat = z.infer<typeof GenerationStatSchema>;
export type AttemptRecord = z.infer<typeof AttemptRecordSchema>;
export type MoveAnalysis = z.infer<typeof MoveAnalysisSchema>;
export type MatchDiagnosis = z.infer<typeof MatchDiagnosisSchema>;
export type RoundRobinData = z.infer<typeof RoundRobinSchema>;
export type SourceIndexEntry = z.infer<typeof SourceIndexEntrySchema>;
export type BundleMeta = z.infer<typeof BundleMetaSchema>;
export type Direction = z.infer<typeof DirectionSchema>;
```

- [ ] **Step 5: 로더를 쓴다**

`web/src/lib/bundle.ts`. **서버 전용이다** — 빌드 타임에만 돈다.

```ts
import 'server-only';
import { readFileSync, existsSync } from 'node:fs';
import path from 'node:path';
import {
  ReplaySchema, GenerationStatSchema, AttemptRecordSchema, MatchDiagnosisSchema,
  RoundRobinSchema, SourceIndexEntrySchema, BundleMetaSchema,
  type Replay, type GenerationStat, type AttemptRecord, type MatchDiagnosis,
  type RoundRobinData, type SourceIndexEntry, type BundleMeta,
} from './schema';
import { z } from 'zod';

export interface Bundle {
  meta: BundleMeta;
  gallery: Replay[];
  diagnosis: MatchDiagnosis[];
  generations: GenerationStat[];
  loopHistory: Record<string, AttemptRecord[]>;
  roundRobin: RoundRobinData;
  sources: SourceIndexEntry[];
  sourceText: Record<number, string>;
}

/**
 * 번들 디렉터리는 ARENA_BUNDLE이 정한다. 기본값을 두지 않는 이유는
 * 발표용 빌드가 조용히 데모 번들로 나가는 것을 막기 위해서다 —
 * 값이 없으면 빌드가 여기서 즉시 죽는다.
 */
function bundleDir(): string {
  const rel = process.env.ARENA_BUNDLE;
  if (!rel) {
    throw new Error(
      'ARENA_BUNDLE이 설정되지 않았다. 진짜 번들은 public/data (먼저 ./gradlew record), ' +
      '데모 번들은 fixtures/data (먼저 ./gradlew fixture).',
    );
  }
  const dir = path.join(process.cwd(), rel);
  if (!existsSync(path.join(dir, 'meta.json'))) {
    throw new Error(
      `번들이 없다: ${dir}. 진짜 번들은 ./gradlew record, 데모 번들은 ./gradlew fixture 로 만든다.`,
    );
  }
  return dir;
}

function read<T>(dir: string, file: string, schema: z.ZodType<T>): T {
  return schema.parse(JSON.parse(readFileSync(path.join(dir, file), 'utf8')));
}

export function loadBundle(): Bundle {
  const dir = bundleDir();

  const sources = read(dir, 'sources/index.json', z.array(SourceIndexEntrySchema));
  const sourceText: Record<number, string> = {};
  for (const entry of sources) {
    if (entry.file) {
      sourceText[entry.generation] = readFileSync(path.join(dir, entry.file), 'utf8');
    }
  }

  return {
    meta: read(dir, 'meta.json', BundleMetaSchema),
    gallery: read(dir, 'gallery.json', z.array(ReplaySchema)),
    diagnosis: read(dir, 'diagnosis.json', z.array(MatchDiagnosisSchema)),
    generations: read(dir, 'generations.json', z.array(GenerationStatSchema)),
    loopHistory: read(dir, 'loop-history.json', z.record(z.string(), z.array(AttemptRecordSchema))),
    roundRobin: read(dir, 'roundrobin.json', RoundRobinSchema),
    sources,
    sourceText,
  };
}
```

> `server-only` 패키지를 넣는다: `npm i server-only`. 이 모듈이 클라이언트
> 번들에 딸려 들어가면 `node:fs` 때문에 빌드가 깨지는데, 그 실패는 원인을
> 알아보기 어렵다. `server-only`가 훨씬 읽기 쉬운 에러로 바꿔준다.

- [ ] **Step 6: 통과를 확인하고 되돌려서 무는지 본다**

Run: `cd web && npm test`
Expected: PASS (6개)

그 다음 `GenerationStatSchema`의 `.strict()`를 지우고 다시 돌린다.
Expected: `알 수 없는 필드가 들어오면 거부한다`가 FAIL. 확인 뒤 원복한다.

- [ ] **Step 7: 배너와 최소 레이아웃을 만든다**

`DataSourceBanner.tsx`는 `meta.demo`가 참일 때만 화면 맨 위에 눈에 띄는
띠를 그린다: **"데모 번들 — 세대 루프가 만든 기록이 아니다"**. 발표장에서
이 띠가 보이면 즉시 잘못을 알 수 있어야 하므로 색과 크기를 아끼지 않는다.

`app/page.tsx`는 지금은 번들 요약(세대 수, 데모 여부, 갤러리 경기 수)만
찍는다. 화면들은 Task 8부터 붙는다.

- [ ] **Step 8: 정적 export가 실제로 되는지 확인한다**

Run: `cd web && npm run build:demo`
Expected: 성공하고 `web/out/`에 정적 파일이 생긴다.

Run: `cd web && npx next build`  (ARENA_BUNDLE 없이)
Expected: **실패**하고 메시지에 `ARENA_BUNDLE이 설정되지 않았다`가 보인다 —
기본값을 두지 않은 판단이 실제로 작동한다는 증거다.

- [ ] **Step 9: 커밋**

`log.md`에 D77: `ARENA_BUNDLE`에 기본값을 두지 않은 이유, Zod 스키마를
타입의 단일 출처로 삼아 손으로 쓴 interface를 두지 않는 이유,
`holdoutScoreRate`가 `"NaN"` 문자열로 올 수 있어 union으로 받는 것.

```bash
git add web log.md
git commit -m "$(cat <<'EOF'
feat: 프론트엔드 스캐폴딩과 번들 스키마 계약

스펙 §11 T5가 프론트엔드에 요구하는 테스트는 "발표 번들 JSON의 스키마
계약" 하나다. 그걸 먼저 놓는다 — 화면보다 계약이 먼저 있어야 백엔드가
필드를 바꿨을 때 화면이 빈 값을 그리는 대신 테스트가 죽는다.

모든 스키마를 strict로 둔 이유가 그것이다. strict가 아니면 백엔드의
필드 개명이 조용히 undefined로 흘러 화면이 빈 값을 그린다.

ARENA_BUNDLE에 기본값을 두지 않았다. 기본값이 있으면 발표용 빌드가
조용히 데모 번들로 나갈 수 있다 — 값이 없으면 빌드가 즉시 죽는다.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 6: 리플레이 디코더 — 이 계획에서 가장 위험한 코드

`moves` 문자열을 턴별 격자 상태로 되돌린다. 화면 1·5가 전부 이것 위에 선다.

**이것은 보드 재구성 규칙의 다섯 번째 사본이고, 처음으로 다른 언어로 쓰는
사본이다.** 계획 1의 최종 리뷰가 잡아낸 유일한 교차 결함이 정확히 이
규칙의 네 번째 사본이었다(`PositionSampler`가 시작 칸 2개만 잡아 10,000개
표본 전부가 엔진이 만들 수 없는 판이었다). 자바 쪽은 `Match.initialGrid`
하나로 모았지만 타입스크립트에서는 그 함수를 부를 수 없다.

그래서 **정확성을 리뷰어의 눈이 아니라 테스트가 보증하게 만든다.** 모든
리플레이는 `result.winner`·`result.turns`·`result.reason`을 들고 있고, 그
값은 엔진이 판정한 것이다. 디코더가 `moves`만 보고 독립적으로 같은 셋을
내놓으면 규칙이 일치한다는 뜻이다 — 골든 파일도, 자바 호출도 필요 없다.

**규칙 전문** (`Match.playInternal`·`initialGrid`·`resolve`를 읽고 옮긴 것):

1. 시작 격자는 **4칸**이다 — `start0`, `start1`, 그리고 각자의 **바로 뒤
   칸**(`시작점 + 방향의 반대`). 뒤 칸을 벽으로 만들어야 첫 턴 후진이
   자기 벽 충돌로 죽는다.
2. 턴 t에서 `d0 = moves[(t-1)*2]`, `d1 = moves[(t-1)*2+1]`.
3. `p_i = head_i + d_i`.
4. **같은 W(t)로 동시에 판정한다** — 3번의 벽 확정보다 먼저다.
   `dead_i = p_i가 격자 밖 ∨ p_i가 벽 ∨ p0 == p1`.
5. **살아남은 쪽만** 벽을 확정한다: `if (!dead0) claim(p0, 0)`, `if (!dead1) claim(p1, 1)`.
   이 턴에 경기가 끝나더라도 예외가 아니다.
6. 둘 중 하나라도 죽었으면 경기가 끝난다. 사유는 **5번의 확정을 마친
   격자**에서 판정한다:
   - 둘 다 죽음 → `winner = -1`, 사유는 `p0 == p1`이면 `HEAD_ON_COLLISION`, 아니면 `BOTH_DIED`
   - 봇0만 죽음 → `winner = 1`, 사유는 `p0`가 격자 밖이면 `P0_OUT_OF_BOUNDS`,
     `ownerAt(p0) == 0`이면 `P0_HIT_OWN_WALL`, 아니면 `P0_HIT_OPPONENT_WALL`
   - 봇1만 죽음 → `winner = 0`, 같은 규칙의 `P1_*`
7. 안 끝났으면 `head_i = p_i`, `dir_i = d_i`로 갱신하고 다음 턴.

**Files:**
- Create: `web/src/lib/replay.ts`
- Test: `web/src/test/replay.test.ts`, `web/src/test/replay.conformance.test.ts`

**Interfaces:**
- Consumes: `Replay` (Task 5의 `schema.ts`)
- Produces:
  - `decodeReplay(replay: Replay): DecodedMatch`
  - `interface DecodedMatch { width: number; height: number; turns: TurnState[]; winner: number; turnCount: number; reason: DeathReason; startWalls: { point: Point; seat: 0 | 1 }[] }`
    (`startWalls`는 시작 4칸이다. 화면이 "시작 칸과 그 뒤 칸" 규칙을 다시 적으면 그게 규칙의 여섯 번째 사본이 되므로, 디코더가 계산해 넘긴다)
  - `interface TurnState { turn: number; heads: [Point, Point]; dirs: [Direction, Direction]; claimed: (Point | null)[]; alive: [boolean, boolean] }`
  - `owner(decoded: DecodedMatch, turn: number, x: number, y: number): 0 | 1 | null` — 화면이 칸 주인을 묻는 용도. 누적 격자는 `TurnState`가 아니라 `DecodedMatch`가 들고 있으므로 턴 번호를 함께 받는다

- [ ] **Step 1: 되돌아온 결과를 대조하는 conformance 테스트를 먼저 쓴다**

`web/src/test/replay.conformance.test.ts`. **이 테스트가 이 태스크의
합격 기준이다.**

```ts
import { describe, it, expect } from 'vitest';
import { loadBundle } from '../lib/bundle';
import { decodeReplay } from '../lib/replay';

describe('디코더 ↔ 엔진 대조', () => {
  const { gallery } = loadBundle();

  it('번들에 대조할 경기가 실제로 들어있다', () => {
    // 갤러리가 비면 아래 테스트들이 0번 돌고도 통과한다.
    expect(gallery.length).toBeGreaterThanOrEqual(12);
  });

  it.each(gallery.map((r) => [r.matchId, r] as const))(
    '%s — 승자·턴수·사망사유가 엔진과 같다',
    (_id, replay) => {
      const decoded = decodeReplay(replay);
      expect(decoded.turnCount).toBe(replay.result.turns);
      expect(decoded.winner).toBe(replay.result.winner);
      expect(decoded.reason).toBe(replay.result.reason);
    },
  );

  it.each(gallery.map((r) => [r.matchId, r] as const))(
    '%s — 벽은 턴마다 생존 봇 수만큼만 늘어난다',
    (_id, replay) => {
      // 스펙 §7.1의 벽 단조성. 이게 깨지면 디코더가 죽은 봇의 머리를
      // 벽으로 잡았거나 살아있는 봇의 머리를 빠뜨린 것이다.
      const decoded = decodeReplay(replay);
      decoded.turns.forEach((state) => {
        const claimedCount = state.claimed.filter((p) => p !== null).length;
        const aliveCount = state.alive.filter(Boolean).length;
        expect(claimedCount).toBe(aliveCount);
      });
    },
  );
});
```

- [ ] **Step 2: 시작 격자의 4칸을 못박는 단위 테스트를 쓴다**

`web/src/test/replay.test.ts`. conformance 테스트만으로는 부족하다 —
데모 번들의 모든 경기가 우연히 뒤로 가지 않는다면 4칸 규칙이 틀려도
통과할 수 있기 때문이다. 그 구멍을 손으로 만든 경기가 막는다.

```ts
import { describe, it, expect } from 'vitest';
import { decodeReplay, owner } from '../lib/replay';
import type { Replay } from '../lib/schema';

/** 첫 턴에 봇0이 뒤로 가고 봇1은 직진하는 최소 경기. */
const 후진_경기: Replay = {
  schema: 1, matchId: 'test-back', width: 30, height: 30, seed: 1, swapped: false,
  bot0Id: 'a', start0: { x: 10, y: 10 }, dir0: 'RIGHT',
  bot1Id: 'b', start1: { x: 20, y: 20 }, dir1: 'LEFT',
  moves: 'LL',
  result: { winner: 1, turns: 1, reason: 'P0_HIT_OWN_WALL' },
  hash: 'sha256:테스트픽스처',
};

describe('시작 격자', () => {
  it('시작 칸 뒤 칸도 벽이다 — 첫 턴 후진이 자기 벽 충돌로 죽는다', () => {
    // 봇0은 RIGHT로 시작하므로 (9,10)이 처음부터 벽이다. LEFT를 내면
    // 거기 박는다. 뒤 칸을 안 잡으면 이 경기는 죽지 않고 계속된다.
    const decoded = decodeReplay(후진_경기);
    expect(decoded.turnCount).toBe(1);
    expect(decoded.winner).toBe(1);
    expect(decoded.reason).toBe('P0_HIT_OWN_WALL');
  });

  it('턴 1의 격자에 벽이 정확히 4칸 있다', () => {
    const decoded = decodeReplay(후진_경기);
    let walls = 0;
    for (let x = 0; x < 30; x++) {
      for (let y = 0; y < 30; y++) if (owner(decoded, 1, x, y) !== null) walls++;
    }
    expect(walls).toBe(4);
  });
});

describe('동시 판정', () => {
  it('같은 칸에 동시 진입하면 무승부다', () => {
    const 정면충돌: Replay = {
      schema: 1, matchId: 'test-headon', width: 30, height: 30, seed: 1, swapped: false,
      bot0Id: 'a', start0: { x: 10, y: 10 }, dir0: 'RIGHT',
      bot1Id: 'b', start1: { x: 12, y: 10 }, dir1: 'LEFT',
      moves: 'RL',
      result: { winner: -1, turns: 1, reason: 'HEAD_ON_COLLISION' },
      hash: 'sha256:테스트픽스처',
    };
    const decoded = decodeReplay(정면충돌);
    expect(decoded.winner).toBe(-1);
    expect(decoded.reason).toBe('HEAD_ON_COLLISION');
  });

  it('죽은 봇의 머리는 벽이 되지 않는다', () => {
    // 봇0만 죽는 턴에 확정되는 벽은 봇1의 머리 하나뿐이다 (스펙 §7.1).
    const decoded = decodeReplay(후진_경기);
    const last = decoded.turns[decoded.turns.length - 1];
    expect(last.claimed[0]).toBeNull();
    expect(last.claimed[1]).not.toBeNull();
  });
});
```

- [ ] **Step 3: 실패를 확인한다**

Run: `cd web && npm test`
Expected: FAIL — `Cannot find module '../lib/replay'`

- [ ] **Step 4: 디코더를 구현한다**

`web/src/lib/replay.ts`. 위 "규칙 전문" 7단계를 그대로 옮긴다.
**규칙을 요약하거나 최적화하지 않는다** — 이 파일의 목적은 빠른 것이
아니라 엔진과 같은 것이다. 파일 맨 위 주석에 "이것은 `Match`의 규칙을
타입스크립트로 옮긴 사본이고, `replay.conformance.test.ts`가 그 일치를
매 빌드마다 검사한다"를 적는다.

`claimed`는 턴별로 그 턴에 새로 벽이 된 칸이다(봇별, 죽었으면 `null`).
화면은 이걸 그대로 그리면 되므로 매 프레임 전체 격자를 다시 그릴
필요가 없다(스펙 §9.3 — 트론은 벽이 영구적이라 캔버스를 지울 필요가 없다).

`owner`는 격자 배열을 `DecodedMatch` 안에 누적해 두고 조회한다 —
`Int8Array(width * height)`에 `-1`(빈 칸)/`0`/`1`을 담는다.

- [ ] **Step 5: 통과를 확인한다**

Run: `cd web && npm test`
Expected: PASS. conformance 테스트가 데모 번들의 12경기 × 2 = 24케이스를 돈다.

- [ ] **Step 6: 되돌려서 무는지 확인한다 (세 번)**

이 태스크에서만은 세 가지를 각각 깨뜨려 본다. 하나씩 되돌리고, 확인하고,
즉시 원복한다. **어느 하나라도 테스트가 초록이면 그 테스트는 아무것도
지키지 않는 것이므로, 테스트를 고친 뒤 다시 확인한다.**

| 무엇을 깨뜨리나 | 실패해야 하는 테스트 |
|---|---|
| 시작 격자를 4칸 → 2칸(뒤 칸 제거) | `시작 칸 뒤 칸도 벽이다`, `턴 1의 격자에 벽이 정확히 4칸` |
| 5번을 "둘 다 항상 claim"으로 바꾼다 | `죽은 봇의 머리는 벽이 되지 않는다`, `벽은 턴마다 생존 봇 수만큼만` |
| 4번의 판정을 봇0 확정 뒤에 봇1을 판정하도록 순차화 | conformance의 `승자·턴수·사망사유` (정면충돌 경기에서 갈린다) |

리포트에 세 실험의 실제 출력을 싣는다.

- [ ] **Step 7: 커밋**

`log.md`에 D78: 규칙의 다섯 번째 사본이자 첫 타 언어 사본이라는 것,
`result`가 리플레이 안에 들어 있다는 사실을 이용해 골든 파일 없이
대조가 가능하다는 것, 그리고 계획 1의 F1이 정확히 같은 부류의 사고였다는 것.

```bash
git add web log.md
git commit -m "$(cat <<'EOF'
feat: 리플레이 디코더 — 엔진과의 일치를 테스트가 보증한다

moves를 턴별 격자로 되돌린다. 이것은 보드 재구성 규칙의 다섯 번째
사본이고 처음으로 타입스크립트로 쓰는 사본이다. 계획 1의 최종 리뷰가
잡은 유일한 교차 결함이 정확히 이 규칙의 네 번째 사본이었다 —
PositionSampler가 시작 칸 2개만 잡아 표본 10,000개 전부가 엔진이 만들 수
없는 판이었다. 자바 쪽은 Match.initialGrid 하나로 모았지만 타입스크립트에서
그 함수를 부를 수 없으므로, 사본이라는 사실 자체를 없앨 수는 없다.

그래서 리뷰어의 눈이 아니라 테스트가 일치를 보증하게 했다. 모든 리플레이가
엔진이 판정한 winner·turns·reason을 들고 있으므로, 디코더가 moves만 보고
독립적으로 같은 셋을 내놓는지 매 빌드마다 대조한다 — 골든 파일도 자바
호출도 필요 없다.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### 화면 태스크(7~13)의 합격 기준에 대해

스펙 §11 T5가 **시각 회귀 테스트를 두지 않는다**고 못박았다. 그러면 화면
태스크는 무엇으로 판정하는가 — R2가 "판정할 수 없는 요구사항은 요구사항이
아니다"라고 하므로 이 질문을 얼버무리면 안 된다. 셋으로 나눈다.

1. **순수 함수는 단위 테스트한다.** 패널 격자 계산, 트레일 밝기, 색 선택,
   수치 포맷 — 화면의 판단은 전부 순수 함수로 빼고 그것만 테스트한다.
   컴포넌트 안에 계산이 숨으면 판정할 수 없어진다.
2. **`npm run build:demo`가 성공해야 한다.** 정적 export는 모든 페이지를
   빌드 타임에 실제로 렌더링하므로, 데이터 접근 오류·널 참조는 여기서
   빌드 실패로 나타난다. 이게 화면 태스크의 실질적인 스모크 테스트다.
3. **육안 확인은 리포트에 적는다.** 구현자가 `npm run dev`로 직접 보고
   "무엇을 보았는지" 문장으로 남긴다. 이건 기계 판정이 아니고, 그래서
   합격 기준이 아니라 **기록**이다. Task 14가 Playwright 스모크로
   콘솔 오류 0건까지는 기계로 끌어올린다.

---

### Task 7: 캔버스 렌더러

격자 한 판을 그린다. 화면 1과 5가 공유한다.

**Files:**
- Create: `web/src/lib/colors.ts`, `web/src/lib/trail.ts`, `web/src/components/ArenaCanvas.tsx`
- Test: `web/src/test/trail.test.ts`

**Interfaces:**
- Consumes: `DecodedMatch`·`TurnState` (Task 6)
- Produces: `BOT_COLORS: readonly [string, string]`, `trailAlpha(age: number, maxAge: number): number`, `<ArenaCanvas decoded turn cellSize dead />`

- [ ] **Step 1: 트레일 밝기의 실패하는 테스트를 쓴다**

스펙 §9.3: "최근 지나온 칸일수록 밝게. 정지 화면에서도 진행 방향이 읽힌다."
그 성질을 그대로 단언으로 옮긴다.

```ts
import { describe, it, expect } from 'vitest';
import { trailAlpha } from '../lib/trail';

describe('트레일 밝기', () => {
  it('가장 최근 칸이 가장 밝다', () => {
    expect(trailAlpha(0, 20)).toBeGreaterThan(trailAlpha(5, 20));
    expect(trailAlpha(5, 20)).toBeGreaterThan(trailAlpha(19, 20));
  });

  it('오래된 칸도 완전히 사라지지는 않는다', () => {
    // 벽은 영구적이다 — 안 보이면 화면이 "이 봇이 얼마나 채웠나"를
    // 못 보여준다. 갤러리가 순위표가 되는 근거가 사라진다.
    expect(trailAlpha(999, 20)).toBeGreaterThan(0.15);
  });

  it('밝기는 0과 1 사이다', () => {
    for (const age of [0, 1, 7, 20, 500]) {
      expect(trailAlpha(age, 20)).toBeGreaterThan(0);
      expect(trailAlpha(age, 20)).toBeLessThanOrEqual(1);
    }
  });

  it('진행 방향이 읽히려면 최근 구간의 기울기가 충분해야 한다', () => {
    // "정지 화면에서도 방향이 읽힌다"를 판정 가능한 형태로 바꾼 것:
    // 머리와 20칸 뒤의 밝기 차가 0.3 이상이어야 눈에 띈다.
    expect(trailAlpha(0, 20) - trailAlpha(20, 20)).toBeGreaterThanOrEqual(0.3);
  });
});
```

- [ ] **Step 2: 실패 확인 → 구현 → 통과 확인**

Run: `cd web && npm test` (실패 → 구현 → PASS)

`colors.ts`는 팔레트를 한 곳에 둔다. **청/주황은 스펙 §9.3이 색각 이상과
프로젝터 대비를 이유로 정한 값이므로 바꾸지 않는다.**

```ts
/** 봇0 = 청, 봇1 = 주황 (스펙 §9.3 — 색각 이상 구분과 프로젝터 대비). */
export const BOT_COLORS = ['#38bdf8', '#fb923c'] as const;
export const GRID_BG = '#0b1120';
export const DEAD_OVERLAY = 'rgba(11, 17, 32, 0.72)';
export const FLASH = '#ffffff';
```

- [ ] **Step 3: `ArenaCanvas`를 만든다**

`'use client'` 컴포넌트다. 누적 렌더링의 뼈대는 이렇다.

```tsx
'use client';
import { useEffect, useRef } from 'react';
import { BOT_COLORS, GRID_BG, FLASH } from '../lib/colors';
import { trailAlpha } from '../lib/trail';
import type { DecodedMatch } from '../lib/replay';

export function ArenaCanvas({ decoded, turn, cellSize, dead }: {
  decoded: DecodedMatch; turn: number; cellSize: number; dead: boolean;
}) {
  const ref = useRef<HTMLCanvasElement>(null);
  // 마지막으로 그린 턴. 이것보다 앞으로 갔으면 그 사이 칸만 칠하고,
  // 뒤로 갔거나 리플레이가 바뀌었으면 처음부터 다시 그린다.
  const drawn = useRef(0);

  useEffect(() => {
    const canvas = ref.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const from = turn >= drawn.current ? drawn.current : 0;
    if (from === 0) {
      ctx.fillStyle = GRID_BG;
      ctx.fillRect(0, 0, canvas.width, canvas.height);
      paintStartWalls(ctx, decoded, cellSize);
    }

    // 층을 둘로 나눈다. 안 나누면 트레일 그라데이션과 누적 렌더링이
    // 양립하지 않는다: 칸을 확정할 때의 밝기로 한 번만 칠하면 모든 칸이
    // "가장 최근" 밝기로 굳어 트레일이 아예 안 보인다.
    //
    //  ① 영구층 — 확정된 벽을 가장 어두운 밝기로 한 번만 칠한다.
    //     트론은 벽이 영구적이라 여기는 지울 필요가 없다 (스펙 §9.3).
    //  ② 트레일 창 — 최근 TRAIL 턴만 매 프레임 다시 칠한다. 먼저
    //     배경색으로 덮어 이전 프레임의 알파가 겹쳐 쌓이지 않게 한다.
    //     매 프레임 손대는 칸은 봇당 TRAIL칸, 12패널을 합쳐도 수백 칸이다.
    for (let t = from; t < turn; t++) paintTurn(ctx, decoded, t, cellSize, trailAlpha(999, TRAIL));

    const windowStart = Math.max(0, turn - TRAIL);
    for (let t = windowStart; t < turn; t++) {
      clearTurn(ctx, decoded, t, cellSize);   // GRID_BG로 불투명하게 덮는다
      paintTurn(ctx, decoded, t, cellSize, trailAlpha(turn - 1 - t, TRAIL));
    }
    ctx.globalAlpha = 1;
    // 다음 프레임의 영구층은 트레일 창 앞까지만 새로 칠하면 된다.
    drawn.current = turn;

    // 사망 순간의 흰 플래시 한 프레임. 회색조는 여기서 칠하지 않는다 —
    // CSS filter로 걸어야 누적 렌더링 전제가 유지된다.
    if (dead && turn === decoded.turnCount) {
      ctx.fillStyle = FLASH;
      ctx.globalAlpha = 0.85;
      ctx.fillRect(0, 0, canvas.width, canvas.height);
      ctx.globalAlpha = 1;
      requestAnimationFrame(() => { drawn.current = 0; });
    }
  }, [decoded, turn, cellSize, dead]);

  return (
    <canvas
      ref={ref}
      // 격자 크기는 리플레이에서 읽는다. 30을 박지 않는다.
      width={decoded.width * cellSize}
      height={decoded.height * cellSize}
      style={{ filter: dead ? 'grayscale(1)' : 'none', transition: 'filter 400ms' }}
    />
  );
}
```

> `devicePixelRatio` 반영은 위 뼈대에 더한다: 캔버스의 `width`/`height`
> 속성에 `dpr`를 곱하고 `ctx.scale(dpr, dpr)`을 한 번 건 뒤 CSS 크기는
> 논리 픽셀로 둔다. 안 하면 프로젝터에서 격자가 흐리게 나온다.
>
> `paintTurn(ctx, decoded, t, cellSize, alpha)`는 턴 `t`에 확정된 칸들을
> 봇 색으로 칠하고, `clearTurn`은 같은 칸들을 `GRID_BG`로 불투명하게
> 덮는다. 둘 다 `state.claimed[seat]`가 `null`이면 건너뛴다 — 그 턴에
> 죽은 봇은 벽을 남기지 않는다.
>
> `paintStartWalls`는 `decoded.startWalls`를 칠한다. **어느 칸이 시작
> 벽인지는 디코더에게 묻는다** — 여기서 "시작 칸과 그 뒤 칸" 규칙을 다시
> 적으면 그것이 규칙의 여섯 번째 사본이 된다.
>
> `TRAIL`은 `trail.ts`가 내보내는 상수 20이다. 테스트가 `trailAlpha(0, 20)`
> 과 `trailAlpha(20, 20)`의 차를 0.3 이상으로 못박고 있으므로, 이 값과
> 그 테스트는 함께 움직인다.

요점 넷을 다시 확인한다:

- **캔버스를 지우지 않는다.** 턴이 늘어날 때 그 턴의 `claimed` 칸만
  새로 칠한다(스펙 §9.3 — 매 프레임 새로 그리는 셀은 봇당 1칸).
  `turn`이 **뒤로** 갔거나 리플레이가 바뀌었을 때만 전체를 다시 그린다.
- **사망 순간** 흰 플래시 한 프레임 → 패널 전체 회색조. 회색조는 캔버스에
  칠하지 말고 CSS `filter: grayscale(1)`로 건다 — 캔버스를 건드리면
  누적 렌더링 전제가 깨진다.
- `devicePixelRatio`를 반영해 선명하게 그린다.
- 캔버스 크기는 `width`·`height`를 리플레이에서 읽어 정한다. **30을 박지 않는다.**

- [ ] **Step 4: 빌드와 커밋**

Run: `cd web && npm test && npm run build:demo` → 성공
리포트에 육안 확인 한 문장.

```bash
git add web
git commit -m "$(cat <<'EOF'
feat: 캔버스 렌더러 — 트레일과 사망 연출

트론은 벽이 영구적이라 캔버스를 지울 필요가 없다(스펙 §9.3). 턴이 늘 때
그 턴에 확정된 칸만 칠하고, 뒤로 감거나 리플레이가 바뀔 때만 전체를 다시
그린다. 회색조는 CSS filter로 건다 — 캔버스에 칠하면 누적 렌더링 전제가
깨진다.

트레일 밝기의 "정지 화면에서도 방향이 읽힌다"를 판정 가능한 형태로
바꿨다: 머리와 20칸 뒤의 밝기 차가 0.3 이상. 그리고 오래된 칸도 0.15
아래로 내려가지 않는다 — 안 보이면 "이 봇이 얼마나 채웠나"가 사라지고
갤러리가 순위표가 되는 근거도 함께 사라진다.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 8: 화면 1 — 세대 갤러리 (R3의 증거)

발표의 첫 화면이자 R3을 화면 하나로 증명하는 자리다. 모든 세대가 같은
시드에서 최종 챔피언과 붙는 경기를 **동시에** 재생한다.

**Files:**
- Create: `web/src/lib/layout.ts`, `web/src/components/GalleryPanel.tsx`, `web/src/components/PlaybackControls.tsx`, `web/src/app/gallery/page.tsx`
- Test: `web/src/test/layout.test.ts`

**Interfaces:**
- Consumes: `loadBundle().gallery`, `decodeReplay`, `<ArenaCanvas />`
- Produces: `panelGrid(count: number): { cols: number; rows: number }`

- [ ] **Step 1: 패널 배치의 실패하는 테스트를 쓴다**

스펙 §9.1이 예를 둘 준다: 12세대 → 3×4, 16세대 → 4×4.

```ts
import { describe, it, expect } from 'vitest';
import { panelGrid } from '../lib/layout';

describe('패널 배치', () => {
  it('스펙이 든 두 예를 그대로 만족한다', () => {
    // 스펙 §9.1의 "12세대 → 3×4"는 행×열로 읽는다 (3행 4열).
    expect(panelGrid(12)).toEqual({ cols: 4, rows: 3 });
    expect(panelGrid(16)).toEqual({ cols: 4, rows: 4 });
  });

  it('모든 패널이 자리를 갖는다', () => {
    for (let n = 1; n <= 40; n++) {
      const { cols, rows } = panelGrid(n);
      expect(cols * rows).toBeGreaterThanOrEqual(n);
    }
  });

  it('빈 자리를 최소로 남긴다', () => {
    // 격자가 지나치게 크면 패널이 작아져 30초 안에 읽히지 않는다.
    for (let n = 1; n <= 40; n++) {
      const { cols, rows } = panelGrid(n);
      expect(cols * rows - n).toBeLessThan(cols);
    }
  });

  it('가로가 세로보다 길거나 같다 — 프로젝터는 가로가 넓다', () => {
    for (let n = 1; n <= 40; n++) {
      const { cols, rows } = panelGrid(n);
      expect(cols).toBeGreaterThanOrEqual(rows);
    }
  });
});
```

- [ ] **Step 2: 실패 확인 → `panelGrid` 구현 → 통과 확인**

Run: `cd web && npm test`

- [ ] **Step 3: 패널과 재생 컨트롤을 만든다**

`GalleryPanel`: 캔버스 + **하단 생존 턴 카운터**. 죽으면 그 값에서 고정한다
(스펙 §9.3 — 갤러리가 그대로 순위표가 된다). 패널 제목은 세대 번호와
봇 이름.

`PlaybackControls`: 재생/정지/속도(0.5× · 1× · 2× · 4×)/처음으로.
**모든 패널이 하나의 턴 카운터를 공유한다** — 패널마다 따로 돌면 "같은
시점에 누가 살아있나"라는 비교가 성립하지 않는다. 이미 죽은 패널은
자기 마지막 상태에서 멈춘다.

재생은 `requestAnimationFrame` 하나로 돌리고 경과 시간으로 턴을 계산한다
(`setInterval`로 턴을 세면 탭이 백그라운드로 갔다 오면 패널마다 어긋난다).

- [ ] **Step 4: 페이지를 붙이고 빌드한다**

`app/gallery/page.tsx`는 서버 컴포넌트로 번들을 읽어 클라이언트 컴포넌트에
넘긴다. **`decodeReplay`는 클라이언트에서 부른다** — 디코딩 결과(턴 수백 개의
상태)를 직렬화해 넘기면 HTML이 수 MB로 불어난다. 넘기는 것은 `Replay`
원본뿐이다.

Run: `cd web && npm test && npm run build:demo` → 성공

- [ ] **Step 5: 육안 확인을 리포트에 적는다**

`npm run dev` → `/gallery`. 확인할 것: 12패널이 동시에 시작하는가,
앞 세대가 먼저 회색이 되는가, 생존 턴 카운터가 죽은 시점에 멈추는가,
속도 버튼이 듣는가. **본 것을 문장으로 쓴다.** "잘 나온다"는 기록이 아니다.

- [ ] **Step 6: 커밋**

```bash
git add web
git commit -m "$(cat <<'EOF'
feat: 화면 1 세대 갤러리 — R3을 화면 하나로 증명한다

모든 패널이 하나의 턴 카운터를 공유한다. 패널마다 따로 돌면 "같은 시점에
누가 살아있나"라는 비교가 성립하지 않고, 그러면 이 화면이 R3의 증거가
되지 못한다.

재생은 requestAnimationFrame 하나에서 경과 시간으로 턴을 계산한다.
setInterval로 턴을 세면 탭이 백그라운드에 갔다 온 뒤 패널마다 어긋난다.

디코딩은 클라이언트에서 한다. 턴 수백 개의 상태를 직렬화해 서버에서
넘기면 HTML이 수 MB가 된다 — 넘기는 것은 Replay 원본뿐이다.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 9: 화면 2 — 개선 곡선

눈으로 본 것을 숫자로 확인시키는 화면. 세대별 평균 생존 턴이 주 지표이고
(스펙 §13 — R3 합격선이 그 값의 10배), 점유율·자멸률·승점 승률이 보조다.

> **구현자에게:** 차트 코드를 한 줄이라도 쓰기 전에 `dataviz` 스킬을
> 먼저 읽는다. 색·축·범례·툴팁 규칙이 거기 있고, 이 저장소의 청/주황
> 제약(스펙 §9.3)과 함께 적용해야 한다.

**Files:**
- Create: `web/src/lib/curve.ts`, `web/src/app/curve/page.tsx`
- Test: `web/src/test/curve.test.ts`

**Interfaces:**
- Consumes: `loadBundle().generations`
- Produces: `r3Ratio(stats: GenerationStat[]): number`, `r3Passed(stats): boolean`, `curveSeries(stats): { key: string; label: string; points: {x:number;y:number}[] }[]`

- [ ] **Step 1: R3 판정의 실패하는 테스트를 쓴다**

이 화면의 핵심 주장은 "R3을 넘었다"이고, 그건 기계가 판정할 수 있다.

```ts
import { describe, it, expect } from 'vitest';
import { r3Ratio, r3Passed } from '../lib/curve';
import { loadBundle } from '../lib/bundle';

const stat = (generation: number, avgSurvivalTurns: number) => ({
  generation, botName: `Gen${generation}`, avgSurvivalTurns,
  occupancy: 0, suicideRate: 0, scoreRate: 0, holdoutScoreRate: NaN, attempts: 1,
});

describe('R3 판정', () => {
  it('마지막 세대 ÷ Gen 0 이 배율이다', () => {
    expect(r3Ratio([stat(0, 10), stat(1, 50), stat(2, 150)])).toBeCloseTo(15);
  });

  it('합격선은 10배다 — 스펙 §13', () => {
    expect(r3Passed([stat(0, 10), stat(1, 99)])).toBe(false);
    expect(r3Passed([stat(0, 10), stat(1, 100)])).toBe(true);  // 경계값 포함
  });

  it('Gen 0의 생존 턴이 0이면 배율을 주장하지 않는다', () => {
    // 0으로 나눈 Infinity를 "무한히 개선됐다"로 그리면 거짓말이 된다.
    expect(Number.isFinite(r3Ratio([stat(0, 0), stat(1, 100)]))).toBe(false);
    expect(r3Passed([stat(0, 0), stat(1, 100)])).toBe(false);
  });

  it('데모 번들이 실제로 R3을 넘는다', () => {
    expect(r3Passed(loadBundle().generations)).toBe(true);
  });
});
```

- [ ] **Step 2: 실패 확인 → 구현 → 통과 확인 → 빌드**

Run: `cd web && npm test && npm run build:demo`

화면은 평균 생존 턴을 주 축으로 그리고, **R3 합격선(Gen 0 × 10)을 가로선으로
표시**한다 — 그 선을 언제 넘었는지가 이 화면의 한 문장이다. 승률·점유율·
자멸률은 토글로 겹쳐 본다. 값은 전부 번들에서 온 것이고 **화면이 다시
계산하지 않는다**(R1).

- [ ] **Step 3: 육안 확인 기록 + 커밋**

```bash
git add web
git commit -m "$(cat <<'EOF'
feat: 화면 2 개선 곡선 — R3 합격선을 선으로 그린다

이 화면의 주장은 "R3을 넘었다"이고 그건 기계가 판정할 수 있다. 배율과
합격 여부를 순수 함수로 빼서 테스트했다.

Gen 0의 생존 턴이 0이면 배율을 주장하지 않는다. 0으로 나눈 Infinity를
"무한히 개선됐다"로 그리면 거짓말이 된다.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 10: 화면 3 — 루프 타임라인 (발표의 진짜 주인공)

스펙 §9.2가 "3번이 발표의 진짜 주인공"이라고 못박았다. 반려된 시도들이
빨간 칸으로 줄줄이 늘어선 화면이 "루프가 돌았다"의 가장 직접적인 증거다(C2).

**Files:**
- Create: `web/src/lib/timeline.ts`, `web/src/app/loop/page.tsx`
- Test: `web/src/test/timeline.test.ts`

**Interfaces:**
- Consumes: `loadBundle().loopHistory`, `loadBundle().generations`
- Produces: `timelineRows(history, generations): TimelineRow[]`, `attemptTone(record: AttemptRecord): 'passed' | 'promoted' | 'rejected'`, `gateColor(failedGate: string | null): string`

- [ ] **Step 1: 실패하는 테스트를 쓴다**

```ts
import { describe, it, expect } from 'vitest';
import { timelineRows, attemptTone, gateColor } from '../lib/timeline';
import { loadBundle } from '../lib/bundle';

describe('루프 타임라인', () => {
  it('시도가 없는 세대도 행을 갖는다', () => {
    // 행이 사라지면 "이 세대는 한 번에 통과했다"와 "이 세대가 없다"가
    // 같은 그림이 된다.
    const rows = timelineRows({ '0': [], '1': [] },
      [{ generation: 0 }, { generation: 1 }] as never);
    expect(rows.length).toBe(2);
    expect(rows[0].attempts.length).toBe(0);
  });

  it('시도는 번호 순으로 늘어선다', () => {
    const history = { '0': [
      { generation: 0, attempt: 2, verdict: 'PROMOTED', stage: 'CHAMPIONSHIP', failedGate: null, detail: '' },
      { generation: 0, attempt: 1, verdict: 'REJECTED', stage: 'GATE', failedGate: 'G4', detail: '' },
    ] };
    const rows = timelineRows(history as never, [{ generation: 0 }] as never);
    expect(rows[0].attempts.map((a) => a.attempt)).toEqual([1, 2]);
  });

  it('반려 사유마다 다른 색을 준다', () => {
    const colors = ['G2', 'G3', 'G4', 'G5', 'G6', 'G7'].map(gateColor);
    expect(new Set(colors).size).toBe(6);
  });

  it('챔피언전 반려도 관문 반려와 구분된다', () => {
    // failedGate가 null인 반려는 챔피언전에서 승률이 모자란 것이다.
    // 관문 반려와 같은 색이면 C2의 이야기가 뭉개진다.
    expect(gateColor(null)).not.toBe(gateColor('G7'));
  });

  it('세 판정이 서로 다른 톤을 갖는다', () => {
    const tones = new Set([
      attemptTone({ verdict: 'PASSED' } as never),
      attemptTone({ verdict: 'PROMOTED' } as never),
      attemptTone({ verdict: 'REJECTED' } as never),
    ]);
    expect(tones.size).toBe(3);
  });

  it('데모 번들에 반려가 실제로 들어있다', () => {
    // 반려가 하나도 없으면 이 화면이 증명할 것이 없다.
    const { loopHistory } = loadBundle();
    const rejected = Object.values(loopHistory).flat()
      .filter((a) => a.verdict === 'REJECTED');
    expect(rejected.length).toBeGreaterThan(0);
  });
});
```

- [ ] **Step 2: 실패 확인 → 구현 → 통과 확인 → 빌드**

Run: `cd web && npm test && npm run build:demo`

화면은 세대를 행, 시도를 열로 놓는다. 각 칸에 마우스를 올리면 `detail`이
뜬다 — 관문 반려는 `failedGate`와 사유, 챔피언전 반려는 "승점 승률 0.48
(기준 0.60)"이 그대로 보인다. **`detail`을 화면에서 다시 조립하지 않는다**
(R1) — 백엔드가 만든 문자열을 그대로 쓴다.

- [ ] **Step 3: 육안 확인 기록 + 커밋**

```bash
git add web
git commit -m "$(cat <<'EOF'
feat: 화면 3 루프 타임라인 — 반려가 줄줄이 보이는 것이 C2의 증거다

스펙 §9.2가 이 화면을 "발표의 진짜 주인공"이라고 못박았다. 반려된 시도를
빨간 칸으로 늘어놓는 것이 "루프가 돌았다"의 가장 직접적인 증거다.

시도가 없는 세대도 행을 남긴다. 행이 사라지면 "한 번에 통과했다"와
"이 세대가 없다"가 같은 그림이 된다.

챔피언전 반려(failedGate=null)를 관문 반려와 다른 색으로 둔다. 같은 색이면
"관문을 못 넘었다"와 "챔피언을 못 이겼다"가 뭉개진다.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 11: 화면 4 — 세대별 코드 diff

"이번 세대는 무엇을 배웠나"를 코드로 보여준다(C1, BRIEF §6 — 개선은
파라미터 튜닝이 아니라 코드 재작성이다).

**Files:**
- Create: `web/src/lib/diff.ts`, `web/src/app/diff/page.tsx`
- Test: `web/src/test/diff.test.ts`
- Modify: `web/package.json` (`npm i diff`, `npm i -D @types/diff`)

**Interfaces:**
- Consumes: `loadBundle().sources`, `loadBundle().sourceText`
- Produces: `diffAgainstPrevious(sources, sourceText, generation): DiffLine[]`, `interface DiffLine { kind: 'add' | 'del' | 'ctx'; text: string; }`

- [ ] **Step 1: 실패하는 테스트를 쓴다**

```ts
import { describe, it, expect } from 'vitest';
import { diffAgainstPrevious } from '../lib/diff';

const sources = [
  { generation: 0, botName: 'A', available: true, file: 'x' },
  { generation: 1, botName: 'B', available: true, file: 'y' },
  { generation: 2, botName: 'C', available: false, file: null },
];

describe('세대 diff', () => {
  it('Gen 0은 비교 대상이 없어 전부 추가로 나온다', () => {
    const lines = diffAgainstPrevious(sources, { 0: 'a\nb\n' }, 0);
    expect(lines.every((l) => l.kind === 'add')).toBe(true);
  });

  it('바뀐 줄만 add/del로 표시된다', () => {
    const lines = diffAgainstPrevious(sources, { 0: 'a\nb\nc\n', 1: 'a\nX\nc\n' }, 1);
    expect(lines.filter((l) => l.kind === 'del').map((l) => l.text.trim())).toEqual(['b']);
    expect(lines.filter((l) => l.kind === 'add').map((l) => l.text.trim())).toEqual(['X']);
  });

  it('소스가 없는 세대는 빈 결과를 준다 — 던지지 않는다', () => {
    // 세대 루프가 아직 안 돈 세대에서 화면 전체가 죽으면 안 된다.
    expect(diffAgainstPrevious(sources, { 0: 'a\n', 1: 'b\n' }, 2)).toEqual([]);
  });

  it('직전 세대의 소스가 없으면 전부 추가로 나온다', () => {
    const lines = diffAgainstPrevious(sources, { 1: 'a\n' }, 1);
    expect(lines.every((l) => l.kind === 'add')).toBe(true);
  });
});
```

- [ ] **Step 2: 실패 확인 → 구현 → 통과 확인 → 빌드**

`diff` 패키지의 `diffLines`를 쓴다. **LCS를 직접 짜지 않는다** — Task 3의
판정이 그것이었다.

Run: `cd web && npm test && npm run build:demo`

화면은 세대 선택기 + 좌우 분할(직전 세대 / 이번 세대) 또는 통합 뷰를
보여주고, 추가 줄은 초록, 삭제 줄은 빨강으로 칠한다. **봇 색(청/주황)을
쓰지 않는다** — 그 두 색은 좌석을 뜻하기로 정한 값이라 여기서 쓰면 의미가
겹친다.

- [ ] **Step 3: 육안 확인 기록 + 커밋**

```bash
git add web
git commit -m "$(cat <<'EOF'
feat: 화면 4 세대별 코드 diff — "이번 세대는 무엇을 배웠나"

diff는 검증된 라이브러리로 계산한다. Task 3에서 백엔드가 diff를 만들지
않기로 한 판정의 착지점이다 — LCS를 손으로 짜 넣지 않는다.

소스가 없는 세대에서 던지지 않고 빈 결과를 준다. 세대 루프가 아직 안 돈
세대 하나 때문에 화면 전체가 죽으면 안 된다.

추가/삭제에 초록·빨강을 쓰고 청/주황은 쓰지 않는다. 그 두 색은 좌석을
뜻하기로 정한 값이라 여기서 쓰면 의미가 겹친다.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 12: 화면 5 — 단일 경기 + 진단

"기계가 실수를 어떻게 짚었나"를 보여준다(C2, R2). 경기 하나를 크게 재생하고,
그 옆에 턴별 `loss` 그래프와 가장 나쁜 수 셋을 놓는다. 그래프의 봉우리를
클릭하면 그 턴으로 이동한다 — 기계의 판정과 화면이 같은 사건을 가리킨다는
것이 이 화면의 요점이다.

**Files:**
- Create: `web/src/lib/worst.ts`, `web/src/app/match/page.tsx`
- Test: `web/src/test/worst.test.ts`

**Interfaces:**
- Consumes: `loadBundle().gallery`, `loadBundle().diagnosis`, `decodeReplay`, `<ArenaCanvas />`
- Produces: `lossSeries(diagnosis: MatchDiagnosis, seat: 0 | 1): { turn: number; loss: number }[]`, `worstFor(diagnosis, seat): MoveAnalysis[]`

- [ ] **Step 1: 인덱스 규약을 못박는 실패하는 테스트를 쓴다**

이 화면의 유일한 실질 위험은 **턴 인덱스 규약이 섞이는 것**이다.
`reach`/`loss` 배열은 0-based이고 `MoveAnalysis.turn`은 1-based다
(Task 2의 `MatchDiagnosis` javadoc). 한 화면에서 둘을 같이 쓰므로
어긋나면 그래프의 봉우리와 "가장 나쁜 수"가 한 칸씩 밀린다.

```ts
import { describe, it, expect } from 'vitest';
import { lossSeries, worstFor } from '../lib/worst';
import { loadBundle } from '../lib/bundle';

const diagnosis = {
  matchId: 'm', reach: [[9, 8, 7], [9, 8, 7]], loss: [[0, 5, 0], [0, 0, 3]],
  occupancy: [0, 0], suicideRate: [0, 0],
  worstMoves0: [{ turn: 2, chose: 'UP', best: 'DOWN', reachAfterChosen: 3,
    reachAfterBest: 8, loss: 5, suicide: false, fatal: false }],
  worstMoves1: [],
} as never;

describe('진단 인덱스 규약', () => {
  it('loss 계열의 turn은 1-based로 나온다', () => {
    // 배열은 0-based, 화면과 MoveAnalysis는 1-based다. 여기서 변환한다.
    expect(lossSeries(diagnosis, 0)).toEqual([
      { turn: 1, loss: 0 }, { turn: 2, loss: 5 }, { turn: 3, loss: 0 },
    ]);
  });

  it('가장 나쁜 수의 turn이 loss 계열의 봉우리와 같은 턴을 가리킨다', () => {
    const peak = lossSeries(diagnosis, 0).reduce((a, b) => (b.loss > a.loss ? b : a));
    expect(worstFor(diagnosis, 0)[0].turn).toBe(peak.turn);
  });

  it('좌석 1은 좌석 1의 배열을 읽는다', () => {
    expect(lossSeries(diagnosis, 1).find((p) => p.loss === 3)?.turn).toBe(3);
  });

  it('데모 번들의 모든 경기에서 worstMoves의 turn이 경기 턴 수 안에 있다', () => {
    const { gallery, diagnosis: all } = loadBundle();
    all.forEach((d, i) => {
      [...d.worstMoves0, ...d.worstMoves1].forEach((m) => {
        expect(m.turn).toBeGreaterThanOrEqual(1);
        expect(m.turn).toBeLessThanOrEqual(gallery[i].result.turns);
      });
    });
  });
});
```

- [ ] **Step 2: 실패 확인 → 구현 → 통과 확인 → 빌드**

Run: `cd web && npm test && npm run build:demo`

화면에는 `fatal`이 참인 수를 별도로 표시한다 — 그건 엔진이 실제로 사망
판정한 턴이고, `loss`가 0이어도 그 경기를 끝낸 수다(계획 1의 판정: 정면
충돌은 자멸률에 넣지 않되 `fatal` 플래그로는 실어 나른다).

- [ ] **Step 3: 육안 확인 기록 + 커밋**

```bash
git add web
git commit -m "$(cat <<'EOF'
feat: 화면 5 단일 경기 + 진단 — 기계의 판정과 화면이 같은 턴을 가리킨다

이 화면의 유일한 실질 위험은 턴 인덱스 규약이 섞이는 것이다. reach·loss
배열은 0-based이고 MoveAnalysis.turn은 1-based인데 한 화면에서 둘을 같이
쓴다. 어긋나면 그래프의 봉우리와 "가장 나쁜 수"가 한 칸씩 밀리고, 그러면
이 화면이 주장하는 것("기계가 이 턴을 짚었다")이 거짓이 된다. 변환을 순수
함수 한 곳에 모으고 두 규약이 같은 턴을 가리키는지 테스트로 고정했다.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 13: 화면 6 — 히트맵과 과적합 격차

부록 화면. 순환 우위(A가 B를 이기고 B가 C를 이기는데 C가 A를 이기는 관계)와
심사/홀드아웃 승률 차를 보여준다.

**Files:**
- Create: `web/src/lib/heatmap.ts`, `web/src/app/heatmap/page.tsx`
- Test: `web/src/test/heatmap.test.ts`

**Interfaces:**
- Consumes: `loadBundle().roundRobin`, `loadBundle().generations`
- Produces: `cycles(matrix: (number|null)[][]): [number, number, number][]`, `overfitGap(stat: GenerationStat): number | null`

- [ ] **Step 1: 실패하는 테스트를 쓴다**

```ts
import { describe, it, expect } from 'vitest';
import { cycles, overfitGap } from '../lib/heatmap';

describe('순환 우위', () => {
  it('A>B>C>A를 찾아낸다', () => {
    // matrix[i][j] = i가 j를 상대로 낸 승점 승률. 0.5 초과면 우위.
    const m = [
      [null, 0.8, 0.2],
      [0.2, null, 0.8],
      [0.8, 0.2, null],
    ];
    expect(cycles(m)).toEqual([[0, 1, 2]]);
  });

  it('일관된 서열에는 순환이 없다', () => {
    const m = [
      [null, 0.8, 0.9],
      [0.2, null, 0.8],
      [0.1, 0.2, null],
    ];
    expect(cycles(m)).toEqual([]);
  });

  it('대각선 null에서 터지지 않는다', () => {
    expect(() => cycles([[null]])).not.toThrow();
    expect(cycles([[null]])).toEqual([]);
  });
});

describe('과적합 격차', () => {
  const stat = (scoreRate: number, holdoutScoreRate: number) =>
    ({ generation: 0, botName: 'X', avgSurvivalTurns: 0, occupancy: 0,
       suicideRate: 0, scoreRate, holdoutScoreRate, attempts: 1 });

  it('심사 − 홀드아웃이다', () => {
    expect(overfitGap(stat(0.70, 0.58))).toBeCloseTo(0.12);
  });

  it('홀드아웃이 NaN이면 격차를 주장하지 않는다', () => {
    // 승격한 시도가 없는 세대다. 0으로 그리면 "격차 없음"으로 읽혀
    // 과적합이 없다는 거짓 주장이 된다.
    expect(overfitGap(stat(0.70, NaN))).toBeNull();
  });
});
```

- [ ] **Step 2: 실패 확인 → 구현 → 통과 확인 → 빌드**

Run: `cd web && npm test && npm run build:demo`

히트맵은 대각선을 빈 칸으로 그린다(값이 `null`이다 — Task 18의 판정).
격차가 `null`인 세대는 막대를 그리지 않고 "승격 기록 없음"이라고 쓴다.

- [ ] **Step 3: 육안 확인 기록 + 커밋**

```bash
git add web
git commit -m "$(cat <<'EOF'
feat: 화면 6 히트맵과 과적합 격차

홀드아웃이 NaN인 세대는 격차를 주장하지 않는다. 0으로 그리면 "격차 없음"
으로 읽혀 "과적합이 없다"는 거짓 주장이 된다 — 사실은 승격한 시도가
아직 없는 것이다.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 14: 발표 셸 + 스모크 + CI

여섯 화면을 발표 순서(스펙 §9.2)로 묶고, 화면이 실제로 뜨는지를 기계가
확인하게 만든다.

**Files:**
- Modify: `web/src/app/layout.tsx`, `web/src/app/page.tsx`
- Create: `web/src/components/Deck.tsx`, `web/e2e/smoke.spec.ts`, `web/playwright.config.ts`
- Modify: `.github/workflows/ci.yml`
- Modify: `CLAUDE.md` (프론트엔드 절 추가)

**Interfaces:**
- Consumes: 앞의 여섯 화면
- Produces: 발표용 정적 사이트 `web/out/`

- [ ] **Step 1: 발표 셸을 만든다**

`/`가 여섯 화면의 목차이자 시작점이다. 순서는 스펙 §9.2를 그대로 따른다:
갤러리 → 개선 곡선 → 루프 타임라인 → 코드 diff → 단일 경기 → 히트맵.
**결과를 먼저, 과정은 나중이다** — 이 순서를 바꾸지 않는다.

발표자용 키보드 내비게이션: `←`/`→`로 화면 이동, `Space`로 재생/정지.
발표 중 마우스를 찾지 않아도 되게 한다(R4는 환경 의존을 줄이라는
요구이고, 조작 실수도 그 범주다).

- [ ] **Step 2: Playwright 스모크를 쓴다**

시각 회귀가 아니다(스펙 §14가 금지한다). **정적 export를 실제 브라우저로
열어 콘솔 오류 0건과 필수 요소의 존재만 본다.**

```ts
import { test, expect } from '@playwright/test';

const 화면들 = ['/', '/gallery', '/curve', '/loop', '/diff', '/match', '/heatmap'];

for (const path of 화면들) {
  test(`${path} — 콘솔 오류 없이 뜬다`, async ({ page }) => {
    const errors: string[] = [];
    page.on('console', (m) => { if (m.type() === 'error') errors.push(m.text()); });
    page.on('pageerror', (e) => errors.push(e.message));

    await page.goto(path);
    await expect(page.locator('body')).toBeVisible();
    expect(errors).toEqual([]);
  });
}

test('/gallery — 패널이 세대 수만큼 그려진다', async ({ page }) => {
  await page.goto('/gallery');
  // 데모 번들은 12세대다. 패널이 그보다 적으면 배치나 디코딩이 죽은 것이다.
  await expect(page.locator('[data-panel]')).toHaveCount(12);
});

test('/gallery — 재생하면 생존 턴 카운터가 올라간다', async ({ page }) => {
  await page.goto('/gallery');
  const counter = page.locator('[data-turn-counter]').first();
  const before = await counter.textContent();
  await page.getByRole('button', { name: '재생' }).click();
  await expect(counter).not.toHaveText(before ?? '');
});
```

`playwright.config.ts`는 `webServer`로 `npx serve out`을 띄우고
`PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1`·`PLAYWRIGHT_BROWSERS_PATH`를 존중한다
(이 환경에 Chromium이 이미 있다 — `playwright install`을 돌리지 않는다).

- [ ] **Step 3: 스모크를 실제로 돌린다**

```bash
cd web && npm run build:demo && npx playwright test
```
Expected: 전 케이스 PASS. **하나라도 콘솔 오류가 나오면 그 화면의
태스크로 돌아가 고친다** — 스모크를 느슨하게 만들지 않는다.

- [ ] **Step 4: CI에 프론트엔드 잡을 더한다**

`.github/workflows/ci.yml`에 잡을 하나 추가한다. 기존 `test` 잡은
그대로 둔다.

```yaml
  web:
    name: 프론트엔드
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '21', cache: gradle }
      - uses: actions/setup-node@v4
        with: { node-version: '22', cache: npm, cache-dependency-path: web/package-lock.json }

      # 데모 번들을 다시 만들어 커밋된 fixtures와 대조한다. 어긋나면
      # 백엔드가 wire 스키마를 바꿨는데 픽스처를 갱신하지 않은 것이다 —
      # 그러면 프론트 테스트가 낡은 데이터 위에서 초록 불을 낸다.
      - name: 데모 번들 재생성 대조
        run: |
          set -o pipefail
          ./gradlew fixture --console=plain --no-daemon | tee fixture.log
          code=$(grep -o 'ARENA_EXIT_CODE=[0-3]' fixture.log | tail -1 | cut -d= -f2)
          if [ "$code" != 0 ]; then
            echo "::error::데모 번들 생성 실패 ARENA_EXIT_CODE=${code:-없음}"
            exit 1
          fi
          git diff --exit-code -- web/fixtures \
            || { echo "::error::커밋된 데모 번들이 현재 코드의 출력과 다르다 — ./gradlew fixture 후 커밋할 것"; exit 1; }

      - run: npm ci
        working-directory: web
      - run: npm test
        working-directory: web
      - run: npm run build:demo
        working-directory: web
      - run: npx playwright test
        working-directory: web
        env:
          PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD: '1'
```

> 이 잡이 데모 번들을 재생성해 대조하는 것이 요점이다. 안 하면 백엔드가
> 필드를 바꿨을 때 프론트 테스트는 **낡은 픽스처** 위에서 계속 초록 불을
> 내고, 진짜 번들로 빌드하는 발표 당일에 처음 깨진다.

- [ ] **Step 5: `CLAUDE.md`에 프론트엔드 절을 더한다**

지금 `CLAUDE.md`는 봇 작성 규칙서(§5~§12)까지만 있다. §13으로 프론트엔드
절을 **덧붙인다**(기존 절을 고치지 않는다). 담을 것:

- `ARENA_BUNDLE`에 기본값이 없다는 것과 두 값의 의미
- `npm run build`는 진짜 번들, `build:demo`는 데모 번들이고 배너가 뜬다는 것
- 청/주황은 좌석 색이므로 다른 용도로 쓰지 않는다는 것
- **리플레이 디코더는 엔진 규칙의 사본이므로 고칠 때 conformance 테스트를
  반드시 함께 본다**는 것
- 화면은 번들의 수치를 재계산하지 않는다는 것(R1)

- [ ] **Step 6: 전체 확인**

```bash
./gradlew test          # BUILD SUCCESSFUL
cd web && npm test      # 전 케이스 PASS
npm run build:demo && npx playwright test
```

그리고 **진짜 번들로도 빌드가 되는지** 확인한다 — 세대가 하나뿐이라
화면은 초라하지만 깨지지는 않아야 한다.

```bash
./gradlew record && cd web && npm run build
```
Expected: 성공. 실패하면 어느 화면이 "세대가 2개 이상"을 암묵적으로
가정하고 있는 것이다. **데모 번들에만 맞춘 화면은 발표 당일 깨진다.**

- [ ] **Step 7: `log.md`와 커밋**

D79: 발표 순서를 스펙 §9.2 그대로 둔 이유, Playwright 스모크가 시각 회귀가
아니라 "콘솔 오류 0건"이라는 것, CI가 데모 번들을 재생성해 대조하는 이유,
세대 1개짜리 진짜 번들로도 빌드가 되어야 한다는 것.

```bash
git add web .github CLAUDE.md log.md
git commit -m "$(cat <<'EOF'
feat: 발표 셸과 스모크, CI 프론트엔드 잡

화면 순서는 스펙 §9.2 그대로다 — 결과를 먼저 보여주고 과정을 나중에
밝힌다. 과정을 먼저 설명하면 비개발자가 따라올 이유가 없다.

Playwright 스모크는 시각 회귀가 아니다(스펙 §14가 금지한다). 정적 export를
실제 브라우저로 열어 콘솔 오류 0건과 필수 요소의 존재만 본다 — 화면
태스크의 "육안 확인"에서 기계가 판정할 수 있는 부분만 떼어낸 것이다.

CI가 데모 번들을 재생성해 커밋된 것과 대조한다. 안 하면 백엔드가 wire
스키마를 바꿨을 때 프론트 테스트가 낡은 픽스처 위에서 계속 초록 불을
내고, 진짜 번들로 빌드하는 발표 당일에 처음 깨진다.

세대 1개짜리 진짜 번들로도 빌드가 되어야 한다. 데모 번들에만 맞춘 화면은
발표 당일 깨진다.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

## 이 계획이 남기는 것

- **스펙 §9의 여섯 화면 전부**와 그것을 먹이는 번들 보강 셋
- 화면 개발이 계획 3(세대 루프)에 묶이지 않게 하는 데모 번들
- 계획 1이 하네스에 세운 것과 같은 규율의 프론트엔드 계약: 스키마가
  strict이고, 디코더가 엔진과 대조되고, CI가 픽스처 드리프트를 잡는다

## 이 계획이 남기지 않는 것

- **세대 루프(계획 3)** — 진짜 12세대 데이터는 그쪽이 만든다. 이 계획은
  화면이 그 데이터를 받을 준비를 끝내는 것까지다
- 관문의 알려진 구멍(`static final` 가변 객체, 형제 최상위 클래스 회피).
  계획 1의 D70·D72가 후속 계획의 최우선 항목으로 남겼고, 화면과 무관하다
- `RecordStore`의 `String.format("gen-%02d", …)` 로케일 건(D72). Task 3이
  `SourceBundle`에서 같은 실수를 반복하지 않도록 `Locale.ROOT`를 쓰지만,
  기존 코드 쪽은 손대지 않는다 — 범위 밖 관찰을 그때그때 주워 담으면
  계획이 끝나지 않는다
