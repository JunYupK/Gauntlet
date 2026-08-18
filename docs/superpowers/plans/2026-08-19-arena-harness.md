# Bot Arena 하네스 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 트론 라이트사이클 봇들을 결정론적으로 겨루게 하고, 관문으로 검증하고, 실패를 기계가 진단하는 백엔드 하네스를 만든다.

**Architecture:** Gradle 멀티모듈 6개. `arena-core`가 순수 자바 게임 엔진이고 나머지가 단방향으로 의존한다. 봇은 `GameView`와 `Direction`만 볼 수 있어 "무엇을 감출 것인가"가 모듈 경계로 강제된다. Spring Boot는 CLI 진입점인 `arena-api`에만 존재하며, 엔진·관문·진단은 Spring을 모른다.

**Tech Stack:** Java 21, Gradle (Groovy DSL), JUnit 5, ASM 9.7 (바이트코드 검사), Jackson (JSON), Spring Boot 3.4 (CLI 진입점만)

**Spec:** [docs/superpowers/specs/2026-08-19-bot-arena-design.md](../specs/2026-08-19-bot-arena-design.md)

## Global Constraints

스펙에서 온 프로젝트 전역 요구사항. **모든 태스크의 요구사항에 암묵적으로 포함된다.**

- **격자는 30 × 30**, 최대 턴은 **900** (구조적 상한)
- **턴 판정은 순서 의존성이 없어야 한다.** `W(t)`를 고정한 채 두 봇의 목표 좌표를 계산한 뒤 동시에 판정한다. A와 B를 바꿔도 결과가 같아야 한다
- **대전 중 어떤 시간 제한도 걸지 않는다.** 성능은 G6에서만 판정한다 (시간 기반 판정은 R1을 깨뜨린다)
- **`arena-core`는 Spring 의존성을 갖지 않는다.** Spring은 `arena-api`에만 존재한다
- **의존은 단방향.** 관문은 봇을 알지만 봇은 관문을 모른다
- **봇은 무상태다.** 인스턴스 필드를 가질 수 없다
- **베이스라인 봇 3종은 한번 커밋한 뒤 수정하지 않는다**
- 심사 시드 = `1‥50`, 홀드아웃 시드 = `1001‥1050`, 라운드로빈 시드 = `1‥10`
- 승격 기준 **승점 승률 60%**, 무승부 **0.5점**, 세대당 재시도 한도 **5회**
- G4·G5·G6 국면 수 **10,000**, G6 상한 **p99 5 ms**
- **이 수치들은 루프가 통과하지 못한다는 이유로 낮추지 않는다** (BRIEF §11-4)
- 커밋 메시지는 한국어로 쓰고, 마지막 줄에 `Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>`을 넣는다

---

## 파일 구조

```
settings.gradle                              모듈 6개 선언
build.gradle                                 루트 공통 (Java 21, JUnit 5)

arena-core/          엔진. 의존 없음
  Direction.java         enum + 이동 델타 + 리플레이 인코딩 문자
  Point.java             record + move(Direction)
  Grid.java              소유자 배열(-1 빈칸 / 0 / 1). 벽 여부와 소유자를 함께 관리
  GameView.java          record. 봇에게 주는 유일한 입력
  DeathReason.java       enum
  MatchResult.java       record (winner, turns, reason)
  StartPositions.java    시드 → 두 시작 위치와 방향
  Replay.java            record + moves 문자열
  ReplayHash.java        SHA-256 정규화 해시
  Match.java             경기 실행 엔진. 이 프로젝트의 심장

arena-bots/          봇. core만 의존
  Bot.java                    인터페이스
  baseline/StraightBot.java   항상 직진
  baseline/RandomBot.java     시드 있는 균등 4방향
  baseline/WallAvoidBot.java  즉시 죽지 않는 방향 중 고정 우선순위
  gen/Gen00Bot.java           = StraightBot (챔피언 계보의 출발점)
  BotRegistry.java            이름 → 인스턴스 조회

arena-diagnostics/   진단. core만 의존
  FloodFill.java         reach 계산
  MoveAnalysis.java      record (turn, chose, best, loss …)
  MatchMetrics.java      record (reach, loss, occupancy, suicideRate)
  LossAnalyzer.java      리플레이 재생하며 loss·자멸률 계산

arena-gate/          관문. core + bots + diagnostics 의존
  Gate.java              인터페이스
  GateResult.java        record (passed, gateId, detail)
  GateReport.java        record (전체 결과 + JSON 직렬화 대상)
  PositionSampler.java   실제 대전에서 국면 10,000개 수집
  StatelessGate.java     G2
  ForbiddenApiGate.java  G3 (ASM)
  LegalMoveGate.java     G4
  DeterminismGate.java   G5
  TimeBudgetGate.java    G6
  RegressionGate.java    G7
  GateRunner.java        G2→G7 순차 실행, 첫 실패에서 중단

arena-tournament/    대전. 전부 의존
  MatchRunner.java       좌석 교대 + 병렬 실행
  Standing.java          승점 집계
  Championship.java      승격 판정 + 진단 리포트
  RoundRobin.java        전 세대 대진표
  RecordStore.java       records/ 이력 저장
  BundleBuilder.java     web/public/data/ 발표 번들 생성

arena-api/           CLI 진입점. Spring Boot
  ArenaApplication.java
  cli/GateCommand.java
  cli/ChallengeCommand.java
  cli/RecordCommand.java

CLAUDE.md            봇 작성 규칙서 (하네스의 문서 쪽 얼굴)
```

---

## Task 1: Gradle 멀티모듈 스캐폴딩 + 기본 타입

**Files:**
- Create: `settings.gradle`, `build.gradle`, `.gitignore`
- Create: `arena-core/build.gradle`
- Create: `arena-core/src/main/java/arena/core/Direction.java`
- Create: `arena-core/src/main/java/arena/core/Point.java`
- Test: `arena-core/src/test/java/arena/core/DirectionTest.java`

**Interfaces:**
- Consumes: 없음 (첫 태스크)
- Produces: `Direction` enum (`UP/DOWN/LEFT/RIGHT`, `int dx()`, `int dy()`, `char code()`, `Direction opposite()`, `static Direction fromCode(char)`), `Point` record (`int x()`, `int y()`, `Point move(Direction)`, `int manhattan(Point)`)

- [ ] **Step 1: Gradle 골격 생성**

`settings.gradle`:

```groovy
rootProject.name = 'bot-arena'

include 'arena-core'
include 'arena-bots'
include 'arena-diagnostics'
include 'arena-gate'
include 'arena-tournament'
include 'arena-api'
```

`build.gradle`:

```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.4.1' apply false
    id 'io.spring.dependency-management' version '1.1.7' apply false
}

subprojects {
    apply plugin: 'java'

    group = 'arena'
    version = '0.1.0'

    java {
        toolchain { languageVersion = JavaLanguageVersion.of(21) }
    }

    repositories { mavenCentral() }

    dependencies {
        testImplementation platform('org.junit:junit-bom:5.11.3')
        testImplementation 'org.junit.jupiter:junit-jupiter'
        testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
    }

    test {
        useJUnitPlatform()
        testLogging { events 'passed', 'failed', 'skipped' }
    }
}
```

`.gitignore`:

```
.gradle/
build/
!gradle/wrapper/gradle-wrapper.jar
.idea/
*.iml
records/
web/node_modules/
web/.next/
```

`arena-core/build.gradle` — **의존성이 없는 것이 이 모듈의 핵심 성질이다:**

```groovy
// arena-core는 어떤 프로덕션 의존성도 갖지 않는다.
// 이 파일에 implementation 한 줄이 추가되는 순간 설계 원칙이 깨진다.
```

- [ ] **Step 2: Gradle 래퍼 생성 및 빌드 확인**

Run:
```bash
gradle wrapper --gradle-version 8.11.1
./gradlew build
```
Expected: BUILD SUCCESSFUL (아직 소스가 없어 아무것도 컴파일하지 않음)

- [ ] **Step 3: 실패하는 테스트 작성**

`arena-core/src/test/java/arena/core/DirectionTest.java`:

```java
package arena.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DirectionTest {

    @Test
    void 각_방향은_고유한_인코딩_문자를_갖는다() {
        assertEquals('U', Direction.UP.code());
        assertEquals('D', Direction.DOWN.code());
        assertEquals('L', Direction.LEFT.code());
        assertEquals('R', Direction.RIGHT.code());
    }

    @Test
    void 인코딩_문자로부터_방향을_복원한다() {
        for (Direction d : Direction.values()) {
            assertEquals(d, Direction.fromCode(d.code()));
        }
    }

    @Test
    void 반대_방향은_델타의_부호가_뒤집힌다() {
        for (Direction d : Direction.values()) {
            Direction o = d.opposite();
            assertEquals(-d.dx(), o.dx());
            assertEquals(-d.dy(), o.dy());
        }
    }

    @Test
    void 점은_방향으로_한_칸_이동한다() {
        Point p = new Point(5, 5);
        assertEquals(new Point(5, 4), p.move(Direction.UP));
        assertEquals(new Point(5, 6), p.move(Direction.DOWN));
        assertEquals(new Point(4, 5), p.move(Direction.LEFT));
        assertEquals(new Point(6, 5), p.move(Direction.RIGHT));
    }

    @Test
    void 맨해튼_거리를_잰다() {
        assertEquals(7, new Point(1, 2).manhattan(new Point(5, 5)));
        assertEquals(0, new Point(3, 3).manhattan(new Point(3, 3)));
    }
}
```

- [ ] **Step 4: 테스트 실패 확인**

Run: `./gradlew :arena-core:test`
Expected: 컴파일 실패 — `Direction` / `Point` 클래스가 없음

- [ ] **Step 5: 최소 구현**

`arena-core/src/main/java/arena/core/Direction.java`:

```java
package arena.core;

/** y축은 아래로 증가한다 (화면 좌표계). */
public enum Direction {
    UP(0, -1),
    DOWN(0, 1),
    LEFT(-1, 0),
    RIGHT(1, 0);

    private final int dx;
    private final int dy;

    Direction(int dx, int dy) {
        this.dx = dx;
        this.dy = dy;
    }

    public int dx() { return dx; }
    public int dy() { return dy; }

    /** 리플레이 moves 문자열의 한 글자. 네 방향의 첫 글자가 서로 다르다. */
    public char code() { return name().charAt(0); }

    public Direction opposite() {
        return switch (this) {
            case UP -> DOWN;
            case DOWN -> UP;
            case LEFT -> RIGHT;
            case RIGHT -> LEFT;
        };
    }

    public static Direction fromCode(char c) {
        return switch (c) {
            case 'U' -> UP;
            case 'D' -> DOWN;
            case 'L' -> LEFT;
            case 'R' -> RIGHT;
            default -> throw new IllegalArgumentException("알 수 없는 방향 문자: " + c);
        };
    }
}
```

`arena-core/src/main/java/arena/core/Point.java`:

```java
package arena.core;

public record Point(int x, int y) {

    public Point move(Direction d) {
        return new Point(x + d.dx(), y + d.dy());
    }

    public int manhattan(Point other) {
        return Math.abs(x - other.x) + Math.abs(y - other.y);
    }
}
```

- [ ] **Step 6: 테스트 통과 확인**

Run: `./gradlew :arena-core:test`
Expected: 5 tests PASS

- [ ] **Step 7: 커밋**

```bash
git add settings.gradle build.gradle .gitignore gradle/ gradlew gradlew.bat arena-core/
git commit -m "$(cat <<'EOF'
feat: Gradle 멀티모듈 골격과 Direction/Point 타입

arena-core는 프로덕션 의존성을 갖지 않는다. 엔진이 순수 자바
함수여야 테스트가 밀리초로 끝나고 재현성 검증이 간단해진다.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: Grid와 GameView

격자는 벽 여부만이 아니라 **소유자**를 기록한다. 시각화에서 두 봇을 다른 색으로 그려야 하고, 사망 원인이 자기 벽인지 상대 벽인지 구분해야 하기 때문이다.

**Files:**
- Create: `arena-core/src/main/java/arena/core/Grid.java`
- Create: `arena-core/src/main/java/arena/core/GameView.java`
- Test: `arena-core/src/test/java/arena/core/GridTest.java`

**Interfaces:**
- Consumes: `Point`, `Direction` (Task 1)
- Produces: `Grid` (`Grid(int w, int h)`, `int width()`, `int height()`, `boolean inBounds(Point)`, `boolean isWall(Point)`, `int ownerAt(Point)`, `void claim(Point, int owner)`, `boolean[][] wallSnapshot()`, `int[][] ownerSnapshot()`), `GameView` record (`int width`, `int height`, `boolean[][] wall`, `Point myHead`, `Direction myDir`, `Point oppHead`, `Direction oppDir`, `int turn`)

- [ ] **Step 1: 실패하는 테스트 작성**

`arena-core/src/test/java/arena/core/GridTest.java`:

```java
package arena.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GridTest {

    @Test
    void 새_격자는_전부_빈칸이다() {
        Grid g = new Grid(30, 30);
        assertFalse(g.isWall(new Point(0, 0)));
        assertFalse(g.isWall(new Point(29, 29)));
        assertEquals(Grid.EMPTY, g.ownerAt(new Point(15, 15)));
    }

    @Test
    void 점유한_칸은_벽이_되고_소유자가_기록된다() {
        Grid g = new Grid(30, 30);
        g.claim(new Point(4, 7), 1);
        assertTrue(g.isWall(new Point(4, 7)));
        assertEquals(1, g.ownerAt(new Point(4, 7)));
    }

    @Test
    void 격자_밖을_판별한다() {
        Grid g = new Grid(30, 30);
        assertTrue(g.inBounds(new Point(0, 0)));
        assertTrue(g.inBounds(new Point(29, 29)));
        assertFalse(g.inBounds(new Point(-1, 0)));
        assertFalse(g.inBounds(new Point(30, 0)));
        assertFalse(g.inBounds(new Point(0, 30)));
    }

    @Test
    void 스냅샷은_방어적_복사라서_봇이_원본을_훼손할_수_없다() {
        Grid g = new Grid(5, 5);
        g.claim(new Point(1, 1), 0);

        boolean[][] snapshot = g.wallSnapshot();
        snapshot[1][1] = false;
        snapshot[4][4] = true;

        assertTrue(g.isWall(new Point(1, 1)), "원본이 훼손되었다");
        assertFalse(g.isWall(new Point(4, 4)), "원본이 훼손되었다");
    }

    @Test
    void 스냅샷은_y_x_순서로_인덱싱된다() {
        Grid g = new Grid(10, 20);
        g.claim(new Point(3, 7), 0);

        boolean[][] wall = g.wallSnapshot();
        assertEquals(20, wall.length, "바깥 배열은 height");
        assertEquals(10, wall[0].length, "안쪽 배열은 width");
        assertTrue(wall[7][3], "wall[y][x] 순서여야 한다");
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :arena-core:test --tests '*GridTest*'`
Expected: 컴파일 실패 — `Grid` 클래스가 없음

- [ ] **Step 3: 최소 구현**

`arena-core/src/main/java/arena/core/Grid.java`:

```java
package arena.core;

/**
 * 격자의 점유 상태. 벽 여부와 소유자를 함께 관리한다.
 *
 * 소유자를 기록하는 이유가 둘 있다. 시각화에서 두 봇의 궤적을 다른 색으로
 * 그려야 하고, 사망 원인이 자기 벽인지 상대 벽인지 구분해야 한다.
 */
public final class Grid {

    public static final int EMPTY = -1;

    private final int width;
    private final int height;
    private final int[][] owner;   // [y][x]

    public Grid(int width, int height) {
        this.width = width;
        this.height = height;
        this.owner = new int[height][width];
        for (int[] row : owner) {
            java.util.Arrays.fill(row, EMPTY);
        }
    }

    public int width() { return width; }
    public int height() { return height; }

    public boolean inBounds(Point p) {
        return p.x() >= 0 && p.x() < width && p.y() >= 0 && p.y() < height;
    }

    public boolean isWall(Point p) {
        return owner[p.y()][p.x()] != EMPTY;
    }

    public int ownerAt(Point p) {
        return owner[p.y()][p.x()];
    }

    public void claim(Point p, int botIndex) {
        owner[p.y()][p.x()] = botIndex;
    }

    /** 봇에게 넘길 방어적 복사본. 봇이 훼손해도 엔진 상태는 안전하다. */
    public boolean[][] wallSnapshot() {
        boolean[][] wall = new boolean[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                wall[y][x] = owner[y][x] != EMPTY;
            }
        }
        return wall;
    }

    public int[][] ownerSnapshot() {
        int[][] copy = new int[height][width];
        for (int y = 0; y < height; y++) {
            copy[y] = owner[y].clone();
        }
        return copy;
    }

    public Grid copy() {
        Grid g = new Grid(width, height);
        for (int y = 0; y < height; y++) {
            g.owner[y] = owner[y].clone();
        }
        return g;
    }
}
```

`arena-core/src/main/java/arena/core/GameView.java`:

```java
package arena.core;

/**
 * 봇이 볼 수 있는 전부. 봇은 이것 말고는 세상에 접근할 수단이 없다.
 *
 * wall은 이동 전 벽 집합 W(t)이며 방어적 복사본이다.
 * 히스토리는 제공하지 않는다 — 봇은 무상태 순수 함수다.
 */
public record GameView(
        int width,
        int height,
        boolean[][] wall,
        Point myHead,
        Direction myDir,
        Point oppHead,
        Direction oppDir,
        int turn
) {
    public boolean isWall(int x, int y) {
        return wall[y][x];
    }

    public boolean inBounds(int x, int y) {
        return x >= 0 && x < width && y >= 0 && y < height;
    }

    /** 그 방향으로 한 칸 가면 즉시 죽는가. 봇이 가장 자주 묻는 질문이다. */
    public boolean isDeadly(Direction d) {
        Point p = myHead.move(d);
        return !inBounds(p.x(), p.y()) || wall[p.y()][p.x()];
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :arena-core:test --tests '*GridTest*'`
Expected: 5 tests PASS

- [ ] **Step 5: 커밋**

```bash
git add arena-core/
git commit -m "$(cat <<'EOF'
feat: Grid와 GameView 추가

Grid는 벽 여부만이 아니라 소유자를 기록한다. 시각화에서 두 봇의
궤적을 다른 색으로 그려야 하고, 사망 원인이 자기 벽인지 상대
벽인지 구분해야 하기 때문이다.

GameView가 봇에게 주는 전부이며 wall은 방어적 복사본이다.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: 시드 → 시작 배치

**Files:**
- Create: `arena-core/src/main/java/arena/core/StartPositions.java`
- Test: `arena-core/src/test/java/arena/core/StartPositionsTest.java`

**Interfaces:**
- Consumes: `Point`, `Direction` (Task 1)
- Produces: `StartPositions` record (`Point p0`, `Direction d0`, `Point p1`, `Direction d1`) + `static StartPositions of(long seed, int width, int height)`

- [ ] **Step 1: 실패하는 테스트 작성**

`arena-core/src/test/java/arena/core/StartPositionsTest.java`:

```java
package arena.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StartPositionsTest {

    @Test
    void 같은_시드는_항상_같은_배치를_낸다() {
        for (long seed = 1; seed <= 50; seed++) {
            StartPositions a = StartPositions.of(seed, 30, 30);
            StartPositions b = StartPositions.of(seed, 30, 30);
            assertEquals(a, b, "시드 " + seed + "이 재현되지 않았다");
        }
    }

    @Test
    void 다른_시드는_대체로_다른_배치를_낸다() {
        long distinct = java.util.stream.LongStream.rangeClosed(1, 50)
                .mapToObj(s -> StartPositions.of(s, 30, 30))
                .distinct()
                .count();
        assertTrue(distinct >= 45, "시드 50개 중 서로 다른 배치가 " + distinct + "개뿐이다");
    }

    @Test
    void 시작_위치는_가장자리에서_최소_3칸_안쪽이다() {
        for (long seed = 1; seed <= 50; seed++) {
            StartPositions sp = StartPositions.of(seed, 30, 30);
            for (Point p : new Point[]{sp.p0(), sp.p1()}) {
                assertTrue(p.x() >= 3 && p.x() <= 26, "x가 여백을 벗어남: " + p);
                assertTrue(p.y() >= 3 && p.y() <= 26, "y가 여백을 벗어남: " + p);
            }
        }
    }

    @Test
    void 두_시작_위치의_맨해튼_거리는_10_이상이다() {
        for (long seed = 1; seed <= 50; seed++) {
            StartPositions sp = StartPositions.of(seed, 30, 30);
            assertTrue(sp.p0().manhattan(sp.p1()) >= 10,
                    "시드 " + seed + "의 두 봇이 너무 가깝다");
        }
    }

    @Test
    void 심사_시드와_홀드아웃_시드는_겹치지_않는다() {
        var judging = java.util.stream.LongStream.rangeClosed(1, 50)
                .mapToObj(s -> StartPositions.of(s, 30, 30))
                .collect(java.util.stream.Collectors.toSet());
        var holdout = java.util.stream.LongStream.rangeClosed(1001, 1050)
                .mapToObj(s -> StartPositions.of(s, 30, 30))
                .collect(java.util.stream.Collectors.toSet());

        judging.retainAll(holdout);
        assertTrue(judging.size() <= 2,
                "두 시드 집합의 배치가 " + judging.size() + "개나 겹친다");
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :arena-core:test --tests '*StartPositionsTest*'`
Expected: 컴파일 실패 — `StartPositions` 클래스가 없음

- [ ] **Step 3: 최소 구현**

`arena-core/src/main/java/arena/core/StartPositions.java`:

```java
package arena.core;

import java.util.Random;

/**
 * 시드로부터 두 봇의 시작 위치와 초기 방향을 만든다.
 *
 * java.util.Random은 알고리즘이 명세로 고정되어 있어 JVM이 바뀌어도
 * 같은 시드가 같은 수열을 낸다. R1의 전제다.
 */
public record StartPositions(Point p0, Direction d0, Point p1, Direction d1) {

    /** 가장자리 여백. 어느 방향으로 출발해도 최소 3턴은 살아남는다. */
    private static final int MARGIN = 3;

    /** 초반 즉시 접촉을 막는 최소 거리. */
    private static final int MIN_DISTANCE = 10;

    private static final int MAX_ATTEMPTS = 1000;

    public static StartPositions of(long seed, int width, int height) {
        Random rng = new Random(seed);

        int spanX = width - 2 * MARGIN;
        int spanY = height - 2 * MARGIN;

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            Point a = new Point(MARGIN + rng.nextInt(spanX), MARGIN + rng.nextInt(spanY));
            Point b = new Point(MARGIN + rng.nextInt(spanX), MARGIN + rng.nextInt(spanY));

            if (a.manhattan(b) >= MIN_DISTANCE) {
                Direction da = Direction.values()[rng.nextInt(4)];
                Direction db = Direction.values()[rng.nextInt(4)];
                return new StartPositions(a, da, b, db);
            }
        }

        throw new IllegalStateException(
                "시드 " + seed + ": " + MAX_ATTEMPTS + "회 시도에도 배치를 못 만들었다. "
                        + "격자가 너무 좁거나 MIN_DISTANCE가 너무 크다.");
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :arena-core:test --tests '*StartPositionsTest*'`
Expected: 5 tests PASS

- [ ] **Step 5: 커밋**

```bash
git add arena-core/
git commit -m "$(cat <<'EOF'
feat: 시드로부터 시작 배치를 생성

가장자리 3칸 여백과 맨해튼 거리 10 이상을 강제한다. 여백은 어느
방향으로 출발해도 최소 3턴을 보장하고, 거리 제약은 초반 즉시
접촉을 막는다.

java.util.Random은 알고리즘이 명세로 고정되어 있어 JVM이 바뀌어도
같은 시드가 같은 수열을 낸다.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: Bot 인터페이스와 베이스라인 봇 3종

엔진보다 봇을 먼저 만든다. 엔진 테스트에 상대가 필요하기 때문이다.

**Files:**
- Create: `arena-bots/build.gradle`
- Create: `arena-bots/src/main/java/arena/bots/Bot.java`
- Create: `arena-bots/src/main/java/arena/bots/baseline/StraightBot.java`
- Create: `arena-bots/src/main/java/arena/bots/baseline/RandomBot.java`
- Create: `arena-bots/src/main/java/arena/bots/baseline/WallAvoidBot.java`
- Test: `arena-bots/src/test/java/arena/bots/BaselineBotTest.java`

**Interfaces:**
- Consumes: `GameView`, `Direction`, `Point` (Task 1, 2)
- Produces: `Bot` 인터페이스 (`String name()`, `Direction move(GameView view)`), `StraightBot`, `RandomBot`, `WallAvoidBot` — 모두 무인자 생성자

- [ ] **Step 1: 빌드 파일 작성**

`arena-bots/build.gradle`:

```groovy
dependencies {
    implementation project(':arena-core')
    testImplementation project(':arena-core')
}
```

- [ ] **Step 2: 실패하는 테스트 작성**

`arena-bots/src/test/java/arena/bots/BaselineBotTest.java`:

```java
package arena.bots;

import arena.bots.baseline.RandomBot;
import arena.bots.baseline.StraightBot;
import arena.bots.baseline.WallAvoidBot;
import arena.core.Direction;
import arena.core.GameView;
import arena.core.Point;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BaselineBotTest {

    /** 벽이 하나도 없는 30x30 격자에서 내 머리가 (15,15), 방향은 RIGHT. */
    private GameView emptyView(Direction myDir) {
        return new GameView(30, 30, new boolean[30][30],
                new Point(15, 15), myDir,
                new Point(5, 5), Direction.LEFT, 1);
    }

    @Test
    void 직진봇은_항상_현재_방향을_유지한다() {
        StraightBot bot = new StraightBot();
        for (Direction d : Direction.values()) {
            assertEquals(d, bot.move(emptyView(d)));
        }
    }

    @Test
    void 랜덤봇은_같은_국면에_항상_같은_답을_낸다() {
        RandomBot bot = new RandomBot();
        GameView view = emptyView(Direction.RIGHT);
        Direction first = bot.move(view);
        for (int i = 0; i < 100; i++) {
            assertEquals(first, bot.move(view), "랜덤봇이 결정론적이지 않다");
        }
    }

    @Test
    void 랜덤봇은_국면이_다르면_대체로_다른_답을_낸다() {
        RandomBot bot = new RandomBot();
        long distinct = java.util.stream.IntStream.range(0, 200)
                .mapToObj(t -> new GameView(30, 30, new boolean[30][30],
                        new Point(15, 15), Direction.RIGHT,
                        new Point(5, 5), Direction.LEFT, t))
                .map(bot::move)
                .distinct()
                .count();
        assertTrue(distinct >= 3, "랜덤봇이 사실상 한 방향만 낸다");
    }

    @Test
    void 벽회피봇은_죽지_않는_방향을_고른다() {
        boolean[][] wall = new boolean[30][30];
        // (15,15) 주변에서 UP, LEFT, RIGHT를 막는다. DOWN만 살길이다.
        wall[14][15] = true;  // UP
        wall[15][14] = true;  // LEFT
        wall[15][16] = true;  // RIGHT

        GameView view = new GameView(30, 30, wall,
                new Point(15, 15), Direction.RIGHT,
                new Point(5, 5), Direction.LEFT, 1);

        assertEquals(Direction.DOWN, new WallAvoidBot().move(view));
    }

    @Test
    void 벽회피봇은_사방이_막혀도_유효한_방향을_반환한다() {
        boolean[][] wall = new boolean[30][30];
        wall[14][15] = true;
        wall[16][15] = true;
        wall[15][14] = true;
        wall[15][16] = true;

        GameView view = new GameView(30, 30, wall,
                new Point(15, 15), Direction.RIGHT,
                new Point(5, 5), Direction.LEFT, 1);

        assertNotNull(new WallAvoidBot().move(view), "죽더라도 null을 내면 안 된다");
    }

    @Test
    void 벽회피봇은_격자_밖도_죽음으로_친다() {
        GameView view = new GameView(30, 30, new boolean[30][30],
                new Point(0, 0), Direction.LEFT,
                new Point(20, 20), Direction.LEFT, 1);

        Direction chosen = new WallAvoidBot().move(view);
        assertTrue(chosen == Direction.DOWN || chosen == Direction.RIGHT,
                "격자 밖으로 나가는 방향을 골랐다: " + chosen);
    }

    @Test
    void 모든_베이스라인_봇은_인스턴스_필드가_없다() {
        for (Class<?> c : new Class<?>[]{StraightBot.class, RandomBot.class, WallAvoidBot.class}) {
            for (var f : c.getDeclaredFields()) {
                assertTrue(java.lang.reflect.Modifier.isStatic(f.getModifiers()),
                        c.getSimpleName() + "에 인스턴스 필드가 있다: " + f.getName());
            }
        }
    }
}
```

- [ ] **Step 3: 테스트 실패 확인**

Run: `./gradlew :arena-bots:test`
Expected: 컴파일 실패 — `Bot`, `StraightBot` 등이 없음

- [ ] **Step 4: 최소 구현**

`arena-bots/src/main/java/arena/bots/Bot.java`:

```java
package arena.bots;

import arena.core.Direction;
import arena.core.GameView;

/**
 * 봇은 무상태 순수 함수다.
 *
 * 구현체는 인스턴스 필드를 가질 수 없다. 이 제약이 "같은 입력 → 같은 출력"을
 * 인터페이스 수준에서 강제하며, G2가 리플렉션으로 기계 판정한다.
 */
public interface Bot {

    String name();

    Direction move(GameView view);
}
```

`arena-bots/src/main/java/arena/bots/baseline/StraightBot.java`:

```java
package arena.bots.baseline;

import arena.bots.Bot;
import arena.core.Direction;
import arena.core.GameView;

/**
 * 항상 가던 방향으로 간다. 벽에 박아 죽는다.
 *
 * 챔피언 계보의 출발점(Gen 0)이기도 하다. 직선 하나가 그려지다 멈추는
 * 그림은 "얘는 아무 생각이 없다"가 설명 없이 전달된다.
 *
 * 베이스라인 봇은 한번 커밋한 뒤 수정하지 않는다. 기준이 움직이면
 * 세대 간 비교가 무의미해진다.
 */
public final class StraightBot implements Bot {

    @Override
    public String name() { return "StraightBot"; }

    @Override
    public Direction move(GameView view) {
        return view.myDir();
    }
}
```

`arena-bots/src/main/java/arena/bots/baseline/RandomBot.java`:

```java
package arena.bots.baseline;

import arena.bots.Bot;
import arena.core.Direction;
import arena.core.GameView;

/**
 * 국면에서 유도한 시드로 방향을 고른다. 후진 자멸로 금방 죽는다.
 *
 * java.util.Random을 무인자로 생성하면 시계에 의존해 R1이 깨진다.
 * 국면을 시드로 삼으면 무작위처럼 보이면서도 완전히 결정론적이다.
 */
public final class RandomBot implements Bot {

    @Override
    public String name() { return "RandomBot"; }

    @Override
    public Direction move(GameView view) {
        int h = 17;
        h = h * 31 + view.myHead().x();
        h = h * 31 + view.myHead().y();
        h = h * 31 + view.turn();
        h = h * 31 + view.myDir().ordinal();
        return Direction.values()[Math.floorMod(h, 4)];
    }
}
```

`arena-bots/src/main/java/arena/bots/baseline/WallAvoidBot.java`:

```java
package arena.bots.baseline;

import arena.bots.Bot;
import arena.core.Direction;
import arena.core.GameView;

/**
 * 한 수 앞만 본다. 즉시 죽지 않는 방향 중 고정 우선순위로 고른다.
 *
 * 위 둘을 압도하지만 공간을 못 읽어서 자기 영역에 갇혀 죽는다.
 * G7 회귀 방지의 최상단 기준선이다.
 */
public final class WallAvoidBot implements Bot {

    private static final Direction[] PRIORITY = {
            Direction.RIGHT, Direction.DOWN, Direction.LEFT, Direction.UP
    };

    @Override
    public String name() { return "WallAvoidBot"; }

    @Override
    public Direction move(GameView view) {
        for (Direction d : PRIORITY) {
            if (!view.isDeadly(d)) {
                return d;
            }
        }
        // 사방이 막혔다. 어차피 죽지만 유효한 방향은 내야 한다.
        return view.myDir();
    }
}
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew :arena-bots:test`
Expected: 7 tests PASS

- [ ] **Step 6: 커밋**

```bash
git add arena-bots/
git commit -m "$(cat <<'EOF'
feat: Bot 인터페이스와 베이스라인 봇 3종

베이스라인은 한번 커밋한 뒤 수정하지 않는다. G7 회귀 방지와 R3
배수 계산이 모두 이 셋의 강도에 의존하므로, 기준이 움직이면 세대
간 비교가 무의미해진다.

RandomBot은 java.util.Random 대신 국면 해시를 쓴다. 무작위처럼
보이면서도 완전히 결정론적이어야 R1이 유지된다.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: 경기 엔진

이 프로젝트의 심장이다. 여기가 틀리면 이후 모든 판정이 무의미하다.

**Files:**
- Create: `arena-core/src/main/java/arena/core/DeathReason.java`
- Create: `arena-core/src/main/java/arena/core/MatchResult.java`
- Create: `arena-core/src/main/java/arena/core/BotFunction.java`
- Create: `arena-core/src/main/java/arena/core/Match.java`
- Test: `arena-core/src/test/java/arena/core/MatchTest.java`

`arena-core`는 `arena-bots`에 의존할 수 없으므로 (의존은 단방향), 엔진은 `Bot` 대신 함수형 인터페이스 `BotFunction`을 받는다.

**Interfaces:**
- Consumes: `Grid`, `GameView`, `StartPositions`, `Point`, `Direction`
- Produces: `BotFunction` (`Direction move(GameView)`), `DeathReason` enum, `MatchResult` record (`int winner`, `int turns`, `DeathReason reason`), `Match.play(String id0, BotFunction b0, String id1, BotFunction b1, long seed, int width, int height)` → `Replay` (Task 6에서 `Replay` 타입 완성 전까지는 `MatchResult`를 반환하는 `Match.playResult(...)`를 먼저 만든다)

- [ ] **Step 1: 실패하는 테스트 작성**

`arena-core/src/test/java/arena/core/MatchTest.java`:

```java
package arena.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MatchTest {

    /** 항상 같은 방향만 내는 봇. */
    private static BotFunction always(Direction d) {
        return view -> d;
    }

    /** 즉시 죽지 않는 방향을 고정 우선순위로 고르는 봇. */
    private static BotFunction avoid() {
        return view -> {
            for (Direction d : new Direction[]{
                    Direction.RIGHT, Direction.DOWN, Direction.LEFT, Direction.UP}) {
                if (!view.isDeadly(d)) return d;
            }
            return view.myDir();
        };
    }

    @Test
    void 벽에_박은_쪽이_진다() {
        // 시드 7의 배치를 그대로 쓰되, 한쪽만 계속 위로 달려 격자 밖으로 나가게 한다.
        MatchResult r = Match.playResult("a", always(Direction.UP), "b", avoid(), 7, 30, 30);

        assertEquals(1, r.winner(), "격자 밖으로 나간 0번이 져야 한다");
        assertEquals(DeathReason.P0_OUT_OF_BOUNDS, r.reason());
    }

    @Test
    void 판정은_순서에_의존하지_않는다() {
        for (long seed = 1; seed <= 50; seed++) {
            MatchResult ab = Match.playResult("a", avoid(), "b", always(Direction.UP), seed, 30, 30);
            MatchResult ba = Match.playResult("b", always(Direction.UP), "a", avoid(), seed, 30, 30);

            // 좌석을 바꿔도 "누가 이겼는가"는 같아야 한다.
            int winnerAB = ab.winner() < 0 ? -1 : (ab.winner() == 0 ? 0 : 1);
            int winnerBA = ba.winner() < 0 ? -1 : (ba.winner() == 0 ? 1 : 0);
            assertEquals(winnerAB, winnerBA, "시드 " + seed + "에서 순서 의존성이 발견됐다");
        }
    }

    @Test
    void 같은_시드와_같은_봇은_항상_같은_결과를_낸다() {
        for (long seed = 1; seed <= 20; seed++) {
            MatchResult first = Match.playResult("a", avoid(), "b", avoid(), seed, 30, 30);
            for (int i = 0; i < 5; i++) {
                assertEquals(first, Match.playResult("a", avoid(), "b", avoid(), seed, 30, 30),
                        "시드 " + seed + "이 재현되지 않았다");
            }
        }
    }

    @Test
    void 경기는_반드시_900턴_이내에_끝난다() {
        for (long seed = 1; seed <= 50; seed++) {
            MatchResult r = Match.playResult("a", avoid(), "b", avoid(), seed, 30, 30);
            assertTrue(r.turns() <= 900, "시드 " + seed + "이 " + r.turns() + "턴이나 갔다");
            assertNotEquals(DeathReason.MAX_TURNS, r.reason(),
                    "시드 " + seed + "이 턴 상한에 걸렸다 — 종료 보장이 깨졌다");
        }
    }

    @Test
    void 시작_칸이_벽이라_후진하면_자기_벽에_박는다() {
        StartPositions sp = StartPositions.of(3, 30, 30);
        // 0번 봇이 처음부터 반대로 간다 = 시작 칸으로 되돌아간다.
        MatchResult r = Match.playResult(
                "back", always(sp.d0().opposite()), "avoid", avoid(), 3, 30, 30);

        assertEquals(1, r.winner());
        assertEquals(DeathReason.P0_HIT_OWN_WALL, r.reason());
        assertEquals(1, r.turns(), "첫 턴에 끝나야 한다");
    }

    @Test
    void 양쪽이_같은_칸에_동시_진입하면_무승부다() {
        // 두 봇을 마주보게 두고 서로에게 직진시킨다.
        MatchResult r = Match.headOnForTest(30, 30);
        assertEquals(-1, r.winner());
        assertEquals(DeathReason.HEAD_ON_COLLISION, r.reason());
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :arena-core:test --tests '*MatchTest*'`
Expected: 컴파일 실패 — `Match`, `MatchResult`, `DeathReason`, `BotFunction`이 없음

- [ ] **Step 3: 최소 구현**

`arena-core/src/main/java/arena/core/BotFunction.java`:

```java
package arena.core;

/**
 * 엔진이 보는 봇. arena-core는 arena-bots에 의존할 수 없으므로
 * (의존은 단방향) 인터페이스가 아니라 함수를 받는다.
 */
@FunctionalInterface
public interface BotFunction {
    Direction move(GameView view);
}
```

`arena-core/src/main/java/arena/core/DeathReason.java`:

```java
package arena.core;

public enum DeathReason {
    P0_OUT_OF_BOUNDS,
    P0_HIT_OWN_WALL,
    P0_HIT_OPPONENT_WALL,
    P1_OUT_OF_BOUNDS,
    P1_HIT_OWN_WALL,
    P1_HIT_OPPONENT_WALL,
    /** 양쪽이 같은 칸에 동시 진입. */
    HEAD_ON_COLLISION,
    /** 양쪽이 각자 다른 이유로 같은 턴에 사망. */
    BOTH_DIED,
    /** 도달 불가능해야 하는 안전장치. 걸리면 엔진 버그다. */
    MAX_TURNS
}
```

`arena-core/src/main/java/arena/core/MatchResult.java`:

```java
package arena.core;

/** winner: 0 또는 1, 무승부는 -1. */
public record MatchResult(int winner, int turns, DeathReason reason) {

    public boolean isDraw() { return winner < 0; }
}
```

`arena-core/src/main/java/arena/core/Match.java`:

```java
package arena.core;

/**
 * 경기 실행 엔진.
 *
 * 턴 판정의 핵심은 벽 집합 W(t)를 고정한 채로 두 봇의 목표 좌표를
 * 계산한 뒤 동시에 판정하는 것이다. A와 B를 바꿔도 결과가 같으므로
 * 선후공 이점이 존재하지 않는다.
 *
 * 대전 중에는 어떤 시간 제한도 걸지 않는다. 시간 기반 판정은
 * 같은 조건에 다른 결과를 내어 R1을 깨뜨린다.
 */
public final class Match {

    private Match() {}

    public static MatchResult playResult(
            String id0, BotFunction bot0,
            String id1, BotFunction bot1,
            long seed, int width, int height) {

        StartPositions sp = StartPositions.of(seed, width, height);
        Grid grid = new Grid(width, height);

        Point[] head = { sp.p0(), sp.p1() };
        Direction[] dir = { sp.d0(), sp.d1() };

        // 시작 칸을 즉시 벽으로 만든다. 덕분에 후진은 별도 규칙 없이
        // 자기 벽 충돌로 자연 사망한다.
        grid.claim(head[0], 0);
        grid.claim(head[1], 1);

        int maxTurns = width * height;

        for (int turn = 1; turn <= maxTurns; turn++) {
            // 1) W(t)를 고정한 채로 두 봇의 의사를 각각 묻는다.
            Direction d0 = bot0.move(viewFor(grid, head, dir, 0, turn));
            Direction d1 = bot1.move(viewFor(grid, head, dir, 1, turn));

            Point p0 = head[0].move(d0);
            Point p1 = head[1].move(d1);

            // 2) 같은 W(t)를 기준으로 동시에 판정한다.
            boolean dead0 = !grid.inBounds(p0) || grid.isWall(p0) || p0.equals(p1);
            boolean dead1 = !grid.inBounds(p1) || grid.isWall(p1) || p1.equals(p0);

            if (dead0 || dead1) {
                return resolve(grid, p0, p1, dead0, dead1, turn);
            }

            // 3) 생존한 봇에 대해서만 벽을 확정한다.
            grid.claim(p0, 0);
            grid.claim(p1, 1);
            head[0] = p0; head[1] = p1;
            dir[0] = d0;  dir[1] = d1;
        }

        // 매 턴 벽이 2칸씩 늘어나므로 여기 도달할 수 없다.
        return new MatchResult(-1, maxTurns, DeathReason.MAX_TURNS);
    }

    private static GameView viewFor(Grid grid, Point[] head, Direction[] dir, int me, int turn) {
        int opp = 1 - me;
        return new GameView(
                grid.width(), grid.height(), grid.wallSnapshot(),
                head[me], dir[me], head[opp], dir[opp], turn);
    }

    private static MatchResult resolve(
            Grid grid, Point p0, Point p1, boolean dead0, boolean dead1, int turn) {

        if (dead0 && dead1) {
            DeathReason reason = p0.equals(p1)
                    ? DeathReason.HEAD_ON_COLLISION
                    : DeathReason.BOTH_DIED;
            return new MatchResult(-1, turn, reason);
        }
        if (dead0) {
            return new MatchResult(1, turn, reasonFor(grid, p0, 0));
        }
        return new MatchResult(0, turn, reasonFor(grid, p1, 1));
    }

    private static DeathReason reasonFor(Grid grid, Point p, int botIndex) {
        if (!grid.inBounds(p)) {
            return botIndex == 0 ? DeathReason.P0_OUT_OF_BOUNDS : DeathReason.P1_OUT_OF_BOUNDS;
        }
        boolean own = grid.ownerAt(p) == botIndex;
        if (botIndex == 0) {
            return own ? DeathReason.P0_HIT_OWN_WALL : DeathReason.P0_HIT_OPPONENT_WALL;
        }
        return own ? DeathReason.P1_HIT_OWN_WALL : DeathReason.P1_HIT_OPPONENT_WALL;
    }

    /**
     * 정면 충돌을 재현하는 테스트 전용 진입점.
     * 두 봇을 같은 행에 짝수 칸 간격으로 마주보게 두고 서로에게 직진시킨다.
     */
    static MatchResult headOnForTest(int width, int height) {
        Grid grid = new Grid(width, height);
        int y = height / 2;
        Point a = new Point(10, y);
        Point b = new Point(16, y);   // 거리 6 = 짝수라 정확히 가운데서 만난다

        Point[] head = { a, b };
        Direction[] dir = { Direction.RIGHT, Direction.LEFT };
        grid.claim(a, 0);
        grid.claim(b, 1);

        for (int turn = 1; turn <= width * height; turn++) {
            Point p0 = head[0].move(dir[0]);
            Point p1 = head[1].move(dir[1]);

            boolean dead0 = !grid.inBounds(p0) || grid.isWall(p0) || p0.equals(p1);
            boolean dead1 = !grid.inBounds(p1) || grid.isWall(p1) || p1.equals(p0);

            if (dead0 || dead1) {
                return resolve(grid, p0, p1, dead0, dead1, turn);
            }
            grid.claim(p0, 0);
            grid.claim(p1, 1);
            head[0] = p0; head[1] = p1;
        }
        return new MatchResult(-1, width * height, DeathReason.MAX_TURNS);
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :arena-core:test --tests '*MatchTest*'`
Expected: 6 tests PASS

한 테스트라도 실패하면 **멈추고 원인을 파악한다.** 엔진이 틀린 채로 진행하면 이후 모든 판정이 무의미하다.

- [ ] **Step 5: 커밋**

```bash
git add arena-core/
git commit -m "$(cat <<'EOF'
feat: 경기 실행 엔진

벽 집합 W(t)를 고정한 채 두 봇의 목표 좌표를 계산하고 동시에
판정한다. A와 B를 바꿔도 결과가 같으므로 선후공 이점이 존재하지
않는다.

시작 칸을 0턴에 벽으로 만들어 후진이 별도 규칙 없이 자기 벽
충돌로 자연 사망하게 했다. 매 턴 벽이 2칸씩 늘어나므로 경기는
격자 칸 수 이내에 반드시 끝난다.

대전 중에는 어떤 시간 제한도 걸지 않는다.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 6: 속성 테스트 — 엔진의 불변식

예제 테스트는 내가 생각한 경우만 검사한다. 속성 테스트는 시드를 대량으로 돌려 **생각하지 못한 경우**를 잡는다.

**Files:**
- Test: `arena-core/src/test/java/arena/core/MatchPropertyTest.java`
- Modify: `arena-core/src/main/java/arena/core/Match.java` (턴별 관찰자 훅 추가)

**Interfaces:**
- Consumes: `Match.playResult` (Task 5)
- Produces: `Match.playResult(..., TurnObserver observer)` 오버로드, `TurnObserver` (`void onTurn(int turn, Grid gridAfter, Point[] heads)`)

- [ ] **Step 1: 실패하는 테스트 작성**

`arena-core/src/test/java/arena/core/MatchPropertyTest.java`:

```java
package arena.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 엔진의 불변식을 시드 500개로 검증한다.
 * 결정론이므로 실패하면 그 시드로 언제든 재현할 수 있다.
 */
class MatchPropertyTest {

    private static final int SEEDS = 500;

    private static BotFunction avoid() {
        return view -> {
            for (Direction d : new Direction[]{
                    Direction.RIGHT, Direction.DOWN, Direction.LEFT, Direction.UP}) {
                if (!view.isDeadly(d)) return d;
            }
            return view.myDir();
        };
    }

    private static BotFunction hugLeft() {
        return view -> {
            for (Direction d : new Direction[]{
                    Direction.UP, Direction.LEFT, Direction.DOWN, Direction.RIGHT}) {
                if (!view.isDeadly(d)) return d;
            }
            return view.myDir();
        };
    }

    @Test
    void 속성_대칭성_좌석을_바꿔도_승자가_같다() {
        for (long seed = 1; seed <= SEEDS; seed++) {
            MatchResult ab = Match.playResult("a", avoid(), "b", hugLeft(), seed, 30, 30);
            MatchResult ba = Match.playResult("b", hugLeft(), "a", avoid(), seed, 30, 30);

            assertEquals(ab.turns(), ba.turns(), "시드 " + seed + ": 턴 수가 다르다");

            String winnerAB = ab.isDraw() ? "draw" : (ab.winner() == 0 ? "a" : "b");
            String winnerBA = ba.isDraw() ? "draw" : (ba.winner() == 0 ? "b" : "a");
            assertEquals(winnerAB, winnerBA, "시드 " + seed + ": 승자가 다르다");
        }
    }

    @Test
    void 속성_종료_보장_어떤_조합도_격자_칸수_이내에_끝난다() {
        BotFunction[] bots = { avoid(), hugLeft(), v -> Direction.UP, v -> v.myDir() };

        for (long seed = 1; seed <= 100; seed++) {
            for (BotFunction b0 : bots) {
                for (BotFunction b1 : bots) {
                    MatchResult r = Match.playResult("x", b0, "y", b1, seed, 30, 30);
                    assertTrue(r.turns() <= 900,
                            "시드 " + seed + ": " + r.turns() + "턴");
                    assertNotEquals(DeathReason.MAX_TURNS, r.reason(),
                            "시드 " + seed + ": 턴 상한에 걸렸다");
                }
            }
        }
    }

    @Test
    void 속성_벽_단조성_벽은_줄지_않고_생존자_수만큼_늘어난다() {
        for (long seed = 1; seed <= 100; seed++) {
            int[] previous = { 2 };   // 시작 칸 2개

            Match.playResult("a", avoid(), "b", hugLeft(), seed, 30, 30,
                    (turn, gridAfter, heads) -> {
                        int count = countWalls(gridAfter);
                        assertEquals(previous[0] + 2, count,
                                "시드 " + seed + " 턴 " + turn + ": 벽이 2칸씩 늘지 않았다");
                        previous[0] = count;
                    });
        }
    }

    private static int countWalls(Grid grid) {
        int[][] owner = grid.ownerSnapshot();
        int count = 0;
        for (int[] row : owner) {
            for (int cell : row) {
                if (cell != Grid.EMPTY) count++;
            }
        }
        return count;
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :arena-core:test --tests '*MatchPropertyTest*'`
Expected: 컴파일 실패 — `TurnObserver`를 받는 `playResult` 오버로드가 없음

- [ ] **Step 3: 관찰자 훅 추가**

`arena-core/src/main/java/arena/core/Match.java`에 다음을 추가한다.

먼저 파일 상단 `private Match() {}` 아래에 인터페이스를 넣는다:

```java
    /** 턴이 끝날 때마다 호출된다. 테스트와 진단이 엔진 내부를 관찰하는 통로. */
    @FunctionalInterface
    public interface TurnObserver {
        void onTurn(int turn, Grid gridAfter, Point[] heads);
    }

    /** 관찰자 없이 돌린다. */
    public static MatchResult playResult(
            String id0, BotFunction bot0,
            String id1, BotFunction bot1,
            long seed, int width, int height) {
        return playResult(id0, bot0, id1, bot1, seed, width, height, (t, g, h) -> {});
    }
```

그리고 기존 `playResult` 본문의 시그니처를 다음으로 바꾸고, 벽을 확정한 직후에 관찰자를 호출한다:

```java
    public static MatchResult playResult(
            String id0, BotFunction bot0,
            String id1, BotFunction bot1,
            long seed, int width, int height,
            TurnObserver observer) {
```

`grid.claim(p1, 1);` 다음 줄, `head[0] = p0;` 앞에 삽입:

```java
            observer.onTurn(turn, grid, new Point[]{ p0, p1 });
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :arena-core:test`
Expected: 모든 테스트 PASS (속성 테스트 3개 포함)

`속성_종료_보장`은 시드 100 × 봇 조합 16 = 1,600경기를 돌린다. 수 초 안에 끝나야 한다. 더 걸리면 엔진에 성능 문제가 있다.

- [ ] **Step 5: 커밋**

```bash
git add arena-core/
git commit -m "$(cat <<'EOF'
test: 엔진 불변식 속성 테스트 3종

대칭성, 종료 보장, 벽 단조성을 시드 500개로 검증한다. 예제
테스트는 내가 생각한 경우만 검사하지만 속성 테스트는 생각하지
못한 경우를 잡는다.

결정론이라 실패한 시드로 언제든 재현할 수 있다.

턴별 관찰자 훅을 추가했다. 테스트와 진단이 엔진 내부를 관찰하는
통로이며, 엔진 자체는 관찰자의 존재를 신경 쓰지 않는다.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 7: 리플레이 인코딩과 해시

**Files:**
- Create: `arena-core/src/main/java/arena/core/Replay.java`
- Create: `arena-core/src/main/java/arena/core/ReplayHash.java`
- Modify: `arena-core/src/main/java/arena/core/Match.java` (`play` 메서드 추가)
- Test: `arena-core/src/test/java/arena/core/ReplayTest.java`

**Interfaces:**
- Consumes: `MatchResult`, `StartPositions`, `Match.playResult`
- Produces: `Replay` record (아래 필드), `ReplayHash.of(Replay)` → `String`, `Match.play(String id0, BotFunction b0, String id1, BotFunction b1, long seed, int width, int height)` → `Replay`

- [ ] **Step 1: 실패하는 테스트 작성**

`arena-core/src/test/java/arena/core/ReplayTest.java`:

```java
package arena.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReplayTest {

    private static BotFunction avoid() {
        return view -> {
            for (Direction d : new Direction[]{
                    Direction.RIGHT, Direction.DOWN, Direction.LEFT, Direction.UP}) {
                if (!view.isDeadly(d)) return d;
            }
            return view.myDir();
        };
    }

    @Test
    void moves는_턴당_2문자다() {
        Replay r = Match.play("a", avoid(), "b", avoid(), 5, 30, 30);
        assertEquals(r.result().turns() * 2, r.moves().length());
    }

    @Test
    void moves는_UDLR만_담는다() {
        Replay r = Match.play("a", avoid(), "b", avoid(), 5, 30, 30);
        assertTrue(r.moves().matches("[UDLR]+"), "예상 못한 문자: " + r.moves());
    }

    @Test
    void 같은_경기는_같은_해시를_낸다() {
        Replay a = Match.play("a", avoid(), "b", avoid(), 5, 30, 30);
        Replay b = Match.play("a", avoid(), "b", avoid(), 5, 30, 30);
        assertEquals(a.hash(), b.hash());
        assertTrue(a.hash().startsWith("sha256:"));
    }

    @Test
    void 다른_시드는_다른_해시를_낸다() {
        Replay a = Match.play("a", avoid(), "b", avoid(), 5, 30, 30);
        Replay b = Match.play("a", avoid(), "b", avoid(), 6, 30, 30);
        assertNotEquals(a.hash(), b.hash());
    }

    @Test
    void 리플레이는_시작_배치를_기록한다() {
        StartPositions sp = StartPositions.of(5, 30, 30);
        Replay r = Match.play("alpha", avoid(), "beta", avoid(), 5, 30, 30);

        assertEquals("alpha", r.bot0Id());
        assertEquals("beta", r.bot1Id());
        assertEquals(sp.p0(), r.start0());
        assertEquals(sp.d0(), r.dir0());
        assertEquals(sp.p1(), r.start1());
        assertEquals(sp.d1(), r.dir1());
        assertEquals(5L, r.seed());
    }

    @Test
    void 짧은_경기의_리플레이는_1KB_미만이다() {
        Replay r = Match.play("a", avoid(), "b", avoid(), 5, 30, 30);
        // moves가 전체 크기를 지배한다. 187턴이면 374바이트.
        assertTrue(r.moves().length() < 1800,
                "moves가 " + r.moves().length() + "자나 된다");
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :arena-core:test --tests '*ReplayTest*'`
Expected: 컴파일 실패 — `Replay`, `Match.play`가 없음

- [ ] **Step 3: 최소 구현**

`arena-core/src/main/java/arena/core/Replay.java`:

```java
package arena.core;

/**
 * 한 경기의 완전한 기록.
 *
 * moves는 턴당 2문자(먼저 봇0, 다음 봇1)이며 사망을 초래한 마지막
 * 이동까지 포함한다. 187턴 경기가 374바이트라, 리플레이를 선별하지
 * 않고 전부 남길 수 있다.
 *
 * metrics는 진단이 필요한 경기에만 채운다. null일 수 있다.
 */
public record Replay(
        int schema,
        String matchId,
        int width,
        int height,
        long seed,
        boolean swapped,
        String bot0Id, Point start0, Direction dir0,
        String bot1Id, Point start1, Direction dir1,
        String moves,
        MatchResult result,
        String hash
) {
    public static final int SCHEMA = 1;

    /** metrics를 붙인 사본. Replay 자체는 metrics를 모른다 (core는 진단에 의존하지 않는다). */
    public Replay withMatchId(String newId) {
        return new Replay(schema, newId, width, height, seed, swapped,
                bot0Id, start0, dir0, bot1Id, start1, dir1, moves, result, hash);
    }

    /** 턴 t(1-based)에서 봇 i가 낸 방향. */
    public Direction moveAt(int turn, int botIndex) {
        return Direction.fromCode(moves.charAt((turn - 1) * 2 + botIndex));
    }
}
```

`arena-core/src/main/java/arena/core/ReplayHash.java`:

```java
package arena.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 리플레이의 정규화 해시.
 *
 * record --verify가 전체 실험을 재실행해 이 값을 대조한다.
 * 발표에서 R1을 주장할 때 그 명령의 출력이 곧 증거가 된다.
 */
public final class ReplayHash {

    private ReplayHash() {}

    public static String of(
            String bot0Id, String bot1Id, long seed,
            int width, int height, String moves, MatchResult result) {

        String canonical = String.join("|",
                String.valueOf(Replay.SCHEMA),
                bot0Id, bot1Id,
                String.valueOf(seed),
                width + "x" + height,
                moves,
                String.valueOf(result.winner()),
                String.valueOf(result.turns()),
                result.reason().name());

        return "sha256:" + hex(sha256(canonical));
    }

    private static byte[] sha256(String s) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(s.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 쓸 수 없는 JVM", e);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
```

`arena-core/src/main/java/arena/core/Match.java`에 `play` 메서드를 추가한다. `playResult`와 중복 없이 만들기 위해, `playResult`의 본문에서 `moves`를 수집하도록 고친다.

`playResult(…, TurnObserver observer)` 본문 시작 부분에 추가:

```java
        StringBuilder moves = new StringBuilder();
```

`Point p1 = head[1].move(d1);` 바로 아래에 추가 (판정 전에 기록해야 마지막 치명적 이동도 남는다):

```java
            moves.append(d0.code()).append(d1.code());
```

그리고 `playResult`의 반환 타입을 바꾸는 대신, 다음 메서드를 새로 추가한다:

```java
    /** moves까지 기록하는 완전한 경기 실행. */
    public static Replay play(
            String id0, BotFunction bot0,
            String id1, BotFunction bot1,
            long seed, int width, int height) {

        StringBuilder moves = new StringBuilder();
        MatchResult result = playInternal(bot0, bot1, seed, width, height,
                (t, g, h) -> {}, moves);

        StartPositions sp = StartPositions.of(seed, width, height);
        String hash = ReplayHash.of(id0, id1, seed, width, height, moves.toString(), result);

        return new Replay(
                Replay.SCHEMA,
                id0 + "-vs-" + id1 + "-seed" + seed,
                width, height, seed, false,
                id0, sp.p0(), sp.d0(),
                id1, sp.p1(), sp.d1(),
                moves.toString(), result, hash);
    }
```

`playResult(…, observer)`의 본문 전체를 `playInternal`로 옮기고, 두 공개 메서드가 모두 이를 호출하게 한다:

```java
    public static MatchResult playResult(
            String id0, BotFunction bot0,
            String id1, BotFunction bot1,
            long seed, int width, int height,
            TurnObserver observer) {
        return playInternal(bot0, bot1, seed, width, height, observer, new StringBuilder());
    }

    private static MatchResult playInternal(
            BotFunction bot0, BotFunction bot1,
            long seed, int width, int height,
            TurnObserver observer, StringBuilder moves) {
        // ← 기존 playResult 본문을 그대로 옮긴다
    }
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :arena-core:test`
Expected: 모든 테스트 PASS

- [ ] **Step 5: 커밋**

```bash
git add arena-core/
git commit -m "$(cat <<'EOF'
feat: 리플레이 인코딩과 SHA-256 정규화 해시

moves를 턴당 2문자로 인코딩해 187턴 경기가 374바이트에 담긴다.
덕분에 리플레이를 선별하지 않고 전부 남길 수 있다.

해시는 record --verify가 전체 실험을 재실행해 대조할 대상이다.
발표에서 R1을 주장할 때 그 명령의 출력이 곧 증거가 된다.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 8: flood fill과 손실 분석

`loss` 정의 하나가 반려 피드백, 자멸률, 화면 하이라이트를 모두 낸다. 구현 비용을 한 번만 내는 지점이다.

**Files:**
- Create: `arena-diagnostics/build.gradle`
- Create: `arena-diagnostics/src/main/java/arena/diagnostics/FloodFill.java`
- Create: `arena-diagnostics/src/main/java/arena/diagnostics/MoveAnalysis.java`
- Create: `arena-diagnostics/src/main/java/arena/diagnostics/MatchMetrics.java`
- Create: `arena-diagnostics/src/main/java/arena/diagnostics/LossAnalyzer.java`
- Test: `arena-diagnostics/src/test/java/arena/diagnostics/FloodFillTest.java`
- Test: `arena-diagnostics/src/test/java/arena/diagnostics/LossAnalyzerTest.java`

**Interfaces:**
- Consumes: `Grid`, `Point`, `Direction`, `Replay`, `Match` (Task 2, 5, 7)
- Produces:
  - `FloodFill.reach(Grid grid, Point head)` → `int`
  - `MoveAnalysis` record (`int turn`, `Direction chose`, `Direction best`, `int reachAfterChosen`, `int reachAfterBest`, `int loss`, `boolean suicide`)
  - `MatchMetrics` record (`int[][] reach`, `int[][] loss`, `double[] occupancy`, `double[] suicideRate`)
  - `LossAnalyzer.analyze(Replay replay)` → `MatchMetrics`
  - `LossAnalyzer.worstMoves(Replay replay, int botIndex, int limit)` → `List<MoveAnalysis>`

- [ ] **Step 1: 빌드 파일 작성**

`arena-diagnostics/build.gradle`:

```groovy
dependencies {
    implementation project(':arena-core')
    testImplementation project(':arena-core')
}
```

- [ ] **Step 2: 실패하는 테스트 작성**

`arena-diagnostics/src/test/java/arena/diagnostics/FloodFillTest.java`:

```java
package arena.diagnostics;

import arena.core.Grid;
import arena.core.Point;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FloodFillTest {

    @Test
    void 빈_5x5에서_머리_한_칸을_뺀_24칸에_닿는다() {
        Grid g = new Grid(5, 5);
        Point head = new Point(2, 2);
        g.claim(head, 0);

        assertEquals(24, FloodFill.reach(g, head));
    }

    @Test
    void 사방이_막히면_0칸이다() {
        Grid g = new Grid(5, 5);
        Point head = new Point(2, 2);
        g.claim(head, 0);
        g.claim(new Point(2, 1), 1);
        g.claim(new Point(2, 3), 1);
        g.claim(new Point(1, 2), 1);
        g.claim(new Point(3, 2), 1);

        assertEquals(0, FloodFill.reach(g, head));
    }

    @Test
    void 벽으로_갈린_반대편은_세지_않는다() {
        Grid g = new Grid(5, 5);
        // x=2 열을 위아래로 완전히 막는다.
        for (int y = 0; y < 5; y++) {
            g.claim(new Point(2, y), 1);
        }
        Point head = new Point(0, 0);
        g.claim(head, 0);

        // 왼쪽 영역은 x=0,1 두 열 = 10칸, 머리 1칸 제외하면 9칸.
        assertEquals(9, FloodFill.reach(g, head));
    }

    @Test
    void 모서리에_갇히면_그_방만_센다() {
        Grid g = new Grid(5, 5);
        g.claim(new Point(0, 1), 1);
        g.claim(new Point(1, 1), 1);
        g.claim(new Point(1, 0), 1);

        Point head = new Point(0, 0);
        g.claim(head, 0);

        assertEquals(0, FloodFill.reach(g, head));
    }
}
```

`arena-diagnostics/src/test/java/arena/diagnostics/LossAnalyzerTest.java`:

```java
package arena.diagnostics;

import arena.core.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class LossAnalyzerTest {

    private static BotFunction avoid() {
        return view -> {
            for (Direction d : new Direction[]{
                    Direction.RIGHT, Direction.DOWN, Direction.LEFT, Direction.UP}) {
                if (!view.isDeadly(d)) return d;
            }
            return view.myDir();
        };
    }

    /** 대안이 있는데도 첫 턴에 후진해 자살하는 봇. */
    private static BotFunction suicidal() {
        return view -> view.myDir().opposite();
    }

    @Test
    void 손실은_항상_0_이상이다() {
        Replay r = Match.play("a", avoid(), "b", avoid(), 5, 30, 30);
        MatchMetrics m = LossAnalyzer.analyze(r);

        for (int bot = 0; bot < 2; bot++) {
            for (int loss : m.loss()[bot]) {
                assertTrue(loss >= 0, "손실이 음수다: " + loss);
            }
        }
    }

    @Test
    void 자살한_봇은_자멸로_기록된다() {
        Replay r = Match.play("suicide", suicidal(), "avoid", avoid(), 5, 30, 30);
        MatchMetrics m = LossAnalyzer.analyze(r);

        assertEquals(1.0, m.suicideRate()[0], 1e-9,
                "첫 턴에 대안을 두고 자살했는데 자멸로 안 잡혔다");
        assertEquals(0.0, m.suicideRate()[1], 1e-9);
    }

    @Test
    void 자살한_수가_최악의_수로_뽑힌다() {
        Replay r = Match.play("suicide", suicidal(), "avoid", avoid(), 5, 30, 30);
        List<MoveAnalysis> worst = LossAnalyzer.worstMoves(r, 0, 3);

        assertFalse(worst.isEmpty());
        MoveAnalysis top = worst.get(0);
        assertEquals(0, top.reachAfterChosen(), "자살한 수의 reach는 0이어야 한다");
        assertTrue(top.loss() > 0, "자살했는데 손실이 0이다");
        assertTrue(top.suicide());
    }

    @Test
    void 점유율은_자기_벽_칸수를_전체로_나눈_값이다() {
        Replay r = Match.play("a", avoid(), "b", avoid(), 5, 30, 30);
        MatchMetrics m = LossAnalyzer.analyze(r);

        double total = m.occupancy()[0] + m.occupancy()[1];
        assertTrue(total > 0 && total <= 1.0, "점유율 합이 이상하다: " + total);
    }

    @Test
    void reach_배열의_길이는_턴_수와_같다() {
        Replay r = Match.play("a", avoid(), "b", avoid(), 5, 30, 30);
        MatchMetrics m = LossAnalyzer.analyze(r);

        assertEquals(r.result().turns(), m.reach()[0].length);
        assertEquals(r.result().turns(), m.loss()[0].length);
    }
}
```

- [ ] **Step 3: 테스트 실패 확인**

Run: `./gradlew :arena-diagnostics:test`
Expected: 컴파일 실패 — `FloodFill`, `LossAnalyzer` 등이 없음

- [ ] **Step 4: 최소 구현**

`arena-diagnostics/src/main/java/arena/diagnostics/FloodFill.java`:

```java
package arena.diagnostics;

import arena.core.Direction;
import arena.core.Grid;
import arena.core.Point;

import java.util.ArrayDeque;
import java.util.Deque;

/** 머리에서 닿을 수 있는 빈 칸의 수. 머리 자신은 세지 않는다. */
public final class FloodFill {

    private FloodFill() {}

    public static int reach(Grid grid, Point head) {
        int width = grid.width();
        int height = grid.height();
        boolean[][] seen = new boolean[height][width];

        Deque<Point> queue = new ArrayDeque<>();
        seen[head.y()][head.x()] = true;
        queue.add(head);

        int count = 0;
        while (!queue.isEmpty()) {
            Point p = queue.poll();
            for (Direction d : Direction.values()) {
                Point n = p.move(d);
                if (!grid.inBounds(n)) continue;
                if (seen[n.y()][n.x()]) continue;
                if (grid.isWall(n)) continue;

                seen[n.y()][n.x()] = true;
                count++;
                queue.add(n);
            }
        }
        return count;
    }
}
```

`arena-diagnostics/src/main/java/arena/diagnostics/MoveAnalysis.java`:

```java
package arena.diagnostics;

import arena.core.Direction;

/**
 * 한 수에 대한 판정.
 *
 * loss = 최선 대안의 reach − 실제 선택의 reach.
 * 실제 선택도 대안 후보에 포함되므로 loss는 항상 0 이상이다.
 */
public record MoveAnalysis(
        int turn,
        Direction chose,
        Direction best,
        int reachAfterChosen,
        int reachAfterBest,
        int loss,
        boolean suicide
) {}
```

`arena-diagnostics/src/main/java/arena/diagnostics/MatchMetrics.java`:

```java
package arena.diagnostics;

/** 봇별·턴별 지표. 바깥 인덱스가 봇, 안쪽이 턴이다. */
public record MatchMetrics(
        int[][] reach,
        int[][] loss,
        double[] occupancy,
        double[] suicideRate
) {}
```

`arena-diagnostics/src/main/java/arena/diagnostics/LossAnalyzer.java`:

```java
package arena.diagnostics;

import arena.core.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 리플레이를 재생하며 각 수의 손실을 잰다.
 *
 * 이 클래스가 내는 값 하나로 셋을 해결한다.
 *   1. 반려 피드백  — 패배 경기의 손실 상위 수
 *   2. 자멸률       — R3 보조 지표
 *   3. 화면 하이라이트 — "이 순간 봇이 졌습니다"
 */
public final class LossAnalyzer {

    private LossAnalyzer() {}

    public static MatchMetrics analyze(Replay replay) {
        List<List<MoveAnalysis>> perBot = replayAndAnalyze(replay);

        int turns = replay.result().turns();
        int[][] reach = new int[2][turns];
        int[][] loss = new int[2][turns];
        int[] suicides = new int[2];

        for (int bot = 0; bot < 2; bot++) {
            List<MoveAnalysis> analyses = perBot.get(bot);
            for (int i = 0; i < turns; i++) {
                MoveAnalysis a = analyses.get(i);
                reach[bot][i] = a.reachAfterChosen();
                loss[bot][i] = a.loss();
                if (a.suicide()) suicides[bot]++;
            }
        }

        Grid finalGrid = replayToFinalGrid(replay);
        int cells = replay.width() * replay.height();
        int[] owned = new int[2];
        int[][] owner = finalGrid.ownerSnapshot();
        for (int[] row : owner) {
            for (int cell : row) {
                if (cell == 0) owned[0]++;
                else if (cell == 1) owned[1]++;
            }
        }

        return new MatchMetrics(reach, loss,
                new double[]{ (double) owned[0] / cells, (double) owned[1] / cells },
                new double[]{ (double) suicides[0] / turns, (double) suicides[1] / turns });
    }

    public static List<MoveAnalysis> worstMoves(Replay replay, int botIndex, int limit) {
        return replayAndAnalyze(replay).get(botIndex).stream()
                .sorted(Comparator.comparingInt(MoveAnalysis::loss).reversed())
                .limit(limit)
                .toList();
    }

    private static List<List<MoveAnalysis>> replayAndAnalyze(Replay replay) {
        int width = replay.width();
        int height = replay.height();
        Grid grid = new Grid(width, height);

        Point[] head = { replay.start0(), replay.start1() };
        grid.claim(head[0], 0);
        grid.claim(head[1], 1);

        List<List<MoveAnalysis>> perBot = List.of(new ArrayList<>(), new ArrayList<>());
        int turns = replay.result().turns();

        for (int turn = 1; turn <= turns; turn++) {
            Direction[] chose = { replay.moveAt(turn, 0), replay.moveAt(turn, 1) };

            for (int bot = 0; bot < 2; bot++) {
                perBot.get(bot).add(analyzeMove(grid, head[bot], bot, chose[bot], turn));
            }

            Point p0 = head[0].move(chose[0]);
            Point p1 = head[1].move(chose[1]);

            boolean dead0 = !grid.inBounds(p0) || grid.isWall(p0) || p0.equals(p1);
            boolean dead1 = !grid.inBounds(p1) || grid.isWall(p1) || p1.equals(p0);
            if (dead0 || dead1) break;

            grid.claim(p0, 0);
            grid.claim(p1, 1);
            head[0] = p0;
            head[1] = p1;
        }
        return perBot;
    }

    /** 네 방향 각각에 대해 reach를 재고, 실제 선택과의 차이를 손실로 삼는다. */
    private static MoveAnalysis analyzeMove(
            Grid grid, Point head, int botIndex, Direction chose, int turn) {

        int bestReach = -1;
        Direction best = chose;
        int chosenReach = 0;
        boolean anySafe = false;

        for (Direction d : Direction.values()) {
            int r = reachAfter(grid, head, botIndex, d);
            if (r > 0 || isSafe(grid, head, d)) anySafe = anySafe || isSafe(grid, head, d);
            if (r > bestReach) {
                bestReach = r;
                best = d;
            }
            if (d == chose) chosenReach = r;
        }

        boolean suicide = !isSafe(grid, head, chose) && anySafe;
        return new MoveAnalysis(turn, chose, best, chosenReach, bestReach,
                bestReach - chosenReach, suicide);
    }

    private static boolean isSafe(Grid grid, Point head, Direction d) {
        Point p = head.move(d);
        return grid.inBounds(p) && !grid.isWall(p);
    }

    /** 그 방향으로 갔을 때의 reach. 즉시 사망이면 0. */
    private static int reachAfter(Grid grid, Point head, int botIndex, Direction d) {
        if (!isSafe(grid, head, d)) return 0;

        Point p = head.move(d);
        Grid next = grid.copy();
        next.claim(p, botIndex);
        return FloodFill.reach(next, p);
    }

    private static Grid replayToFinalGrid(Replay replay) {
        Grid grid = new Grid(replay.width(), replay.height());
        Point[] head = { replay.start0(), replay.start1() };
        grid.claim(head[0], 0);
        grid.claim(head[1], 1);

        for (int turn = 1; turn <= replay.result().turns(); turn++) {
            Point p0 = head[0].move(replay.moveAt(turn, 0));
            Point p1 = head[1].move(replay.moveAt(turn, 1));

            boolean dead0 = !grid.inBounds(p0) || grid.isWall(p0) || p0.equals(p1);
            boolean dead1 = !grid.inBounds(p1) || grid.isWall(p1) || p1.equals(p0);
            if (dead0 || dead1) break;

            grid.claim(p0, 0);
            grid.claim(p1, 1);
            head[0] = p0;
            head[1] = p1;
        }
        return grid;
    }
}
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew :arena-diagnostics:test`
Expected: 9 tests PASS

- [ ] **Step 6: 커밋**

```bash
git add arena-diagnostics/
git commit -m "$(cat <<'EOF'
feat: flood fill 기반 손실 분석기

loss = 최선 대안의 reach − 실제 선택의 reach. 실제 선택도 후보에
포함되므로 손실은 항상 0 이상이다.

이 정의 하나가 셋을 해결한다. 반려 피드백의 치명적인 수, R3
보조 지표인 자멸률, 발표 화면의 "이 순간 봇이 졌습니다"
하이라이트가 모두 같은 값에서 나온다. 구현 비용을 한 번만 낸다.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 9: 함정 봇 스위트

관문보다 함정 봇을 먼저 만든다. **관문이 무엇을 잡아야 하는지가 곧 관문의 명세**이기 때문이다. 이 스위트가 "관문이 진짜 작동하는지 어떻게 압니까?"에 대한 답이며 C1의 가장 강력한 증거다.

**Files:**
- Create: `arena-gate/build.gradle`
- Create: `arena-gate/src/test/java/arena/gate/traps/` 아래 함정 봇 8종
- Test: `arena-gate/src/test/java/arena/gate/traps/TrapSanityTest.java`

**Interfaces:**
- Consumes: `Bot`, `GameView`, `Direction` (Task 4)
- Produces: `arena.gate.traps` 패키지의 `Bot` 구현체 8종 — `StatefulTrap`, `ClockTrap`, `UnseededRandomTrap`, `CrashTrap`, `NondeterministicTrap`, `SlowTrap`, `WeakTrap`, `CleanBot`. 모두 무인자 생성자를 갖는다. `CleanBot`은 모든 관문을 통과해야 하는 대조군이다.

- [ ] **Step 1: 빌드 파일 작성**

`arena-gate/build.gradle`:

```groovy
dependencies {
    implementation project(':arena-core')
    implementation project(':arena-bots')
    implementation project(':arena-diagnostics')
    implementation 'org.ow2.asm:asm:9.7'

    testImplementation project(':arena-core')
    testImplementation project(':arena-bots')
}
```

- [ ] **Step 2: 함정 봇 8종 작성**

`arena-gate/src/test/java/arena/gate/traps/StatefulTrap.java`:

```java
package arena.gate.traps;

import arena.bots.Bot;
import arena.core.Direction;
import arena.core.GameView;

/** G2 위반: 인스턴스 필드를 갖는다. */
public final class StatefulTrap implements Bot {

    private int callCount = 0;   // ← G2가 잡아야 하는 것

    @Override
    public String name() { return "StatefulTrap"; }

    @Override
    public Direction move(GameView view) {
        callCount++;
        return Direction.values()[callCount % 4];
    }
}
```

`arena-gate/src/test/java/arena/gate/traps/ClockTrap.java`:

```java
package arena.gate.traps;

import arena.bots.Bot;
import arena.core.Direction;
import arena.core.GameView;

/** G3 위반: 시계를 읽는다. */
public final class ClockTrap implements Bot {

    @Override
    public String name() { return "ClockTrap"; }

    @Override
    public Direction move(GameView view) {
        long t = System.nanoTime();   // ← G3가 잡아야 하는 것
        return Direction.values()[(int) Math.floorMod(t, 4)];
    }
}
```

`arena-gate/src/test/java/arena/gate/traps/UnseededRandomTrap.java`:

```java
package arena.gate.traps;

import arena.bots.Bot;
import arena.core.Direction;
import arena.core.GameView;

/** G3 위반: 시드 없는 난수. 시드 있는 Random은 허용되므로 무인자 생성자만 잡혀야 한다. */
public final class UnseededRandomTrap implements Bot {

    @Override
    public String name() { return "UnseededRandomTrap"; }

    @Override
    public Direction move(GameView view) {
        return Direction.values()[new java.util.Random().nextInt(4)];   // ← G3
    }
}
```

`arena-gate/src/test/java/arena/gate/traps/CrashTrap.java`:

```java
package arena.gate.traps;

import arena.bots.Bot;
import arena.core.Direction;
import arena.core.GameView;

/** G4 위반: 특정 국면에서 배열 범위를 벗어난다. */
public final class CrashTrap implements Bot {

    @Override
    public String name() { return "CrashTrap"; }

    @Override
    public Direction move(GameView view) {
        // 경계 검사를 빠뜨렸다. 머리가 아래 가장자리에 닿는 순간 터진다.
        boolean blocked = view.wall()[view.myHead().y() + 1][view.myHead().x()];
        return blocked ? Direction.LEFT : Direction.RIGHT;
    }
}
```

`arena-gate/src/test/java/arena/gate/traps/NondeterministicTrap.java`:

```java
package arena.gate.traps;

import arena.bots.Bot;
import arena.core.Direction;
import arena.core.GameView;

/**
 * G5 위반: 객체 아이덴티티 해시에 의존한다.
 * 금지 API를 쓰지 않으므로 G3는 통과한다. 결정론 검사만이 이걸 잡는다.
 */
public final class NondeterministicTrap implements Bot {

    @Override
    public String name() { return "NondeterministicTrap"; }

    @Override
    public Direction move(GameView view) {
        int h = new Object().hashCode();   // ← G5가 잡아야 하는 것
        return Direction.values()[Math.floorMod(h, 4)];
    }
}
```

`arena-gate/src/test/java/arena/gate/traps/SlowTrap.java`:

```java
package arena.gate.traps;

import arena.bots.Bot;
import arena.core.Direction;
import arena.core.GameView;

/** G6 위반: 한 수에 수십 밀리초를 쓴다. 결과는 결정론적이라 G5는 통과한다. */
public final class SlowTrap implements Bot {

    @Override
    public String name() { return "SlowTrap"; }

    @Override
    public Direction move(GameView view) {
        long acc = 0;
        for (int i = 0; i < 40_000_000; i++) {
            acc += i % 7;
        }
        // acc를 결과에 반영해 JIT가 루프를 통째로 지워버리지 못하게 한다.
        for (Direction d : Direction.values()) {
            if (!view.isDeadly(d)) return d;
        }
        return Direction.values()[(int) Math.floorMod(acc, 4)];
    }
}
```

`arena-gate/src/test/java/arena/gate/traps/WeakTrap.java`:

```java
package arena.gate.traps;

import arena.bots.Bot;
import arena.core.Direction;
import arena.core.GameView;

/** G7 위반: 위생 관문은 모두 통과하지만 벽회피봇에게 진다. */
public final class WeakTrap implements Bot {

    @Override
    public String name() { return "WeakTrap"; }

    @Override
    public Direction move(GameView view) {
        return view.myDir();   // 직진만 한다
    }
}
```

`arena-gate/src/test/java/arena/gate/traps/CleanBot.java`:

```java
package arena.gate.traps;

import arena.bots.Bot;
import arena.core.Direction;
import arena.core.GameView;
import arena.core.Point;

/**
 * 대조군. 모든 관문을 통과해야 한다.
 *
 * 관문이 통과시켜야 할 것까지 반려하면 루프가 영원히 막히므로,
 * 함정 봇만큼이나 이 봇이 중요하다.
 */
public final class CleanBot implements Bot {

    private static final Direction[] PRIORITY = {
            Direction.UP, Direction.RIGHT, Direction.DOWN, Direction.LEFT
    };

    @Override
    public String name() { return "CleanBot"; }

    @Override
    public Direction move(GameView view) {
        Direction best = view.myDir();
        int bestSpace = -1;

        for (Direction d : PRIORITY) {
            if (view.isDeadly(d)) continue;
            int space = openNeighbours(view, d);
            if (space > bestSpace) {
                bestSpace = space;
                best = d;
            }
        }
        return best;
    }

    /** 그 칸에 갔을 때 인접한 빈 칸 수. 아주 얕은 공간 감각. */
    private int openNeighbours(GameView view, Direction d) {
        Point p = view.myHead().move(d);
        int open = 0;
        for (Direction n : Direction.values()) {
            Point q = p.move(n);
            if (view.inBounds(q.x(), q.y()) && !view.isWall(q.x(), q.y())) open++;
        }
        return open;
    }
}
```

- [ ] **Step 3: 함정이 정말 함정인지 확인하는 테스트**

`arena-gate/src/test/java/arena/gate/traps/TrapSanityTest.java`:

```java
package arena.gate.traps;

import arena.bots.Bot;
import arena.core.Direction;
import arena.core.GameView;
import arena.core.Point;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 함정 봇이 정말 함정인지 확인한다.
 * 함정이 함정이 아니면 관문 테스트가 통과해도 아무것도 증명하지 못한다.
 */
class TrapSanityTest {

    private GameView view(int x, int y) {
        return new GameView(30, 30, new boolean[30][30],
                new Point(x, y), Direction.RIGHT,
                new Point(1, 1), Direction.LEFT, 1);
    }

    @Test
    void StatefulTrap은_정말_인스턴스_필드를_갖는다() {
        boolean hasInstanceField = false;
        for (var f : StatefulTrap.class.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers())) hasInstanceField = true;
        }
        assertTrue(hasInstanceField);
    }

    @Test
    void CrashTrap은_아래_가장자리에서_정말_터진다() {
        assertThrows(ArrayIndexOutOfBoundsException.class,
                () -> new CrashTrap().move(view(15, 29)));
    }

    @Test
    void NondeterministicTrap은_같은_국면에_다른_답을_낼_수_있다() {
        Bot bot = new NondeterministicTrap();
        GameView v = view(15, 15);

        Direction first = bot.move(v);
        boolean differed = false;
        for (int i = 0; i < 100_000 && !differed; i++) {
            if (bot.move(v) != first) differed = true;
        }
        assertTrue(differed, "비결정론 함정이 결정론적으로 동작한다");
    }

    @Test
    void CleanBot은_모든_위치에서_유효한_방향을_낸다() {
        Bot bot = new CleanBot();
        for (int x = 1; x < 29; x++) {
            for (int y = 1; y < 29; y++) {
                assertNotNull(bot.move(view(x, y)));
            }
        }
    }

    @Test
    void CleanBot은_인스턴스_필드가_없다() {
        for (var f : CleanBot.class.getDeclaredFields()) {
            assertTrue(Modifier.isStatic(f.getModifiers()),
                    "CleanBot에 인스턴스 필드가 있다: " + f.getName());
        }
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :arena-gate:test`
Expected: 5 tests PASS

- [ ] **Step 5: 커밋**

```bash
git add arena-gate/
git commit -m "$(cat <<'EOF'
test: 관문 검증용 함정 봇 스위트

관문보다 함정 봇을 먼저 만든다. 관문이 무엇을 잡아야 하는지가
곧 관문의 명세다.

CleanBot은 대조군이다. 관문이 통과시켜야 할 것까지 반려하면
루프가 영원히 막히므로 함정만큼이나 중요하다.

앞으로 관문을 추가할 때는 함정 봇도 함께 추가한다.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 10: 관문 프레임워크와 G2 (무상태)

**Files:**
- Create: `arena-gate/src/main/java/arena/gate/Gate.java`
- Create: `arena-gate/src/main/java/arena/gate/GateResult.java`
- Create: `arena-gate/src/main/java/arena/gate/GateContext.java`
- Create: `arena-gate/src/main/java/arena/gate/StatelessGate.java`
- Test: `arena-gate/src/test/java/arena/gate/GateContextFixture.java`
- Test: `arena-gate/src/test/java/arena/gate/StatelessGateTest.java`

**Interfaces:**
- Consumes: `Bot` (Task 4), 함정 봇 (Task 9)
- Produces:
  - `GateResult` record (`String gateId`, `boolean passed`, `String detail`) + `static GateResult pass(String)` + `static GateResult fail(String, String)`
  - `GateContext` record (`Bot bot`, `Class<?> botClass`, `int width`, `int height`, `List<Long> judgingSeeds`)
  - `Gate` 인터페이스 (`String id()`, `GateResult check(GateContext ctx)`)
  - `StatelessGate` (id = `"G2"`)

- [ ] **Step 1: 실패하는 테스트 작성**

`arena-gate/src/test/java/arena/gate/GateContextFixture.java`:

```java
package arena.gate;

import arena.bots.Bot;
import java.util.stream.LongStream;

/** 테스트에서 GateContext를 짧게 만들기 위한 헬퍼. */
final class GateContextFixture {

    private GateContextFixture() {}

    static GateContext of(Bot bot) {
        return new GateContext(bot, bot.getClass(), 30, 30,
                LongStream.rangeClosed(1, 50).boxed().toList());
    }
}
```

`arena-gate/src/test/java/arena/gate/StatelessGateTest.java`:

```java
package arena.gate;

import arena.bots.baseline.StraightBot;
import arena.bots.baseline.WallAvoidBot;
import arena.gate.traps.CleanBot;
import arena.gate.traps.StatefulTrap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StatelessGateTest {

    private final Gate gate = new StatelessGate();

    @Test
    void 아이디는_G2다() {
        assertEquals("G2", gate.id());
    }

    @Test
    void 인스턴스_필드를_가진_봇을_반려한다() {
        GateResult r = gate.check(GateContextFixture.of(new StatefulTrap()));

        assertFalse(r.passed());
        assertTrue(r.detail().contains("callCount"),
                "위반 필드 이름을 알려줘야 한다: " + r.detail());
    }

    @Test
    void 무상태_봇을_통과시킨다() {
        assertTrue(gate.check(GateContextFixture.of(new CleanBot())).passed());
        assertTrue(gate.check(GateContextFixture.of(new StraightBot())).passed());
    }

    @Test
    void static_final_상수는_허용한다() {
        // WallAvoidBot은 private static final Direction[] PRIORITY를 갖는다.
        assertTrue(gate.check(GateContextFixture.of(new WallAvoidBot())).passed(),
                "static final 상수까지 반려하면 정상적인 봇을 못 만든다");
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :arena-gate:test --tests '*StatelessGateTest*'`
Expected: 컴파일 실패 — `Gate`, `GateResult`, `GateContext`, `StatelessGate`가 없음

- [ ] **Step 3: 최소 구현**

`arena-gate/src/main/java/arena/gate/GateResult.java`:

```java
package arena.gate;

public record GateResult(String gateId, boolean passed, String detail) {

    public static GateResult pass(String gateId) {
        return new GateResult(gateId, true, "");
    }

    public static GateResult fail(String gateId, String detail) {
        return new GateResult(gateId, false, detail);
    }
}
```

`arena-gate/src/main/java/arena/gate/GateContext.java`:

```java
package arena.gate;

import arena.bots.Bot;
import java.util.List;

public record GateContext(
        Bot bot,
        Class<?> botClass,
        int width,
        int height,
        List<Long> judgingSeeds
) {}
```

`arena-gate/src/main/java/arena/gate/Gate.java`:

```java
package arena.gate;

/**
 * 관문 하나. 반드시 코드가 O/X를 내야 한다.
 * 사람의 눈이 필요한 기준은 관문이 될 수 없다.
 */
public interface Gate {

    String id();

    GateResult check(GateContext ctx);
}
```

`arena-gate/src/main/java/arena/gate/StatelessGate.java`:

```java
package arena.gate;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * G2 — 봇은 인스턴스 필드를 가질 수 없다.
 *
 * 이 하나가 "같은 입력 → 같은 출력"을 인터페이스 수준에서 강제한다.
 * R1이 규율이 아니라 구조가 되는 지점이다.
 *
 * static final 상수는 허용한다. 방향 우선순위 배열 같은 것까지
 * 막으면 정상적인 봇을 만들 수 없다. 전역 가변 상태는 G3가 잡는다.
 */
public final class StatelessGate implements Gate {

    @Override
    public String id() { return "G2"; }

    @Override
    public GateResult check(GateContext ctx) {
        List<String> violations = new ArrayList<>();

        for (Field f : ctx.botClass().getDeclaredFields()) {
            if (f.isSynthetic()) continue;               // 컴파일러가 만든 필드
            if (Modifier.isStatic(f.getModifiers())) continue;

            violations.add(f.getType().getSimpleName() + " " + f.getName());
        }

        if (violations.isEmpty()) {
            return GateResult.pass(id());
        }
        return GateResult.fail(id(),
                "인스턴스 필드가 " + violations.size() + "개 있다: "
                        + String.join(", ", violations)
                        + " — 봇은 무상태 순수 함수여야 한다");
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :arena-gate:test --tests '*StatelessGateTest*'`
Expected: 4 tests PASS

- [ ] **Step 5: 커밋**

```bash
git add arena-gate/
git commit -m "$(cat <<'EOF'
feat: 관문 프레임워크와 G2 무상태 검사

G2는 리플렉션으로 인스턴스 필드를 센다. static final 상수는
허용한다 — 방향 우선순위 배열까지 막으면 정상적인 봇을 만들 수
없다. 전역 가변 상태는 G3가 따로 잡는다.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 11: G3 — 금지 API 바이트코드 검사

**핵심 판별:** `new Random()`은 금지하고 `new Random(seed)`는 허용해야 한다. 바이트코드에서는 생성자 디스크립터로 구분된다 — `()V`는 시드 없음, `(J)V`는 시드 있음.

**Files:**
- Create: `arena-gate/src/main/java/arena/gate/ForbiddenApiGate.java`
- Test: `arena-gate/src/test/java/arena/gate/ForbiddenApiGateTest.java`

**Interfaces:**
- Consumes: `Gate`, `GateContext`, `GateResult` (Task 10)
- Produces: `ForbiddenApiGate` (id = `"G3"`), 무인자 생성자

- [ ] **Step 1: 실패하는 테스트 작성**

`arena-gate/src/test/java/arena/gate/ForbiddenApiGateTest.java`:

```java
package arena.gate;

import arena.bots.Bot;
import arena.bots.baseline.RandomBot;
import arena.core.Direction;
import arena.core.GameView;
import arena.gate.traps.CleanBot;
import arena.gate.traps.ClockTrap;
import arena.gate.traps.UnseededRandomTrap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ForbiddenApiGateTest {

    private final Gate gate = new ForbiddenApiGate();

    @Test
    void 아이디는_G3다() {
        assertEquals("G3", gate.id());
    }

    @Test
    void 시계를_읽는_봇을_반려한다() {
        GateResult r = gate.check(GateContextFixture.of(new ClockTrap()));
        assertFalse(r.passed());
        assertTrue(r.detail().contains("nanoTime"), r.detail());
    }

    @Test
    void 시드_없는_난수를_반려한다() {
        GateResult r = gate.check(GateContextFixture.of(new UnseededRandomTrap()));
        assertFalse(r.passed());
        assertTrue(r.detail().contains("Random"), r.detail());
    }

    @Test
    void 시드_있는_난수는_허용한다() {
        assertTrue(gate.check(GateContextFixture.of(new SeededRandomBot())).passed(),
                "시드 있는 Random까지 막으면 안 된다");
    }

    @Test
    void 파일_접근을_반려한다() {
        assertFalse(gate.check(GateContextFixture.of(new FileReadingBot())).passed());
    }

    @Test
    void 가변_static_필드를_반려한다() {
        GateResult r = gate.check(GateContextFixture.of(new MutableStaticBot()));
        assertFalse(r.passed());
        assertTrue(r.detail().contains("counter"), r.detail());
    }

    @Test
    void 깨끗한_봇과_베이스라인을_통과시킨다() {
        assertTrue(gate.check(GateContextFixture.of(new CleanBot())).passed());
        assertTrue(gate.check(GateContextFixture.of(new RandomBot())).passed());
    }

    // --- 이 테스트에서만 쓰는 봇들 ---

    static final class SeededRandomBot implements Bot {
        public String name() { return "SeededRandomBot"; }
        public Direction move(GameView view) {
            var rng = new java.util.Random(view.turn());   // 시드 있음 = 허용
            return Direction.values()[rng.nextInt(4)];
        }
    }

    static final class FileReadingBot implements Bot {
        public String name() { return "FileReadingBot"; }
        public Direction move(GameView view) {
            java.io.File f = new java.io.File("/tmp/hint");
            return f.exists() ? Direction.UP : Direction.DOWN;
        }
    }

    static final class MutableStaticBot implements Bot {
        static int counter = 0;   // non-final static = 전역 가변 상태
        public String name() { return "MutableStaticBot"; }
        public Direction move(GameView view) {
            counter++;
            return Direction.values()[counter % 4];
        }
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :arena-gate:test --tests '*ForbiddenApiGateTest*'`
Expected: 컴파일 실패 — `ForbiddenApiGate`가 없음

- [ ] **Step 3: 최소 구현**

`arena-gate/src/main/java/arena/gate/ForbiddenApiGate.java`:

```java
package arena.gate;

import org.objectweb.asm.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * G3 — R1을 깨뜨릴 수 있는 API를 바이트코드에서 찾아낸다.
 *
 * 시드 있는 Random은 허용한다. 바이트코드에서는 생성자 디스크립터로
 * 구분된다 — ()V는 시드 없음, (J)V는 시드 있음.
 */
public final class ForbiddenApiGate implements Gate {

    /** 이 접두사로 시작하는 클래스는 통째로 금지. */
    private static final String[] FORBIDDEN_PREFIXES = {
            "java/io/",
            "java/nio/file/",
            "java/net/",
            "java/util/concurrent/",
            "java/time/",
            "java/lang/reflect/",
            "sun/misc/Unsafe",
            "java/lang/Thread",
            "java/lang/ProcessBuilder",
    };

    /** owner.name 정확 일치 금지. */
    private static final String[] FORBIDDEN_METHODS = {
            "java/lang/Math.random",
            "java/lang/System.currentTimeMillis",
            "java/lang/System.nanoTime",
            "java/lang/System.identityHashCode",
            "java/lang/System.getenv",
            "java/lang/System.getProperty",
            "java/lang/Object.hashCode",
    };

    @Override
    public String id() { return "G3"; }

    @Override
    public GateResult check(GateContext ctx) {
        List<String> violations = new ArrayList<>();
        byte[] bytecode = readClassFile(ctx.botClass());

        new ClassReader(bytecode).accept(
                new Scanner(ctx.botClass().getSimpleName(), violations),
                ClassReader.SKIP_FRAMES);

        if (violations.isEmpty()) {
            return GateResult.pass(id());
        }
        return GateResult.fail(id(),
                "금지 API를 " + violations.size() + "곳에서 사용한다:\n  "
                        + String.join("\n  ", violations));
    }

    private static byte[] readClassFile(Class<?> clazz) {
        String resource = clazz.getName().replace('.', '/') + ".class";
        try (InputStream in = clazz.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("클래스 파일을 찾을 수 없다: " + resource);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("클래스 파일을 읽지 못했다: " + resource, e);
        }
    }

    private static final class Scanner extends ClassVisitor {

        private final String botName;
        private final List<String> violations;

        Scanner(String botName, List<String> violations) {
            super(Opcodes.ASM9);
            this.botName = botName;
            this.violations = violations;
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor,
                                       String signature, Object value) {
            boolean isStatic = (access & Opcodes.ACC_STATIC) != 0;
            boolean isFinal = (access & Opcodes.ACC_FINAL) != 0;

            if (isStatic && !isFinal) {
                violations.add("가변 static 필드: " + name
                        + " — 전역 가변 상태는 경기 간 오염을 만든다");
            }
            return super.visitField(access, name, descriptor, signature, value);
        }

        @Override
        public MethodVisitor visitMethod(int access, String methodName, String descriptor,
                                         String signature, String[] exceptions) {
            return new MethodVisitor(Opcodes.ASM9) {

                @Override
                public void visitMethodInsn(int opcode, String owner, String name,
                                            String desc, boolean isInterface) {
                    // 시드 없는 Random 생성자만 콕 집어 금지한다.
                    if (owner.equals("java/util/Random")
                            && name.equals("<init>")
                            && desc.equals("()V")) {
                        violations.add(botName + "." + methodName
                                + " → new Random() (시드 없음). 시드를 주면 허용된다");
                        return;
                    }

                    String qualified = owner + "." + name;
                    for (String forbidden : FORBIDDEN_METHODS) {
                        if (qualified.equals(forbidden)) {
                            violations.add(botName + "." + methodName + " → " + qualified);
                            return;
                        }
                    }
                    checkOwner(owner, methodName);
                }

                @Override
                public void visitTypeInsn(int opcode, String type) {
                    if (opcode == Opcodes.NEW) {
                        checkOwner(type, methodName);
                    }
                }

                @Override
                public void visitFieldInsn(int opcode, String owner, String name, String desc) {
                    checkOwner(owner, methodName);
                }

                private void checkOwner(String owner, String where) {
                    for (String prefix : FORBIDDEN_PREFIXES) {
                        if (owner.startsWith(prefix)) {
                            violations.add(botName + "." + where + " → " + owner);
                            return;
                        }
                    }
                }
            };
        }
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :arena-gate:test --tests '*ForbiddenApiGateTest*'`
Expected: 7 tests PASS

`시드_있는_난수는_허용한다`가 실패하면 디스크립터 판별이 잘못된 것이다. `new Random(long)`의 생성자 디스크립터는 `(J)V`이지 `()V`가 아니다.

- [ ] **Step 5: 커밋**

```bash
git add arena-gate/
git commit -m "$(cat <<'EOF'
feat: G3 금지 API 바이트코드 검사

ASM으로 봇의 클래스 파일을 스캔해 시계, 시드 없는 난수, 파일과
네트워크, 스레드, 리플렉션, 가변 static 필드를 찾는다.

시드 있는 Random은 허용한다. 바이트코드에서는 생성자 디스크립터로
구분된다 — ()V는 시드 없음, (J)V는 시드 있음.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 12: 국면 수집기와 G4 (합법 수)

국면은 무작위로 만들지 않고 **실제 대전에서 수집한다.** 도달 불가능한 격자 상태로 봇을 시험하면 실제로는 일어나지 않을 실패를 잡아 루프를 헛돌게 한다.

**Files:**
- Create: `arena-gate/src/main/java/arena/gate/PositionSampler.java`
- Create: `arena-gate/src/main/java/arena/gate/LegalMoveGate.java`
- Test: `arena-gate/src/test/java/arena/gate/PositionSamplerTest.java`
- Test: `arena-gate/src/test/java/arena/gate/LegalMoveGateTest.java`

**Interfaces:**
- Consumes: `Match`, `Replay`, `GameView`, `Grid` (Task 2, 5, 7), `Gate` (Task 10)
- Produces:
  - `PositionSampler.sample(int count, int width, int height)` → `List<GameView>`
  - `LegalMoveGate(List<GameView> positions)` (id = `"G4"`)

- [ ] **Step 1: 실패하는 테스트 작성**

`arena-gate/src/test/java/arena/gate/PositionSamplerTest.java`:

```java
package arena.gate;

import arena.core.GameView;
import org.junit.jupiter.api.Test;

import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PositionSamplerTest {

    @Test
    void 요청한_개수만큼_수집한다() {
        assertEquals(1_000, PositionSampler.sample(1_000, 30, 30).size());
    }

    @Test
    void 같은_인자로_부르면_같은_국면이_나온다() {
        List<GameView> a = PositionSampler.sample(200, 30, 30);
        List<GameView> b = PositionSampler.sample(200, 30, 30);

        for (int i = 0; i < 200; i++) {
            assertEquals(a.get(i).myHead(), b.get(i).myHead(), "국면 " + i + "이 재현되지 않았다");
            assertEquals(a.get(i).turn(), b.get(i).turn());
        }
    }

    @Test
    void 수집된_국면은_모두_도달_가능한_상태다() {
        for (GameView v : PositionSampler.sample(500, 30, 30)) {
            assertTrue(v.inBounds(v.myHead().x(), v.myHead().y()), "머리가 격자 밖이다");
            assertTrue(v.isWall(v.myHead().x(), v.myHead().y()), "머리 칸이 벽이 아니다");
            assertTrue(v.isWall(v.oppHead().x(), v.oppHead().y()), "상대 머리 칸이 벽이 아니다");
            assertNotEquals(v.myHead(), v.oppHead(), "두 머리가 같은 칸에 있다");
        }
    }

    @Test
    void 국면은_경기_초반에만_몰려있지_않다() {
        long lateGame = PositionSampler.sample(1_000, 30, 30).stream()
                .filter(v -> v.turn() > 20)
                .count();
        assertTrue(lateGame > 100, "중후반 국면이 " + lateGame + "개뿐이다");
    }
}
```

`arena-gate/src/test/java/arena/gate/LegalMoveGateTest.java`:

```java
package arena.gate;

import arena.bots.baseline.WallAvoidBot;
import arena.core.Direction;
import arena.core.GameView;
import arena.gate.traps.CleanBot;
import arena.gate.traps.CrashTrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class LegalMoveGateTest {

    private static List<GameView> positions;

    @BeforeAll
    static void samplePositions() {
        positions = PositionSampler.sample(2_000, 30, 30);
    }

    @Test
    void 아이디는_G4다() {
        assertEquals("G4", new LegalMoveGate(positions).id());
    }

    @Test
    void 예외를_던지는_봇을_반려하고_반례를_알려준다() {
        GateResult r = new LegalMoveGate(positions).check(GateContextFixture.of(new CrashTrap()));

        assertFalse(r.passed());
        assertTrue(r.detail().contains("ArrayIndexOutOfBounds"), r.detail());
        assertTrue(r.detail().contains("myHead"), "반례 국면을 알려줘야 한다: " + r.detail());
    }

    @Test
    void 정상_봇을_통과시킨다() {
        assertTrue(new LegalMoveGate(positions)
                .check(GateContextFixture.of(new CleanBot())).passed());
        assertTrue(new LegalMoveGate(positions)
                .check(GateContextFixture.of(new WallAvoidBot())).passed());
    }

    @Test
    void null을_반환하는_봇을_반려한다() {
        GateResult r = new LegalMoveGate(positions).check(GateContextFixture.of(new NullBot()));
        assertFalse(r.passed());
        assertTrue(r.detail().contains("null"), r.detail());
    }

    static final class NullBot implements arena.bots.Bot {
        public String name() { return "NullBot"; }
        public Direction move(GameView view) { return null; }
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :arena-gate:test --tests '*PositionSamplerTest*' --tests '*LegalMoveGateTest*'`
Expected: 컴파일 실패 — `PositionSampler`, `LegalMoveGate`가 없음

- [ ] **Step 3: 최소 구현**

`arena-gate/src/main/java/arena/gate/PositionSampler.java`:

```java
package arena.gate;

import arena.bots.baseline.RandomBot;
import arena.bots.baseline.StraightBot;
import arena.bots.baseline.WallAvoidBot;
import arena.core.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 실제 대전을 재생하며 국면을 모은다.
 *
 * 무작위로 격자를 채워 만들지 않는 이유가 있다. 도달 불가능한 상태로
 * 봇을 시험하면 실제로는 일어나지 않을 실패를 잡아 루프를 헛돌게 한다.
 */
public final class PositionSampler {

    private PositionSampler() {}

    public static List<GameView> sample(int count, int width, int height) {
        List<GameView> collected = new ArrayList<>(count);

        BotFunction[] pool = {
                v -> new WallAvoidBot().move(v),
                v -> new RandomBot().move(v),
                v -> new StraightBot().move(v),
        };

        outer:
        for (long seed = 1; seed <= 10_000; seed++) {
            for (BotFunction a : pool) {
                for (BotFunction b : pool) {
                    Replay replay = Match.play("a", a, "b", b, seed, width, height);
                    for (GameView v : viewsOf(replay)) {
                        collected.add(v);
                        if (collected.size() >= count) break outer;
                    }
                }
            }
        }

        if (collected.size() < count) {
            throw new IllegalStateException(
                    "시드 10,000개를 다 써도 국면 " + count + "개를 못 모았다 (모은 것 "
                            + collected.size() + "개)");
        }
        return collected;
    }

    /** 리플레이를 재생하며 매 턴 두 봇의 시야를 만든다. */
    private static List<GameView> viewsOf(Replay replay) {
        List<GameView> views = new ArrayList<>();

        Grid grid = new Grid(replay.width(), replay.height());
        Point[] head = { replay.start0(), replay.start1() };
        Direction[] dir = { replay.dir0(), replay.dir1() };
        grid.claim(head[0], 0);
        grid.claim(head[1], 1);

        for (int turn = 1; turn <= replay.result().turns(); turn++) {
            boolean[][] snapshot = grid.wallSnapshot();
            for (int me = 0; me < 2; me++) {
                int opp = 1 - me;
                views.add(new GameView(
                        replay.width(), replay.height(), snapshot,
                        head[me], dir[me], head[opp], dir[opp], turn));
            }

            Direction d0 = replay.moveAt(turn, 0);
            Direction d1 = replay.moveAt(turn, 1);
            Point p0 = head[0].move(d0);
            Point p1 = head[1].move(d1);

            boolean dead0 = !grid.inBounds(p0) || grid.isWall(p0) || p0.equals(p1);
            boolean dead1 = !grid.inBounds(p1) || grid.isWall(p1) || p1.equals(p0);
            if (dead0 || dead1) break;

            grid.claim(p0, 0);
            grid.claim(p1, 1);
            head[0] = p0; head[1] = p1;
            dir[0] = d0;  dir[1] = d1;
        }
        return views;
    }
}
```

`arena-gate/src/main/java/arena/gate/LegalMoveGate.java`:

```java
package arena.gate;

import arena.core.Direction;
import arena.core.GameView;

import java.util.List;

/**
 * G4 — 어떤 국면에서도 유효한 방향을 예외 없이 반환해야 한다.
 *
 * "죽지 않는 수"를 요구하는 게 아니다. 자멸은 봇의 자유이며 지표로만
 * 남긴다. 여기서 보는 것은 오직 계약 준수다.
 */
public final class LegalMoveGate implements Gate {

    private final List<GameView> positions;

    public LegalMoveGate(List<GameView> positions) {
        this.positions = positions;
    }

    @Override
    public String id() { return "G4"; }

    @Override
    public GateResult check(GateContext ctx) {
        for (int i = 0; i < positions.size(); i++) {
            GameView view = positions.get(i);
            try {
                Direction d = ctx.bot().move(view);
                if (d == null) {
                    return GateResult.fail(id(),
                            "국면 " + i + "에서 null을 반환했다\n" + describe(view));
                }
            } catch (RuntimeException | StackOverflowError e) {
                return GateResult.fail(id(),
                        "국면 " + i + "에서 " + e.getClass().getSimpleName()
                                + (e.getMessage() == null ? "" : ": " + e.getMessage())
                                + "\n" + describe(view));
            }
        }
        return GateResult.pass(id());
    }

    /** 에이전트가 그대로 재현할 수 있도록 반례 국면을 적는다. */
    private static String describe(GameView v) {
        return "  turn=" + v.turn()
                + " myHead=" + v.myHead() + " myDir=" + v.myDir()
                + " oppHead=" + v.oppHead() + " oppDir=" + v.oppDir();
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :arena-gate:test --tests '*PositionSamplerTest*' --tests '*LegalMoveGateTest*'`
Expected: 8 tests PASS

- [ ] **Step 5: 커밋**

```bash
git add arena-gate/
git commit -m "$(cat <<'EOF'
feat: 국면 수집기와 G4 합법 수 검사

국면은 무작위로 만들지 않고 실제 대전을 재생하며 수집한다. 도달
불가능한 격자 상태로 봇을 시험하면 실제로는 일어나지 않을 실패를
잡아 루프를 헛돌게 한다.

G4는 계약 준수만 본다. "죽지 않는 수"를 요구하지 않는다 — 자멸은
봇의 자유이며 지표로만 남긴다.

반려 시 재현 가능한 반례 국면을 함께 낸다.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 13: G5 (결정론)과 G6 (시간 예산)

**Files:**
- Create: `arena-gate/src/main/java/arena/gate/DeterminismGate.java`
- Create: `arena-gate/src/main/java/arena/gate/TimeBudgetGate.java`
- Test: `arena-gate/src/test/java/arena/gate/DeterminismGateTest.java`
- Test: `arena-gate/src/test/java/arena/gate/TimeBudgetGateTest.java`

**Interfaces:**
- Consumes: `Gate`, `GateContext` (Task 10), `PositionSampler` (Task 12), `Match`, `Replay` (Task 5, 7)
- Produces:
  - `DeterminismGate(List<GameView> positions)` (id = `"G5"`)
  - `TimeBudgetGate(List<GameView> positions, double p99LimitMillis)` (id = `"G6"`)

- [ ] **Step 1: 실패하는 테스트 작성**

`arena-gate/src/test/java/arena/gate/DeterminismGateTest.java`:

```java
package arena.gate;

import arena.bots.baseline.RandomBot;
import arena.core.GameView;
import arena.gate.traps.CleanBot;
import arena.gate.traps.NondeterministicTrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DeterminismGateTest {

    private static List<GameView> positions;

    @BeforeAll
    static void samplePositions() {
        positions = PositionSampler.sample(2_000, 30, 30);
    }

    @Test
    void 아이디는_G5다() {
        assertEquals("G5", new DeterminismGate(positions).id());
    }

    @Test
    void 같은_국면에_다른_답을_내는_봇을_반려한다() {
        GateResult r = new DeterminismGate(positions)
                .check(GateContextFixture.of(new NondeterministicTrap()));

        assertFalse(r.passed());
        assertTrue(r.detail().contains("국면"), r.detail());
    }

    @Test
    void 결정론적_봇을_통과시킨다() {
        assertTrue(new DeterminismGate(positions)
                .check(GateContextFixture.of(new CleanBot())).passed());
        assertTrue(new DeterminismGate(positions)
                .check(GateContextFixture.of(new RandomBot())).passed());
    }
}
```

`arena-gate/src/test/java/arena/gate/TimeBudgetGateTest.java`:

```java
package arena.gate;

import arena.core.GameView;
import arena.gate.traps.CleanBot;
import arena.gate.traps.SlowTrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TimeBudgetGateTest {

    private static List<GameView> positions;

    @BeforeAll
    static void samplePositions() {
        // G6는 시간을 재므로 국면 수를 줄여 테스트를 빠르게 유지한다.
        positions = PositionSampler.sample(500, 30, 30);
    }

    @Test
    void 아이디는_G6다() {
        assertEquals("G6", new TimeBudgetGate(positions, 5.0).id());
    }

    @Test
    void 느린_봇을_반려하고_실측값을_알려준다() {
        // SlowTrap은 한 수에 수십 ms를 쓰므로 국면 20개로도 충분히 판별된다.
        GateResult r = new TimeBudgetGate(positions.subList(0, 20), 5.0)
                .check(GateContextFixture.of(new SlowTrap()));

        assertFalse(r.passed());
        assertTrue(r.detail().contains("p99"), r.detail());
    }

    @Test
    void 빠른_봇을_통과시킨다() {
        GateResult r = new TimeBudgetGate(positions, 5.0)
                .check(GateContextFixture.of(new CleanBot()));
        assertTrue(r.passed(), r.detail());
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :arena-gate:test --tests '*DeterminismGateTest*' --tests '*TimeBudgetGateTest*'`
Expected: 컴파일 실패 — `DeterminismGate`, `TimeBudgetGate`가 없음

- [ ] **Step 3: 최소 구현**

`arena-gate/src/main/java/arena/gate/DeterminismGate.java`:

```java
package arena.gate;

import arena.bots.baseline.RandomBot;
import arena.bots.baseline.StraightBot;
import arena.bots.baseline.WallAvoidBot;
import arena.core.*;

import java.util.List;

/**
 * G5 — 결정론.
 *
 * 두 층으로 본다.
 *   ① 같은 국면을 두 번 물으면 같은 답이 나오는가
 *   ② 베이스라인 상대 경기를 두 번 돌리면 리플레이 해시가 같은가
 *
 * ①은 봇 내부의 비결정론을, ②는 경기 전체에 걸쳐 누적되는
 * 비결정론을 잡는다.
 */
public final class DeterminismGate implements Gate {

    private final List<GameView> positions;

    public DeterminismGate(List<GameView> positions) {
        this.positions = positions;
    }

    @Override
    public String id() { return "G5"; }

    @Override
    public GateResult check(GateContext ctx) {
        GateResult perCall = checkPerCall(ctx);
        if (!perCall.passed()) return perCall;

        return checkReplayHash(ctx);
    }

    private GateResult checkPerCall(GateContext ctx) {
        for (int i = 0; i < positions.size(); i++) {
            GameView view = positions.get(i);
            Direction first = ctx.bot().move(view);

            for (int repeat = 0; repeat < 3; repeat++) {
                Direction again = ctx.bot().move(view);
                if (again != first) {
                    return GateResult.fail(id(),
                            "국면 " + i + "에서 같은 입력에 다른 답을 냈다: "
                                    + first + " → " + again
                                    + "\n  turn=" + view.turn() + " myHead=" + view.myHead());
                }
            }
        }
        return GateResult.pass(id());
    }

    private GateResult checkReplayHash(GateContext ctx) {
        BotFunction subject = v -> ctx.bot().move(v);
        String[] names = { "StraightBot", "RandomBot", "WallAvoidBot" };
        BotFunction[] opponents = {
                v -> new StraightBot().move(v),
                v -> new RandomBot().move(v),
                v -> new WallAvoidBot().move(v),
        };

        for (long seed : ctx.judgingSeeds()) {
            for (int i = 0; i < opponents.length; i++) {
                Replay first = Match.play(ctx.bot().name(), subject, names[i], opponents[i],
                        seed, ctx.width(), ctx.height());
                Replay second = Match.play(ctx.bot().name(), subject, names[i], opponents[i],
                        seed, ctx.width(), ctx.height());

                if (!first.hash().equals(second.hash())) {
                    return GateResult.fail(id(),
                            "vs " + names[i] + " 시드 " + seed + " 경기를 두 번 돌렸더니"
                                    + " 리플레이 해시가 달랐다\n  1회차 " + first.hash()
                                    + "\n  2회차 " + second.hash());
                }
            }
        }
        return GateResult.pass(id());
    }
}
```

`arena-gate/src/main/java/arena/gate/TimeBudgetGate.java`:

```java
package arena.gate;

import arena.core.GameView;

import java.util.Arrays;
import java.util.List;

/**
 * G6 — 시간 예산.
 *
 * 성능을 재는 유일한 지점이다. 대전 중에는 어떤 시간 제한도 걸지
 * 않는다. 시간 기반 판정은 같은 조건에 다른 결과를 내어 R1을
 * 깨뜨리기 때문이다. 여기를 통과한 봇의 대전은 순수하게 결정론이다.
 */
public final class TimeBudgetGate implements Gate {

    private static final int WARMUP = 1_000;

    private final List<GameView> positions;
    private final double p99LimitMillis;

    public TimeBudgetGate(List<GameView> positions, double p99LimitMillis) {
        this.positions = positions;
        this.p99LimitMillis = p99LimitMillis;
    }

    @Override
    public String id() { return "G6"; }

    @Override
    public GateResult check(GateContext ctx) {
        // JIT 워밍업. 빠뜨리면 인터프리터 속도를 재게 된다.
        int warmup = Math.min(WARMUP, positions.size());
        for (int i = 0; i < warmup; i++) {
            ctx.bot().move(positions.get(i));
        }

        long[] nanos = new long[positions.size()];
        for (int i = 0; i < positions.size(); i++) {
            long start = System.nanoTime();
            ctx.bot().move(positions.get(i));
            nanos[i] = System.nanoTime() - start;
        }

        Arrays.sort(nanos);
        double p50 = nanos[nanos.length / 2] / 1_000_000.0;
        double p99 = nanos[Math.min(nanos.length - 1, (int) (nanos.length * 0.99))] / 1_000_000.0;

        if (p99 <= p99LimitMillis) {
            return GateResult.pass(id());
        }
        return GateResult.fail(id(), String.format(
                "너무 느리다 — p50 %.3f ms, p99 %.3f ms (상한 %.1f ms)",
                p50, p99, p99LimitMillis));
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :arena-gate:test --tests '*DeterminismGateTest*' --tests '*TimeBudgetGateTest*'`
Expected: 6 tests PASS

- [ ] **Step 5: 커밋**

```bash
git add arena-gate/
git commit -m "$(cat <<'EOF'
feat: G5 결정론과 G6 시간 예산

G5는 두 층으로 본다. 같은 국면 반복 호출과, 베이스라인 상대
경기의 리플레이 해시 대조다. 전자는 봇 내부의 비결정론을, 후자는
경기 전체에 누적되는 비결정론을 잡는다.

G6는 성능을 재는 유일한 지점이다. 대전 중에는 시간 제한을 걸지
않는다 — 시간 기반 판정은 같은 조건에 다른 결과를 내어 R1을
깨뜨린다. 여기를 통과한 봇의 대전은 순수하게 결정론이다.

측정 전 JIT 워밍업을 돈다. 빠뜨리면 인터프리터 속도를 재게 된다.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 14: 시리즈 실행기 — 좌석 교대와 병렬

G7과 챔피언전이 모두 이걸 쓴다. `arena-core`에 두어 관문이 대전 모듈에 의존하지 않게 한다.

**Files:**
- Create: `arena-core/src/main/java/arena/core/SeriesRunner.java`
- Test: `arena-core/src/test/java/arena/core/SeriesRunnerTest.java`

**Interfaces:**
- Consumes: `Match`, `Replay`, `BotFunction` (Task 5, 7)
- Produces: `SeriesRunner.run(String id0, BotFunction b0, String id1, BotFunction b1, List<Long> seeds, int width, int height, boolean parallel)` → `List<Replay>`. 시드마다 2경기(정방향 + 좌석 교대)를 만들며, 교대 경기는 `swapped == true`이고 `winner`는 **교대된 좌석 기준**이다.

- [ ] **Step 1: 실패하는 테스트 작성**

`arena-core/src/test/java/arena/core/SeriesRunnerTest.java`:

```java
package arena.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.LongStream;
import static org.junit.jupiter.api.Assertions.*;

class SeriesRunnerTest {

    private static BotFunction avoid() {
        return view -> {
            for (Direction d : new Direction[]{
                    Direction.RIGHT, Direction.DOWN, Direction.LEFT, Direction.UP}) {
                if (!view.isDeadly(d)) return d;
            }
            return view.myDir();
        };
    }

    private static BotFunction hugLeft() {
        return view -> {
            for (Direction d : new Direction[]{
                    Direction.UP, Direction.LEFT, Direction.DOWN, Direction.RIGHT}) {
                if (!view.isDeadly(d)) return d;
            }
            return view.myDir();
        };
    }

    private static final List<Long> SEEDS = LongStream.rangeClosed(1, 50).boxed().toList();

    @Test
    void 시드마다_2경기가_생긴다() {
        assertEquals(100, SeriesRunner.run("a", avoid(), "b", hugLeft(),
                SEEDS, 30, 30, false).size());
    }

    @Test
    void 절반은_좌석_교대_경기다() {
        long swapped = SeriesRunner.run("a", avoid(), "b", hugLeft(), SEEDS, 30, 30, false)
                .stream().filter(Replay::swapped).count();
        assertEquals(50, swapped);
    }

    @Test
    void 교대_경기는_같은_시작_위치에_봇만_바꿔_앉힌다() {
        List<Replay> replays = SeriesRunner.run(
                "a", avoid(), "b", hugLeft(), List.of(7L), 30, 30, false);

        Replay normal = replays.stream().filter(r -> !r.swapped()).findFirst().orElseThrow();
        Replay swapped = replays.stream().filter(Replay::swapped).findFirst().orElseThrow();

        assertEquals(normal.start0(), swapped.start0(),
                "시작 위치가 바뀌었다 — 미러링이 아니라 좌석 교대여야 한다");
        assertEquals(normal.start1(), swapped.start1());
        assertEquals("a", normal.bot0Id());
        assertEquals("b", swapped.bot0Id(), "교대 경기에서는 b가 0번 좌석에 앉아야 한다");
    }

    @Test
    void 병렬_실행이_순차_실행과_같은_결과를_낸다() {
        List<Replay> sequential = SeriesRunner.run(
                "a", avoid(), "b", hugLeft(), SEEDS, 30, 30, false);
        List<Replay> parallel = SeriesRunner.run(
                "a", avoid(), "b", hugLeft(), SEEDS, 30, 30, true);

        assertEquals(sequential.size(), parallel.size());
        for (int i = 0; i < sequential.size(); i++) {
            assertEquals(sequential.get(i).hash(), parallel.get(i).hash(),
                    "경기 " + i + "의 결과가 병렬 실행에서 달라졌다");
        }
    }

    @Test
    void 반복_실행이_항상_같은_결과를_낸다() {
        List<Replay> first = SeriesRunner.run("a", avoid(), "b", hugLeft(), SEEDS, 30, 30, true);
        List<Replay> second = SeriesRunner.run("a", avoid(), "b", hugLeft(), SEEDS, 30, 30, true);

        for (int i = 0; i < first.size(); i++) {
            assertEquals(first.get(i).hash(), second.get(i).hash());
        }
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :arena-core:test --tests '*SeriesRunnerTest*'`
Expected: 컴파일 실패 — `SeriesRunner`가 없음

- [ ] **Step 3: 최소 구현**

`arena-core/src/main/java/arena/core/SeriesRunner.java`:

```java
package arena.core;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/**
 * 시드 목록에 대해 좌석을 교대해가며 경기를 돌린다.
 *
 * 좌석 교대는 미러링이 아니다. 같은 두 시작 위치에 봇 배정만 바꾼다.
 * 미러링하면 격자가 비대칭일 때 이점이 상쇄되지 않지만, 좌석 교대는
 * 어떤 격자에서도 정확히 상쇄된다.
 *
 * 병렬 실행이 안전한 이유는 각 경기가 완전히 독립적이고 봇이
 * 무상태이기 때문이다. 결과 순서는 인덱스로 고정한다.
 */
public final class SeriesRunner {

    private SeriesRunner() {}

    public static List<Replay> run(
            String id0, BotFunction bot0,
            String id1, BotFunction bot1,
            List<Long> seeds, int width, int height, boolean parallel) {

        int total = seeds.size() * 2;
        Replay[] results = new Replay[total];

        IntStream indices = IntStream.range(0, total);
        if (parallel) {
            indices = indices.parallel();
        }

        indices.forEach(i -> {
            long seed = seeds.get(i / 2);
            boolean swapped = (i % 2) == 1;

            Replay replay = swapped
                    ? Match.play(id1, bot1, id0, bot0, seed, width, height)
                    : Match.play(id0, bot0, id1, bot1, seed, width, height);

            results[i] = withSwapFlag(replay, swapped);
        });

        return new ArrayList<>(List.of(results));
    }

    private static Replay withSwapFlag(Replay r, boolean swapped) {
        return new Replay(
                r.schema(), r.matchId() + (swapped ? "-swapped" : ""),
                r.width(), r.height(), r.seed(), swapped,
                r.bot0Id(), r.start0(), r.dir0(),
                r.bot1Id(), r.start1(), r.dir1(),
                r.moves(), r.result(), r.hash());
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :arena-core:test --tests '*SeriesRunnerTest*'`
Expected: 5 tests PASS

`병렬_실행이_순차_실행과_같은_결과를_낸다`가 실패하면 봇이 상태를 갖고 있거나 결과 순서가 흔들린 것이다. 둘 다 심각한 문제이므로 멈추고 원인을 찾는다.

- [ ] **Step 5: 커밋**

```bash
git add arena-core/
git commit -m "$(cat <<'EOF'
feat: 좌석 교대 시리즈 실행기

좌석 교대는 미러링이 아니다. 같은 두 시작 위치에 봇 배정만 바꾼다.
미러링하면 격자가 비대칭일 때 이점이 상쇄되지 않지만 좌석 교대는
어떤 격자에서도 정확히 상쇄된다.

각 경기가 독립적이고 봇이 무상태라 병렬 실행이 안전하다. 결과
순서는 인덱스로 고정해 병렬과 순차가 같은 리플레이를 낸다.

arena-core에 두어 관문이 대전 모듈에 의존하지 않게 했다.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 15: 승점 집계, G7 (회귀 방지), 관문 실행기

**Files:**
- Create: `arena-core/src/main/java/arena/core/Standing.java`
- Create: `arena-gate/src/main/java/arena/gate/RegressionGate.java`
- Create: `arena-gate/src/main/java/arena/gate/GateReport.java`
- Create: `arena-gate/src/main/java/arena/gate/GateRunner.java`
- Test: `arena-core/src/test/java/arena/core/StandingTest.java`
- Test: `arena-gate/src/test/java/arena/gate/GateRunnerTest.java`

**Interfaces:**
- Consumes: `SeriesRunner`, `Replay` (Task 14), 모든 Gate (Task 10~13)
- Produces:
  - `Standing.of(List<Replay> replays, String subjectId)` → `Standing` record (`int wins`, `int draws`, `int losses`, `double scoreRate`) + `int total()`
  - `RegressionGate` (id = `"G7"`), 무인자 생성자
  - `GateReport` record (`String botName`, `boolean passed`, `String failedGate`, `String detail`, `List<GateResult> results`)
  - `GateRunner.run(GateContext ctx)` → `GateReport`, 상수 `GateRunner.SAMPLE_SIZE = 10_000`, `GateRunner.P99_LIMIT_MILLIS = 5.0`

- [ ] **Step 1: 승점 집계 테스트 작성**

`arena-core/src/test/java/arena/core/StandingTest.java`:

```java
package arena.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class StandingTest {

    private Replay replay(String id0, String id1, boolean swapped, int winner) {
        MatchResult result = new MatchResult(winner, 10,
                winner < 0 ? DeathReason.HEAD_ON_COLLISION : DeathReason.P0_HIT_OWN_WALL);
        return new Replay(Replay.SCHEMA, "m", 30, 30, 1L, swapped,
                id0, new Point(1, 1), Direction.UP,
                id1, new Point(2, 2), Direction.DOWN,
                "UU", result, "sha256:x");
    }

    @Test
    void 좌석_교대_경기의_승자를_올바르게_귀속한다() {
        List<Replay> replays = List.of(
                replay("hero", "rival", false, 0),   // hero가 0번 좌석에서 승
                replay("rival", "hero", true, 1)     // hero가 1번 좌석에서 승
        );

        Standing s = Standing.of(replays, "hero");
        assertEquals(2, s.wins());
        assertEquals(0, s.losses());
    }

    @Test
    void 무승부는_0점5를_준다() {
        List<Replay> replays = List.of(
                replay("hero", "rival", false, 0),    // 승
                replay("hero", "rival", false, -1),   // 무
                replay("hero", "rival", false, 1)     // 패
        );

        Standing s = Standing.of(replays, "hero");
        assertEquals(1, s.wins());
        assertEquals(1, s.draws());
        assertEquals(1, s.losses());
        assertEquals(0.5, s.scoreRate(), 1e-9);
    }

    @Test
    void 승점_승률은_승과_무의_절반을_합해_나눈_값이다() {
        List<Replay> replays = List.of(
                replay("hero", "rival", false, 0),
                replay("hero", "rival", false, 0),
                replay("hero", "rival", false, 0),
                replay("hero", "rival", false, -1)
        );
        // (3 + 0.5) / 4 = 0.875
        assertEquals(0.875, Standing.of(replays, "hero").scoreRate(), 1e-9);
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :arena-core:test --tests '*StandingTest*'`
Expected: 컴파일 실패 — `Standing`이 없음

- [ ] **Step 3: Standing 구현**

`arena-core/src/main/java/arena/core/Standing.java`:

```java
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
```

- [ ] **Step 4: G7과 GateRunner 테스트 작성**

`arena-gate/src/test/java/arena/gate/GateRunnerTest.java`:

```java
package arena.gate;

import arena.bots.Bot;
import arena.gate.traps.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 함정 봇 스위트의 본체.
 * 각 함정이 정확히 자기 관문에서 걸려야 한다.
 */
class GateRunnerTest {

    private GateReport run(Bot bot) {
        return GateRunner.run(GateContextFixture.of(bot));
    }

    @Test
    void CleanBot은_모든_관문을_통과한다() {
        GateReport report = run(new CleanBot());
        assertTrue(report.passed(),
                "대조군이 " + report.failedGate() + "에서 막혔다: " + report.detail());
    }

    @Test
    void StatefulTrap은_G2에서_걸린다() {
        assertEquals("G2", run(new StatefulTrap()).failedGate());
    }

    @Test
    void ClockTrap은_G3에서_걸린다() {
        assertEquals("G3", run(new ClockTrap()).failedGate());
    }

    @Test
    void UnseededRandomTrap은_G3에서_걸린다() {
        assertEquals("G3", run(new UnseededRandomTrap()).failedGate());
    }

    @Test
    void CrashTrap은_G4에서_걸린다() {
        assertEquals("G4", run(new CrashTrap()).failedGate());
    }

    @Test
    void NondeterministicTrap은_G5에서_걸린다() {
        assertEquals("G5", run(new NondeterministicTrap()).failedGate());
    }

    @Test
    void SlowTrap은_G6에서_걸린다() {
        assertEquals("G6", run(new SlowTrap()).failedGate());
    }

    @Test
    void WeakTrap은_G7에서_걸린다() {
        assertEquals("G7", run(new WeakTrap()).failedGate());
    }

    @Test
    void 첫_실패에서_멈추고_뒤_관문은_돌리지_않는다() {
        GateReport report = run(new StatefulTrap());
        assertEquals(1, report.results().size(), "G2에서 실패했는데 뒤 관문까지 돌렸다");
    }
}
```

- [ ] **Step 5: 테스트 실패 확인**

Run: `./gradlew :arena-gate:test --tests '*GateRunnerTest*'`
Expected: 컴파일 실패 — `GateRunner`, `GateReport`, `RegressionGate`가 없음

- [ ] **Step 6: G7과 GateRunner 구현**

`arena-gate/src/main/java/arena/gate/RegressionGate.java`:

```java
package arena.gate;

import arena.bots.baseline.RandomBot;
import arena.bots.baseline.StraightBot;
import arena.bots.baseline.WallAvoidBot;
import arena.core.BotFunction;
import arena.core.Replay;
import arena.core.SeriesRunner;
import arena.core.Standing;

import java.util.List;

/**
 * G7 — 고정된 베이스라인 3종에게 한 번도 지지 않아야 한다.
 *
 * "전승"이 아니라 "패배 0회"인 이유가 있다. RandomBot 상대로는
 * 정면 충돌 무승부가 구조적으로 발생하므로, 전승을 요구하면 실력과
 * 무관하게 반려된다.
 *
 * 고정 상대라 변하지 않는 절대 좌표가 생긴다. 도전자가 운으로
 * 챔피언을 이기고 승격해 세대 공선이 뒤로 가는 사고를 막는다.
 */
public final class RegressionGate implements Gate {

    @Override
    public String id() { return "G7"; }

    @Override
    public GateResult check(GateContext ctx) {
        BotFunction subject = v -> ctx.bot().move(v);

        String[] names = { "StraightBot", "RandomBot", "WallAvoidBot" };
        BotFunction[] baselines = {
                v -> new StraightBot().move(v),
                v -> new RandomBot().move(v),
                v -> new WallAvoidBot().move(v),
        };

        for (int i = 0; i < baselines.length; i++) {
            List<Replay> replays = SeriesRunner.run(
                    ctx.bot().name(), subject, names[i], baselines[i],
                    ctx.judgingSeeds(), ctx.width(), ctx.height(), true);

            Standing standing = Standing.of(replays, ctx.bot().name());

            if (standing.losses() > 0) {
                List<String> lostSeeds = replays.stream()
                        .filter(r -> {
                            int mySeat = r.bot0Id().equals(ctx.bot().name()) ? 0 : 1;
                            return !r.result().isDraw() && r.result().winner() != mySeat;
                        })
                        .limit(5)
                        .map(r -> String.valueOf(r.seed()))
                        .toList();

                return GateResult.fail(id(),
                        names[i] + "에게 " + standing.losses() + "번 졌다 "
                                + "(승 " + standing.wins() + " 무 " + standing.draws() + ")"
                                + "\n  진 시드(최대 5개): " + lostSeeds);
            }
        }
        return GateResult.pass(id());
    }
}
```

`arena-gate/src/main/java/arena/gate/GateReport.java`:

```java
package arena.gate;

import java.util.List;

/**
 * 관문 전체의 판정 결과. 반려 시 JSON으로 직렬화해 에이전트에게 돌려준다.
 * failedGate가 null이면 통과다.
 */
public record GateReport(
        String botName,
        boolean passed,
        String failedGate,
        String detail,
        List<GateResult> results
) {}
```

`arena-gate/src/main/java/arena/gate/GateRunner.java`:

```java
package arena.gate;

import arena.core.GameView;

import java.util.ArrayList;
import java.util.List;

/**
 * G2부터 G7까지 순서대로 돌리고 첫 실패에서 멈춘다.
 *
 * 비용이 싼 것부터 배치했다. 피드백 속도와 비용 순서가 같은 방향이라
 * 흔한 실수일수록 빨리 알려준다.
 *
 * G1(컴파일)은 여기에 없다. Gradle이 이 코드에 도달하기 전에 판정한다.
 */
public final class GateRunner {

    /** G4·G5·G6가 공유하는 국면 표본 크기. */
    public static final int SAMPLE_SIZE = 10_000;

    /** G6 응답 시간 상한. 루프가 못 넘는다고 해서 올리지 않는다. */
    public static final double P99_LIMIT_MILLIS = 5.0;

    private GateRunner() {}

    public static GateReport run(GateContext ctx) {
        List<GameView> positions = PositionSampler.sample(SAMPLE_SIZE, ctx.width(), ctx.height());

        List<Gate> gates = List.of(
                new StatelessGate(),
                new ForbiddenApiGate(),
                new LegalMoveGate(positions),
                new DeterminismGate(positions),
                new TimeBudgetGate(positions, P99_LIMIT_MILLIS),
                new RegressionGate());

        List<GateResult> results = new ArrayList<>();

        for (Gate gate : gates) {
            GateResult result = gate.check(ctx);
            results.add(result);

            if (!result.passed()) {
                return new GateReport(ctx.bot().name(), false,
                        result.gateId(), result.detail(), List.copyOf(results));
            }
        }
        return new GateReport(ctx.bot().name(), true, null, "", List.copyOf(results));
    }
}
```

- [ ] **Step 7: 테스트 통과 확인**

Run: `./gradlew :arena-core:test :arena-gate:test`
Expected: 모든 테스트 PASS

`GateRunnerTest`는 국면 10,000개를 수집하고 G7이 300경기를 돌리므로 함정 봇 8종에 대해 수십 초가 걸릴 수 있다. `SlowTrap은_G6에서_걸린다`가 가장 오래 걸린다.

- [ ] **Step 8: 커밋**

```bash
git add arena-core/ arena-gate/
git commit -m "$(cat <<'EOF'
feat: 승점 집계, G7 회귀 방지, 관문 실행기

G7은 "전승"이 아니라 "패배 0회"를 요구한다. RandomBot 상대로는
정면 충돌 무승부가 구조적으로 발생해, 전승을 요구하면 실력과
무관하게 반려된다.

GateRunner는 비용이 싼 관문부터 돌리고 첫 실패에서 멈춘다.
피드백 속도와 비용 순서가 같은 방향이라 흔한 실수일수록 빨리
알려준다.

함정 봇 8종이 각자의 관문에서 정확히 걸리고 CleanBot은 전부
통과하는 것을 테스트로 고정했다. "관문이 진짜 작동하는지 어떻게
압니까"에 대한 답이다.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 16: 챔피언전과 승격 판정

**스펙과의 명명 차이 하나:** 스펙 §7.1의 진단 JSON은 `reachBefore` / `reachAfter`를 쓰지만, 실제 의미는 "최선 대안의 reach"와 "실제 선택의 reach"다. 코드에서는 오해가 없도록 **`reachIfBest` / `reachChosen`** 으로 명명하고 JSON에도 그 이름을 쓴다. 계획 완료 후 스펙 §7.1을 이 이름으로 맞춘다.

**Files:**
- Create: `arena-bots/src/main/java/arena/bots/gen/Gen00Bot.java`
- Create: `arena-tournament/build.gradle`
- Create: `arena-tournament/src/main/java/arena/tournament/DiagnosisEntry.java`
- Create: `arena-tournament/src/main/java/arena/tournament/ChallengeReport.java`
- Create: `arena-tournament/src/main/java/arena/tournament/Championship.java`
- Test: `arena-tournament/src/test/java/arena/tournament/ChampionshipTest.java`

**Interfaces:**
- Consumes: `SeriesRunner`, `Standing` (Task 14, 15), `LossAnalyzer`, `MoveAnalysis` (Task 8), `Bot` (Task 4)
- Produces:
  - `Gen00Bot` — `name()`이 `"Gen00Bot"`, 동작은 `StraightBot`과 동일
  - `DiagnosisEntry` record (`long seed`, `int turn`, `String chose`, `String best`, `int reachIfBest`, `int reachChosen`, `int loss`)
  - `ChallengeReport` record (`String challenger`, `String champion`, `boolean promoted`, `double scoreRate`, `double threshold`, `int wins`, `int draws`, `int losses`, `double holdoutScoreRate`, `List<DiagnosisEntry> diagnosis`)
  - `Championship.judge(Bot challenger, Bot champion, List<Long> judgingSeeds, List<Long> holdoutSeeds, int width, int height)` → `ChallengeReport`
  - 상수 `Championship.PROMOTION_THRESHOLD = 0.60`

- [ ] **Step 1: Gen00Bot 작성**

`arena-bots/src/main/java/arena/bots/gen/Gen00Bot.java`:

```java
package arena.bots.gen;

import arena.bots.Bot;
import arena.core.Direction;
import arena.core.GameView;

/**
 * 챔피언 계보의 출발점. 동작은 StraightBot과 같다.
 *
 * Gen 0은 사람이 심는 기준선이며 관문 대상이 아니다. 루프는 Gen 1부터
 * 돈다. 처참하게 약해야 R3의 개선 곡선이 극적으로 나온다.
 */
public final class Gen00Bot implements Bot {

    @Override
    public String name() { return "Gen00Bot"; }

    @Override
    public Direction move(GameView view) {
        return view.myDir();
    }
}
```

- [ ] **Step 2: 빌드 파일과 실패하는 테스트 작성**

`arena-tournament/build.gradle`:

```groovy
dependencies {
    implementation project(':arena-core')
    implementation project(':arena-bots')
    implementation project(':arena-diagnostics')
    implementation project(':arena-gate')
    implementation 'com.fasterxml.jackson.core:jackson-databind:2.18.2'

    testImplementation project(':arena-core')
    testImplementation project(':arena-bots')
}
```

`arena-tournament/src/test/java/arena/tournament/ChampionshipTest.java`:

```java
package arena.tournament;

import arena.bots.Bot;
import arena.bots.baseline.WallAvoidBot;
import arena.bots.gen.Gen00Bot;
import arena.core.Direction;
import arena.core.GameView;
import arena.core.Point;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.LongStream;
import static org.junit.jupiter.api.Assertions.*;

class ChampionshipTest {

    private static final List<Long> JUDGING = LongStream.rangeClosed(1, 50).boxed().toList();
    private static final List<Long> HOLDOUT = LongStream.rangeClosed(1001, 1050).boxed().toList();

    private ChallengeReport judge(Bot challenger, Bot champion) {
        return Championship.judge(challenger, champion, JUDGING, HOLDOUT, 30, 30);
    }

    @Test
    void 승격_기준은_60퍼센트다() {
        assertEquals(0.60, Championship.PROMOTION_THRESHOLD, 1e-9);
    }

    @Test
    void 벽회피봇은_직진봇_챔피언을_압도해_승격한다() {
        ChallengeReport r = judge(new WallAvoidBot(), new Gen00Bot());

        assertTrue(r.promoted(), "승점 승률 " + r.scoreRate() + "로 승격에 실패했다");
        assertTrue(r.scoreRate() >= 0.60);
        assertEquals(100, r.wins() + r.draws() + r.losses(), "심사는 100경기여야 한다");
    }

    @Test
    void 같은_봇끼리_붙으면_승격하지_못한다() {
        ChallengeReport r = judge(new SlightlyDifferentStraight(), new Gen00Bot());

        assertFalse(r.promoted(), "직진봇과 사실상 같은데 승격했다");
    }

    @Test
    void 반려되면_진단이_붙는다() {
        ChallengeReport r = judge(new SlightlyDifferentStraight(), new Gen00Bot());

        assertFalse(r.promoted());
        assertFalse(r.diagnosis().isEmpty(), "반려됐는데 진단이 비어 있다");
        assertTrue(r.diagnosis().get(0).loss() >= 0);
    }

    @Test
    void 승격하면_홀드아웃_승률이_함께_기록된다() {
        ChallengeReport r = judge(new WallAvoidBot(), new Gen00Bot());

        assertTrue(r.promoted());
        assertTrue(r.holdoutScoreRate() > 0.0, "홀드아웃 승률이 기록되지 않았다");
    }

    @Test
    void 반려되면_홀드아웃은_돌리지_않는다() {
        ChallengeReport r = judge(new SlightlyDifferentStraight(), new Gen00Bot());

        assertFalse(r.promoted());
        assertEquals(Double.NaN, r.holdoutScoreRate(),
                "반려된 봇에 홀드아웃 시드를 낭비했다");
    }

    @Test
    void 같은_인자로_두_번_판정하면_같은_결과가_나온다() {
        ChallengeReport a = judge(new WallAvoidBot(), new Gen00Bot());
        ChallengeReport b = judge(new WallAvoidBot(), new Gen00Bot());

        assertEquals(a.scoreRate(), b.scoreRate(), 1e-12);
        assertEquals(a.wins(), b.wins());
    }

    /** 직진봇과 거의 같지만 이름만 다른 봇. 승격 기준을 넘지 못해야 한다. */
    static final class SlightlyDifferentStraight implements Bot {
        public String name() { return "SlightlyDifferentStraight"; }
        public Direction move(GameView view) {
            Point p = view.myHead().move(view.myDir());
            // 격자 밖으로 나갈 때만 한 번 꺾는다. 그 외에는 직진봇과 같다.
            if (!view.inBounds(p.x(), p.y())) {
                for (Direction d : Direction.values()) {
                    if (!view.isDeadly(d)) return d;
                }
            }
            return view.myDir();
        }
    }
}
```

- [ ] **Step 3: 테스트 실패 확인**

Run: `./gradlew :arena-tournament:test`
Expected: 컴파일 실패 — `Championship`, `ChallengeReport`, `DiagnosisEntry`가 없음

- [ ] **Step 4: 최소 구현**

`arena-tournament/src/main/java/arena/tournament/DiagnosisEntry.java`:

```java
package arena.tournament;

/**
 * 치명적인 수 하나에 대한 판정.
 *
 * reachIfBest  최선 대안을 골랐을 때 닿을 수 있었던 칸 수
 * reachChosen  실제 선택 이후 닿을 수 있는 칸 수
 * loss         둘의 차이. 이 수로 잃은 공간이다.
 */
public record DiagnosisEntry(
        long seed,
        int turn,
        String chose,
        String best,
        int reachIfBest,
        int reachChosen,
        int loss
) {}
```

`arena-tournament/src/main/java/arena/tournament/ChallengeReport.java`:

```java
package arena.tournament;

import java.util.List;

/**
 * 챔피언전 결과. 반려 시 JSON으로 직렬화해 에이전트에게 돌려준다.
 *
 * holdoutScoreRate는 승격했을 때만 채워지고, 반려 시에는 NaN이다.
 * 심사 승률과의 격차가 시드 과적합의 정도를 말해준다.
 */
public record ChallengeReport(
        String challenger,
        String champion,
        boolean promoted,
        double scoreRate,
        double threshold,
        int wins,
        int draws,
        int losses,
        double holdoutScoreRate,
        List<DiagnosisEntry> diagnosis
) {}
```

`arena-tournament/src/main/java/arena/tournament/Championship.java`:

```java
package arena.tournament;

import arena.bots.Bot;
import arena.core.*;
import arena.diagnostics.LossAnalyzer;
import arena.diagnostics.MoveAnalysis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 도전자가 챔피언을 교체할 자격이 있는지 판정한다.
 *
 * 봇이 무상태 결정론이고 시작 위치가 시드로 고정되므로 승률은 확률이
 * 아니라 하나의 고정된 숫자다. 표본 크기나 우연에 대한 통계 논의가
 * 필요 없고 임계값만 있으면 된다.
 */
public final class Championship {

    /** 승점 승률 기준. 루프가 못 넘는다고 해서 낮추지 않는다 (BRIEF §11-4). */
    public static final double PROMOTION_THRESHOLD = 0.60;

    /** 반려 리포트에 담을 치명적인 수의 개수. */
    private static final int DIAGNOSIS_LIMIT = 3;

    private Championship() {}

    public static ChallengeReport judge(
            Bot challenger, Bot champion,
            List<Long> judgingSeeds, List<Long> holdoutSeeds,
            int width, int height) {

        List<Replay> replays = SeriesRunner.run(
                challenger.name(), v -> challenger.move(v),
                champion.name(), v -> champion.move(v),
                judgingSeeds, width, height, true);

        Standing standing = Standing.of(replays, challenger.name());
        boolean promoted = standing.scoreRate() >= PROMOTION_THRESHOLD;

        double holdoutRate = Double.NaN;
        List<DiagnosisEntry> diagnosis = List.of();

        if (promoted) {
            // 승격한 봇만 홀드아웃 시드를 쓴다. 심사 승률과의 격차가 과적합 신호다.
            List<Replay> holdout = SeriesRunner.run(
                    challenger.name(), v -> challenger.move(v),
                    champion.name(), v -> champion.move(v),
                    holdoutSeeds, width, height, true);
            holdoutRate = Standing.of(holdout, challenger.name()).scoreRate();
        } else {
            diagnosis = diagnose(replays, challenger.name());
        }

        return new ChallengeReport(
                challenger.name(), champion.name(), promoted,
                standing.scoreRate(), PROMOTION_THRESHOLD,
                standing.wins(), standing.draws(), standing.losses(),
                holdoutRate, diagnosis);
    }

    /** 패배한 경기들에서 손실이 가장 큰 수를 뽑는다. */
    private static List<DiagnosisEntry> diagnose(List<Replay> replays, String subjectId) {
        List<DiagnosisEntry> all = new ArrayList<>();

        for (Replay r : replays) {
            int mySeat = r.bot0Id().equals(subjectId) ? 0 : 1;
            boolean lost = !r.result().isDraw() && r.result().winner() != mySeat;
            if (!lost) continue;

            for (MoveAnalysis a : LossAnalyzer.worstMoves(r, mySeat, 1)) {
                all.add(new DiagnosisEntry(
                        r.seed(), a.turn(),
                        a.chose().name(), a.best().name(),
                        a.reachAfterBest(), a.reachAfterChosen(), a.loss()));
            }
        }

        return all.stream()
                .sorted(Comparator.comparingInt(DiagnosisEntry::loss).reversed())
                .limit(DIAGNOSIS_LIMIT)
                .toList();
    }
}
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew :arena-tournament:test`
Expected: 7 tests PASS

`벽회피봇은_직진봇_챔피언을_압도해_승격한다`가 실패하면 승률을 출력해 확인한다. 벽회피봇은 직진봇 상대로 90% 이상이 나와야 정상이다.

- [ ] **Step 6: 커밋**

```bash
git add arena-bots/ arena-tournament/
git commit -m "$(cat <<'EOF'
feat: 챔피언전 승격 판정과 진단 리포트

승점 승률 60% 이상이면 승격한다. 봇이 무상태 결정론이고 시작
위치가 시드로 고정되므로 승률은 확률이 아니라 고정된 숫자다.
표본 크기나 우연에 대한 통계 논의가 필요 없다.

홀드아웃 시드는 승격한 봇에만 쓴다. 심사 승률과의 격차가 시드
과적합의 정도를 말해준다.

반려 시 패배 경기에서 손실 상위 3개 수를 뽑아 돌려준다. 진단
필드는 reachIfBest/reachChosen으로 명명했다 — 스펙의
reachBefore/reachAfter보다 실제 의미를 정확히 담는다.

Gen00Bot을 심었다. 챔피언 계보의 출발점이며 관문 대상이 아니다.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 17: 기록 저장소

**Files:**
- Create: `arena-tournament/src/main/java/arena/tournament/RecordStore.java`
- Create: `arena-tournament/src/main/java/arena/tournament/AttemptRecord.java`
- Test: `arena-tournament/src/test/java/arena/tournament/RecordStoreTest.java`

**Interfaces:**
- Consumes: `GateReport` (Task 15), `ChallengeReport` (Task 16), `Replay` (Task 7)
- Produces:
  - `AttemptRecord` record (`int generation`, `int attempt`, `String verdict`, `String stage`, `String failedGate`, `String detail`)
  - `RecordStore(Path root)` + `int nextAttempt(int generation)` + `void saveGateReport(int gen, int attempt, String botSource, GateReport report)` + `void saveChallengeReport(int gen, int attempt, ChallengeReport report)` + `void saveReplays(int gen, List<Replay> replays)` + `List<AttemptRecord> historyOf(int generation)`

- [ ] **Step 1: 실패하는 테스트 작성**

`arena-tournament/src/test/java/arena/tournament/RecordStoreTest.java`:

```java
package arena.tournament;

import arena.gate.GateReport;
import arena.gate.GateResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RecordStoreTest {

    private GateReport rejected(String gate) {
        return new GateReport("Gen07Bot", false, gate, gate + " 위반",
                List.of(GateResult.fail(gate, gate + " 위반")));
    }

    private ChallengeReport challengeRejected() {
        return new ChallengeReport("Gen07Bot", "Gen06Bot", false, 0.48, 0.60,
                44, 8, 48, Double.NaN,
                List.of(new DiagnosisEntry(12, 87, "UP", "LEFT", 214, 31, 183)));
    }

    @Test
    void 첫_시도는_1번이다(@TempDir Path tmp) {
        assertEquals(1, new RecordStore(tmp).nextAttempt(7));
    }

    @Test
    void 저장할수록_시도_번호가_올라간다(@TempDir Path tmp) {
        RecordStore store = new RecordStore(tmp);

        store.saveGateReport(7, 1, "class A {}", rejected("G3"));
        assertEquals(2, store.nextAttempt(7));

        store.saveGateReport(7, 2, "class B {}", rejected("G4"));
        assertEquals(3, store.nextAttempt(7));
    }

    @Test
    void 반려된_봇_소스도_남긴다(@TempDir Path tmp) throws Exception {
        RecordStore store = new RecordStore(tmp);
        store.saveGateReport(7, 1, "class Rejected {}", rejected("G3"));

        Path source = tmp.resolve("gen-07/attempt-1/bot.java");
        assertTrue(Files.exists(source), "반려된 시도의 소스가 지워졌다");
        assertEquals("class Rejected {}", Files.readString(source));
    }

    @Test
    void 관문_리포트를_JSON으로_남긴다(@TempDir Path tmp) throws Exception {
        RecordStore store = new RecordStore(tmp);
        store.saveGateReport(7, 1, "class A {}", rejected("G3"));

        String json = Files.readString(tmp.resolve("gen-07/attempt-1/gate-report.json"));
        assertTrue(json.contains("\"failedGate\""), json);
        assertTrue(json.contains("G3"), json);
    }

    @Test
    void 챔피언전_리포트를_JSON으로_남긴다(@TempDir Path tmp) throws Exception {
        RecordStore store = new RecordStore(tmp);
        store.saveChallengeReport(7, 3, challengeRejected());

        String json = Files.readString(tmp.resolve("gen-07/attempt-3/championship.json"));
        assertTrue(json.contains("\"scoreRate\""), json);
        assertTrue(json.contains("0.48"), json);
    }

    @Test
    void 이력에_반려_사유가_순서대로_쌓인다(@TempDir Path tmp) {
        RecordStore store = new RecordStore(tmp);
        store.saveGateReport(7, 1, "a", rejected("G3"));
        store.saveGateReport(7, 2, "b", rejected("G4"));
        store.saveChallengeReport(7, 3, challengeRejected());

        List<AttemptRecord> history = store.historyOf(7);

        assertEquals(3, history.size());
        assertEquals("G3", history.get(0).failedGate());
        assertEquals("G4", history.get(1).failedGate());
        assertEquals("CHAMPIONSHIP", history.get(2).stage());
        assertEquals("REJECTED", history.get(2).verdict());
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :arena-tournament:test --tests '*RecordStoreTest*'`
Expected: 컴파일 실패 — `RecordStore`, `AttemptRecord`가 없음

- [ ] **Step 3: 최소 구현**

`arena-tournament/src/main/java/arena/tournament/AttemptRecord.java`:

```java
package arena.tournament;

/**
 * 한 번의 시도. verdict는 PROMOTED 또는 REJECTED,
 * stage는 GATE 또는 CHAMPIONSHIP이다.
 */
public record AttemptRecord(
        int generation,
        int attempt,
        String verdict,
        String stage,
        String failedGate,
        String detail
) {}
```

`arena-tournament/src/main/java/arena/tournament/RecordStore.java`:

```java
package arena.tournament;

import arena.core.Replay;
import arena.gate.GateReport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 시도 이력을 파일로 남긴다.
 *
 * 반려된 봇 소스도 지우지 않는다. 실패 횟수가 보이는 편이 발표에
 * 유리하고, 반려 이력 자체가 "루프가 돌았다"의 증거다 (BRIEF §8).
 */
public final class RecordStore {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private final Path root;

    public RecordStore(Path root) {
        this.root = root;
    }

    public int nextAttempt(int generation) {
        Path genDir = generationDir(generation);
        if (!Files.isDirectory(genDir)) return 1;

        try (var entries = Files.list(genDir)) {
            return entries
                    .map(p -> p.getFileName().toString())
                    .filter(n -> n.startsWith("attempt-"))
                    .mapToInt(n -> Integer.parseInt(n.substring("attempt-".length())))
                    .max()
                    .orElse(0) + 1;
        } catch (IOException e) {
            throw new UncheckedIOException("시도 번호를 셀 수 없다: " + genDir, e);
        }
    }

    public void saveGateReport(int gen, int attempt, String botSource, GateReport report) {
        Path dir = attemptDir(gen, attempt);
        write(dir.resolve("bot.java"), botSource);
        writeJson(dir.resolve("gate-report.json"), report);
    }

    public void saveChallengeReport(int gen, int attempt, ChallengeReport report) {
        writeJson(attemptDir(gen, attempt).resolve("championship.json"), report);
    }

    public void saveReplays(int gen, List<Replay> replays) {
        writeJson(generationDir(gen).resolve("replays.json"), replays);
    }

    /** 세대의 시도 이력을 번호 순으로 읽는다. */
    public List<AttemptRecord> historyOf(int generation) {
        List<AttemptRecord> history = new ArrayList<>();

        for (int attempt = 1; attempt < nextAttempt(generation); attempt++) {
            Path dir = attemptDir(generation, attempt);

            Path championship = dir.resolve("championship.json");
            if (Files.exists(championship)) {
                ChallengeReport r = readJson(championship, ChallengeReport.class);
                history.add(new AttemptRecord(generation, attempt,
                        r.promoted() ? "PROMOTED" : "REJECTED", "CHAMPIONSHIP", null,
                        String.format("승점 승률 %.2f (기준 %.2f)", r.scoreRate(), r.threshold())));
                continue;
            }

            Path gate = dir.resolve("gate-report.json");
            if (Files.exists(gate)) {
                GateReport r = readJson(gate, GateReport.class);
                history.add(new AttemptRecord(generation, attempt,
                        r.passed() ? "PASSED" : "REJECTED", "GATE",
                        r.failedGate(), r.detail()));
            }
        }
        return history;
    }

    private Path generationDir(int generation) {
        return root.resolve(String.format("gen-%02d", generation));
    }

    private Path attemptDir(int generation, int attempt) {
        Path dir = generationDir(generation).resolve("attempt-" + attempt);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException("디렉터리를 만들 수 없다: " + dir, e);
        }
        return dir;
    }

    private static void write(Path path, String content) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, content);
        } catch (IOException e) {
            throw new UncheckedIOException("파일을 쓸 수 없다: " + path, e);
        }
    }

    private static void writeJson(Path path, Object value) {
        try {
            Files.createDirectories(path.getParent());
            MAPPER.writeValue(path.toFile(), value);
        } catch (IOException e) {
            throw new UncheckedIOException("JSON을 쓸 수 없다: " + path, e);
        }
    }

    private static <T> T readJson(Path path, Class<T> type) {
        try {
            return MAPPER.readValue(path.toFile(), type);
        } catch (IOException e) {
            throw new UncheckedIOException("JSON을 읽을 수 없다: " + path, e);
        }
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :arena-tournament:test --tests '*RecordStoreTest*'`
Expected: 6 tests PASS

Jackson이 record를 역직렬화하지 못해 실패하면 `jackson-databind` 2.12 이상인지 확인한다. 2.18.2는 record를 기본 지원한다.

- [ ] **Step 5: 커밋**

```bash
git add arena-tournament/
git commit -m "$(cat <<'EOF'
feat: 시도 이력 저장소

반려된 봇 소스도 지우지 않는다. 실패 횟수가 보이는 편이 발표에
유리하고, 반려 이력 자체가 "루프가 돌았다"의 가장 직접적인
증거다.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 18: 라운드로빈과 발표 번들

**Files:**
- Create: `arena-tournament/src/main/java/arena/tournament/RoundRobin.java`
- Create: `arena-tournament/src/main/java/arena/tournament/GenerationStat.java`
- Create: `arena-tournament/src/main/java/arena/tournament/BundleBuilder.java`
- Test: `arena-tournament/src/test/java/arena/tournament/RoundRobinTest.java`
- Test: `arena-tournament/src/test/java/arena/tournament/BundleBuilderTest.java`

**Interfaces:**
- Consumes: `SeriesRunner`, `Standing`, `LossAnalyzer`, `RecordStore` (Task 8, 14, 15, 17)
- Produces:
  - `RoundRobin.run(List<Bot> bots, List<Long> seeds, int width, int height)` → `double[][]` (`[i][j]` = 봇 i가 봇 j 상대로 낸 승점 승률, 대각선은 `Double.NaN`)
  - `GenerationStat` record (`int generation`, `String botName`, `double avgSurvivalTurns`, `double occupancy`, `double suicideRate`, `double scoreRate`, `int attempts`)
  - `BundleBuilder.build(List<Bot> generations, Bot finalChampion, long gallerySeed, List<Long> judgingSeeds, int width, int height, RecordStore store, Path outputDir)` → `void`

- [ ] **Step 1: 실패하는 테스트 작성**

`arena-tournament/src/test/java/arena/tournament/RoundRobinTest.java`:

```java
package arena.tournament;

import arena.bots.Bot;
import arena.bots.baseline.RandomBot;
import arena.bots.baseline.WallAvoidBot;
import arena.bots.gen.Gen00Bot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.LongStream;
import static org.junit.jupiter.api.Assertions.*;

class RoundRobinTest {

    private static final List<Long> SEEDS = LongStream.rangeClosed(1, 10).boxed().toList();
    private static final List<Bot> BOTS =
            List.of(new Gen00Bot(), new RandomBot(), new WallAvoidBot());

    @Test
    void 정사각_행렬을_낸다() {
        double[][] m = RoundRobin.run(BOTS, SEEDS, 30, 30);

        assertEquals(3, m.length);
        for (double[] row : m) {
            assertEquals(3, row.length);
        }
    }

    @Test
    void 대각선은_비운다() {
        double[][] m = RoundRobin.run(BOTS, SEEDS, 30, 30);

        for (int i = 0; i < 3; i++) {
            assertTrue(Double.isNaN(m[i][i]), "자기 자신과의 대전이 채워졌다");
        }
    }

    @Test
    void 마주보는_칸의_승률은_합이_1이다() {
        double[][] m = RoundRobin.run(BOTS, SEEDS, 30, 30);

        for (int i = 0; i < 3; i++) {
            for (int j = i + 1; j < 3; j++) {
                assertEquals(1.0, m[i][j] + m[j][i], 1e-9,
                        "(" + i + "," + j + ")의 승률 합이 1이 아니다");
            }
        }
    }

    @Test
    void 벽회피봇이_직진봇을_압도한다() {
        double[][] m = RoundRobin.run(BOTS, SEEDS, 30, 30);

        assertTrue(m[2][0] > 0.8, "벽회피봇의 대 직진봇 승률이 " + m[2][0] + "밖에 안 된다");
    }
}
```

`arena-tournament/src/test/java/arena/tournament/BundleBuilderTest.java`:

```java
package arena.tournament;

import arena.bots.Bot;
import arena.bots.baseline.RandomBot;
import arena.bots.baseline.WallAvoidBot;
import arena.bots.gen.Gen00Bot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.*;

class BundleBuilderTest {

    private static final List<Long> SEEDS = LongStream.rangeClosed(1, 10).boxed().toList();

    private void build(Path out, Path records) {
        List<Bot> generations = List.of(new Gen00Bot(), new RandomBot(), new WallAvoidBot());
        BundleBuilder.build(generations, new WallAvoidBot(), 1L, SEEDS, 30, 30,
                new RecordStore(records), out);
    }

    @Test
    void 화면이_읽을_파일_넷을_만든다(@TempDir Path tmp) {
        Path out = tmp.resolve("data");
        build(out, tmp.resolve("records"));

        for (String name : new String[]{
                "gallery.json", "generations.json", "loop-history.json", "roundrobin.json"}) {
            assertTrue(Files.exists(out.resolve(name)), name + "이 없다");
        }
    }

    @Test
    void 갤러리는_세대마다_경기_하나씩_담는다(@TempDir Path tmp) throws Exception {
        Path out = tmp.resolve("data");
        build(out, tmp.resolve("records"));

        String json = Files.readString(out.resolve("gallery.json"));
        assertTrue(json.contains("Gen00Bot"), json.substring(0, Math.min(400, json.length())));
        assertTrue(json.contains("\"moves\""), "리플레이 본문이 없다");
    }

    @Test
    void 갤러리의_모든_경기는_같은_시드를_쓴다(@TempDir Path tmp) throws Exception {
        Path out = tmp.resolve("data");
        build(out, tmp.resolve("records"));

        String json = Files.readString(out.resolve("gallery.json"));
        assertFalse(json.contains("\"seed\":2"),
                "세대마다 다른 시드를 쓰면 패널끼리 비교할 수 없다");
    }

    @Test
    void 세대_지표에_생존턴과_자멸률이_담긴다(@TempDir Path tmp) throws Exception {
        Path out = tmp.resolve("data");
        build(out, tmp.resolve("records"));

        String json = Files.readString(out.resolve("generations.json"));
        assertTrue(json.contains("avgSurvivalTurns"), json);
        assertTrue(json.contains("suicideRate"), json);
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :arena-tournament:test --tests '*RoundRobinTest*' --tests '*BundleBuilderTest*'`
Expected: 컴파일 실패 — `RoundRobin`, `BundleBuilder`, `GenerationStat`이 없음

- [ ] **Step 3: 최소 구현**

`arena-tournament/src/main/java/arena/tournament/RoundRobin.java`:

```java
package arena.tournament;

import arena.bots.Bot;
import arena.core.Replay;
import arena.core.SeriesRunner;
import arena.core.Standing;

import java.util.Arrays;
import java.util.List;

/**
 * 전 세대 대진표.
 *
 * 승격 판정에는 쓰지 않는다. 마지막에 한 번만 돌려 히트맵으로 남긴다.
 * 순환 우위(A가 B를 이기고 B가 C를 이기는데 C가 A를 이기는 관계)가
 * 나오면 그것도 그대로 보여줄 재료다.
 */
public final class RoundRobin {

    private RoundRobin() {}

    /** [i][j] = 봇 i가 봇 j 상대로 낸 승점 승률. 대각선은 NaN. */
    public static double[][] run(List<Bot> bots, List<Long> seeds, int width, int height) {
        int n = bots.size();
        double[][] matrix = new double[n][n];
        for (double[] row : matrix) {
            Arrays.fill(row, Double.NaN);
        }

        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                Bot a = bots.get(i);
                Bot b = bots.get(j);

                List<Replay> replays = SeriesRunner.run(
                        a.name(), v -> a.move(v),
                        b.name(), v -> b.move(v),
                        seeds, width, height, true);

                double rateA = Standing.of(replays, a.name()).scoreRate();
                matrix[i][j] = rateA;
                matrix[j][i] = 1.0 - rateA;
            }
        }
        return matrix;
    }
}
```

`arena-tournament/src/main/java/arena/tournament/GenerationStat.java`:

```java
package arena.tournament;

/**
 * 세대 하나의 성적. 개선 곡선 화면이 이걸 그대로 그린다.
 *
 * avgSurvivalTurns가 R3의 주 지표다. 화면에서 패널이 멈추는 시점
 * 그 자체이므로, 눈에 보이는 것과 코드가 재는 것이 같은 양이 된다.
 */
public record GenerationStat(
        int generation,
        String botName,
        double avgSurvivalTurns,
        double occupancy,
        double suicideRate,
        double scoreRate,
        int attempts
) {}
```

`arena-tournament/src/main/java/arena/tournament/BundleBuilder.java`:

```java
package arena.tournament;

import arena.bots.Bot;
import arena.core.*;
import arena.diagnostics.LossAnalyzer;
import arena.diagnostics.MatchMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 화면이 읽을 정적 JSON을 만든다.
 *
 * 프론트엔드가 이 파일들만 읽으므로 발표 당일 백엔드가 떠 있지
 * 않아도 화면이 뜬다. 서버 장애라는 위험 범주 자체가 사라진다.
 */
public final class BundleBuilder {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private BundleBuilder() {}

    public static void build(
            List<Bot> generations, Bot finalChampion,
            long gallerySeed, List<Long> judgingSeeds,
            int width, int height,
            RecordStore store, Path outputDir) {

        List<Replay> gallery = buildGallery(generations, finalChampion, gallerySeed, width, height);
        List<GenerationStat> stats = buildStats(
                generations, finalChampion, judgingSeeds, width, height, store);

        writeJson(outputDir.resolve("gallery.json"), gallery);
        writeJson(outputDir.resolve("generations.json"), stats);
        writeJson(outputDir.resolve("loop-history.json"), buildHistory(generations, store));
        writeJson(outputDir.resolve("roundrobin.json"),
                buildRoundRobin(generations, judgingSeeds.subList(0, Math.min(10, judgingSeeds.size())),
                        width, height));
    }

    /**
     * 모든 세대가 같은 시드로 최종 챔피언에게 도전하는 경기.
     * 패널끼리 비교하려면 상대와 시작 배치가 같아야 한다.
     */
    private static List<Replay> buildGallery(
            List<Bot> generations, Bot champion, long seed, int width, int height) {

        List<Replay> gallery = new ArrayList<>();
        for (Bot bot : generations) {
            gallery.add(Match.play(
                    bot.name(), v -> bot.move(v),
                    champion.name(), v -> champion.move(v),
                    seed, width, height));
        }
        return gallery;
    }

    private static List<GenerationStat> buildStats(
            List<Bot> generations, Bot champion, List<Long> seeds,
            int width, int height, RecordStore store) {

        List<GenerationStat> stats = new ArrayList<>();

        for (int gen = 0; gen < generations.size(); gen++) {
            Bot bot = generations.get(gen);

            List<Replay> replays = SeriesRunner.run(
                    bot.name(), v -> bot.move(v),
                    champion.name(), v -> champion.move(v),
                    seeds, width, height, true);

            double totalTurns = 0;
            double totalOccupancy = 0;
            double totalSuicide = 0;

            for (Replay r : replays) {
                int mySeat = r.bot0Id().equals(bot.name()) ? 0 : 1;
                MatchMetrics m = LossAnalyzer.analyze(r);

                totalTurns += r.result().turns();
                totalOccupancy += m.occupancy()[mySeat];
                totalSuicide += m.suicideRate()[mySeat];
            }

            int n = replays.size();
            stats.add(new GenerationStat(
                    gen, bot.name(),
                    totalTurns / n,
                    totalOccupancy / n,
                    totalSuicide / n,
                    Standing.of(replays, bot.name()).scoreRate(),
                    Math.max(1, store.nextAttempt(gen) - 1)));
        }
        return stats;
    }

    private static Map<String, List<AttemptRecord>> buildHistory(
            List<Bot> generations, RecordStore store) {

        Map<String, List<AttemptRecord>> history = new LinkedHashMap<>();
        for (int gen = 0; gen < generations.size(); gen++) {
            history.put(String.valueOf(gen), store.historyOf(gen));
        }
        return history;
    }

    private static Map<String, Object> buildRoundRobin(
            List<Bot> generations, List<Long> seeds, int width, int height) {

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("bots", generations.stream().map(Bot::name).toList());
        payload.put("matrix", RoundRobin.run(generations, seeds, width, height));
        return payload;
    }

    private static void writeJson(Path path, Object value) {
        try {
            Files.createDirectories(path.getParent());
            MAPPER.writeValue(path.toFile(), value);
        } catch (IOException e) {
            throw new UncheckedIOException("발표 번들을 쓸 수 없다: " + path, e);
        }
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :arena-tournament:test --tests '*RoundRobinTest*' --tests '*BundleBuilderTest*'`
Expected: 8 tests PASS

- [ ] **Step 5: 커밋**

```bash
git add arena-tournament/
git commit -m "$(cat <<'EOF'
feat: 라운드로빈 히트맵과 발표 번들 생성

라운드로빈은 승격 판정에 쓰지 않고 마지막에 한 번만 돌린다.
순환 우위가 나오면 그것도 그대로 보여줄 재료다.

갤러리는 모든 세대가 같은 시드로 최종 챔피언에게 도전하는 경기를
담는다. 패널끼리 비교하려면 상대와 시작 배치가 같아야 한다.

프론트엔드가 이 정적 JSON만 읽으므로 발표 당일 백엔드가 떠 있지
않아도 화면이 뜬다. 서버 장애라는 위험 범주 자체가 사라진다.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 19: CLI — 하네스의 사용자 인터페이스

에이전트가 실제로 만지는 표면이다. 이 넷이 하네스의 전부다.

**Files:**
- Create: `arena-bots/src/main/java/arena/bots/BotRegistry.java`
- Create: `arena-api/build.gradle`
- Create: `arena-api/src/main/java/arena/api/ArenaApplication.java`
- Create: `arena-api/src/main/java/arena/api/Seeds.java`
- Create: `arena-api/src/main/java/arena/api/cli/GateCommand.java`
- Create: `arena-api/src/main/java/arena/api/cli/ChallengeCommand.java`
- Create: `arena-api/src/main/java/arena/api/cli/RecordCommand.java`
- Modify: `build.gradle` (루트에 Gradle 태스크 넷 추가)
- Test: `arena-bots/src/test/java/arena/bots/BotRegistryTest.java`
- Test: `arena-api/src/test/java/arena/api/cli/RecordCommandTest.java`

**Interfaces:**
- Consumes: `GateRunner` (Task 15), `Championship` (Task 16), `RecordStore` (Task 17), `BundleBuilder` (Task 18)
- Produces:
  - `BotRegistry.byName(String name)` → `Bot`, `BotRegistry.allGenerations()` → `List<Bot>` (Gen 번호 오름차순), `BotRegistry.latestGeneration()` → `Bot`
  - `Seeds.JUDGING` (1‥50), `Seeds.HOLDOUT` (1001‥1050), `Seeds.ROUND_ROBIN` (1‥10), `Seeds.GALLERY` (= 1L)
  - `GateCommand.run(String botName)` → `int` (종료 코드), `ChallengeCommand.run(String botName)` → `int`, `RecordCommand.run(boolean verifyOnly)` → `int`

- [ ] **Step 1: 실패하는 테스트 작성**

`arena-bots/src/test/java/arena/bots/BotRegistryTest.java`:

```java
package arena.bots;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BotRegistryTest {

    @Test
    void 이름으로_봇을_찾는다() {
        assertEquals("Gen00Bot", BotRegistry.byName("Gen00Bot").name());
        assertEquals("WallAvoidBot", BotRegistry.byName("WallAvoidBot").name());
    }

    @Test
    void 없는_이름은_친절한_오류를_낸다() {
        var e = assertThrows(IllegalArgumentException.class,
                () -> BotRegistry.byName("Gen99Bot"));
        assertTrue(e.getMessage().contains("Gen99Bot"), e.getMessage());
    }

    @Test
    void 세대_봇은_번호_오름차순으로_나온다() {
        var generations = BotRegistry.allGenerations();

        assertFalse(generations.isEmpty());
        assertEquals("Gen00Bot", generations.get(0).name());
        for (int i = 1; i < generations.size(); i++) {
            assertTrue(generations.get(i).name().compareTo(generations.get(i - 1).name()) > 0,
                    "세대 순서가 뒤엉켰다");
        }
    }

    @Test
    void 최신_세대는_목록의_마지막이다() {
        var generations = BotRegistry.allGenerations();
        assertEquals(generations.get(generations.size() - 1).name(),
                BotRegistry.latestGeneration().name());
    }
}
```

`arena-api/src/test/java/arena/api/cli/RecordCommandTest.java`:

```java
package arena.api.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class RecordCommandTest {

    @Test
    void 번들을_만들고_0을_반환한다(@TempDir Path tmp) {
        int code = RecordCommand.runInto(tmp.resolve("records"), tmp.resolve("data"), false);

        assertEquals(0, code);
        assertTrue(Files.exists(tmp.resolve("data/gallery.json")));
    }

    @Test
    void verify는_두_번_돌려_해시가_같으면_0을_반환한다(@TempDir Path tmp) {
        RecordCommand.runInto(tmp.resolve("records"), tmp.resolve("data"), false);
        int code = RecordCommand.runInto(tmp.resolve("records"), tmp.resolve("data"), true);

        assertEquals(0, code, "재현 검증이 실패했다 — R1이 깨졌다");
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :arena-bots:test --tests '*BotRegistryTest*'`
Expected: 컴파일 실패 — `BotRegistry`가 없음

- [ ] **Step 3: BotRegistry 구현**

`arena-bots/src/main/java/arena/bots/BotRegistry.java`:

```java
package arena.bots;

import arena.bots.baseline.RandomBot;
import arena.bots.baseline.StraightBot;
import arena.bots.baseline.WallAvoidBot;
import arena.bots.gen.Gen00Bot;

import java.util.Comparator;
import java.util.List;

/**
 * 이름으로 봇을 찾는다.
 *
 * 새 세대를 추가할 때 GENERATIONS에 한 줄을 더한다. 리플렉션으로
 * 자동 탐색하지 않는 이유는 G3가 봇의 리플렉션 사용을 금지하는데
 * 하네스만 예외로 두면 규칙이 흐려지기 때문이다. 명시적인 목록이
 * 무엇이 챔피언 계보에 속하는지도 분명히 한다.
 */
public final class BotRegistry {

    private static final List<Bot> GENERATIONS = List.of(
            new Gen00Bot()
            // 세대가 승격될 때마다 여기에 추가한다.
    );

    private static final List<Bot> BASELINES = List.of(
            new StraightBot(), new RandomBot(), new WallAvoidBot());

    private BotRegistry() {}

    public static Bot byName(String name) {
        return all().stream()
                .filter(b -> b.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "그런 봇이 없다: " + name + "\n등록된 봇: "
                                + all().stream().map(Bot::name).toList()));
    }

    /** Gen 번호 오름차순. 갤러리 패널 순서가 이걸 그대로 따른다. */
    public static List<Bot> allGenerations() {
        return GENERATIONS.stream()
                .sorted(Comparator.comparing(Bot::name))
                .toList();
    }

    public static Bot latestGeneration() {
        List<Bot> generations = allGenerations();
        return generations.get(generations.size() - 1);
    }

    public static List<Bot> baselines() {
        return BASELINES;
    }

    private static List<Bot> all() {
        return java.util.stream.Stream.concat(GENERATIONS.stream(), BASELINES.stream()).toList();
    }
}
```

- [ ] **Step 4: CLI 구현**

`arena-api/build.gradle`:

```groovy
plugins {
    id 'org.springframework.boot'
    id 'io.spring.dependency-management'
}

dependencies {
    implementation project(':arena-core')
    implementation project(':arena-bots')
    implementation project(':arena-diagnostics')
    implementation project(':arena-gate')
    implementation project(':arena-tournament')
    implementation 'org.springframework.boot:spring-boot-starter'
}
```

`arena-api/src/main/java/arena/api/Seeds.java`:

```java
package arena.api;

import java.util.List;
import java.util.stream.LongStream;

/**
 * 시드 집합.
 *
 * 심사와 홀드아웃을 범위로 갈라 두어 겹칠 여지를 없앴다.
 * 홀드아웃은 에이전트에게 노출하지 않는다 — 두 승률의 격차가
 * 시드 과적합의 정도다.
 */
public final class Seeds {

    public static final List<Long> JUDGING = range(1, 50);
    public static final List<Long> HOLDOUT = range(1001, 1050);
    public static final List<Long> ROUND_ROBIN = range(1, 10);

    /** 갤러리는 전 세대가 같은 시드를 써야 패널끼리 비교된다. */
    public static final long GALLERY = 1L;

    public static final int WIDTH = 30;
    public static final int HEIGHT = 30;

    private Seeds() {}

    private static List<Long> range(long from, long to) {
        return LongStream.rangeClosed(from, to).boxed().toList();
    }
}
```

`arena-api/src/main/java/arena/api/cli/GateCommand.java`:

```java
package arena.api.cli;

import arena.api.Seeds;
import arena.bots.Bot;
import arena.bots.BotRegistry;
import arena.gate.GateContext;
import arena.gate.GateReport;
import arena.gate.GateRunner;
import arena.tournament.RecordStore;

import java.nio.file.Path;

/** 관문 G2~G7을 돌린다. G1(컴파일)은 Gradle이 이미 판정했다. */
public final class GateCommand {

    private GateCommand() {}

    public static int run(String botName) {
        Bot bot = BotRegistry.byName(botName);

        GateReport report = GateRunner.run(new GateContext(
                bot, bot.getClass(), Seeds.WIDTH, Seeds.HEIGHT, Seeds.JUDGING));

        if (report.passed()) {
            System.out.println("통과 — " + botName + "이 관문 G2~G7을 모두 넘었다");
            return 0;
        }

        System.out.println("반려 — " + report.failedGate());
        System.out.println(report.detail());

        int generation = generationOf(botName);
        if (generation >= 0) {
            RecordStore store = new RecordStore(Path.of("records"));
            store.saveGateReport(generation, store.nextAttempt(generation),
                    readSourceOrPlaceholder(botName), report);
        }
        return 1;
    }

    /** "Gen07Bot" → 7. 세대 봇이 아니면 -1. */
    static int generationOf(String botName) {
        if (!botName.startsWith("Gen") || !botName.endsWith("Bot")) return -1;
        try {
            return Integer.parseInt(botName.substring(3, botName.length() - 3));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String readSourceOrPlaceholder(String botName) {
        Path source = Path.of("arena-bots/src/main/java/arena/bots/gen", botName + ".java");
        try {
            return java.nio.file.Files.readString(source);
        } catch (java.io.IOException e) {
            return "// 소스를 읽지 못했다: " + source;
        }
    }
}
```

`arena-api/src/main/java/arena/api/cli/ChallengeCommand.java`:

```java
package arena.api.cli;

import arena.api.Seeds;
import arena.bots.Bot;
import arena.bots.BotRegistry;
import arena.tournament.ChallengeReport;
import arena.tournament.Championship;
import arena.tournament.DiagnosisEntry;
import arena.tournament.RecordStore;

import java.nio.file.Path;

/** 현 챔피언과 100경기를 붙여 승격 여부를 판정한다. */
public final class ChallengeCommand {

    private ChallengeCommand() {}

    public static int run(String botName) {
        Bot challenger = BotRegistry.byName(botName);
        Bot champion = BotRegistry.latestGeneration();

        if (challenger.name().equals(champion.name())) {
            System.out.println("도전자가 현 챔피언과 같다: " + botName);
            return 2;
        }

        ChallengeReport report = Championship.judge(
                challenger, champion, Seeds.JUDGING, Seeds.HOLDOUT, Seeds.WIDTH, Seeds.HEIGHT);

        System.out.printf("승점 승률 %.3f (기준 %.2f) — 승 %d 무 %d 패 %d%n",
                report.scoreRate(), report.threshold(),
                report.wins(), report.draws(), report.losses());

        if (report.promoted()) {
            System.out.printf("승격 — 홀드아웃 승률 %.3f (격차 %.3f)%n",
                    report.holdoutScoreRate(),
                    report.scoreRate() - report.holdoutScoreRate());
        } else {
            System.out.println("반려 — 손실이 가장 컸던 수:");
            for (DiagnosisEntry d : report.diagnosis()) {
                System.out.printf("  시드 %d 턴 %d: %s를 골랐다 (최선은 %s). "
                                + "닿을 수 있는 칸이 %d → %d로 %d칸 줄었다%n",
                        d.seed(), d.turn(), d.chose(), d.best(),
                        d.reachIfBest(), d.reachChosen(), d.loss());
            }
        }

        int generation = GateCommand.generationOf(botName);
        if (generation >= 0) {
            RecordStore store = new RecordStore(Path.of("records"));
            store.saveChallengeReport(generation, store.nextAttempt(generation), report);
        }
        return report.promoted() ? 0 : 1;
    }
}
```

`arena-api/src/main/java/arena/api/cli/RecordCommand.java`:

```java
package arena.api.cli;

import arena.api.Seeds;
import arena.bots.BotRegistry;
import arena.tournament.BundleBuilder;
import arena.tournament.RecordStore;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Comparator;

/**
 * 발표 번들을 만든다. --verify는 전체를 다시 만들어 내용을 대조한다.
 *
 * 결정론이 지켜지고 있다면 두 번 만든 번들은 바이트 단위로 같다.
 * 발표에서 R1을 주장할 때 이 명령의 출력이 곧 증거다.
 */
public final class RecordCommand {

    private RecordCommand() {}

    public static int run(boolean verifyOnly) {
        return runInto(Path.of("records"), Path.of("web/public/data"), verifyOnly);
    }

    public static int runInto(Path recordsDir, Path outputDir, boolean verifyOnly) {
        if (!verifyOnly) {
            buildInto(recordsDir, outputDir);
            System.out.println("발표 번들 생성 완료: " + outputDir);
            return 0;
        }

        String before = digestOf(outputDir);
        Path scratch = outputDir.resolveSibling(outputDir.getFileName() + "-verify");
        buildInto(recordsDir, scratch);
        String after = digestOf(scratch);
        deleteRecursively(scratch);

        if (before.equals(after)) {
            System.out.println("재현 검증 통과 — 번들 해시 " + before);
            return 0;
        }
        System.out.println("재현 검증 실패 — R1이 깨졌다");
        System.out.println("  기존 " + before);
        System.out.println("  재생성 " + after);
        return 1;
    }

    private static void buildInto(Path recordsDir, Path outputDir) {
        BundleBuilder.build(
                BotRegistry.allGenerations(),
                BotRegistry.latestGeneration(),
                Seeds.GALLERY,
                Seeds.JUDGING,
                Seeds.WIDTH, Seeds.HEIGHT,
                new RecordStore(recordsDir),
                outputDir);
    }

    /** 번들 전체를 파일명 순으로 이어붙여 해시한다. */
    private static String digestOf(Path dir) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (var files = Files.list(dir)) {
                files.sorted(Comparator.comparing(Path::getFileName))
                        .forEach(p -> {
                            try {
                                md.update(p.getFileName().toString().getBytes());
                                md.update(Files.readAllBytes(p));
                            } catch (IOException e) {
                                throw new UncheckedIOException(e);
                            }
                        });
            }
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest()) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("번들 해시를 낼 수 없다: " + dir, e);
        }
    }

    private static void deleteRecursively(Path dir) {
        try (var paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException("임시 디렉터리를 지울 수 없다: " + dir, e);
        }
    }
}
```

`arena-api/src/main/java/arena/api/ArenaApplication.java`:

```java
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

        int code = switch (args[0]) {
            case "gate"      -> GateCommand.run(requireArg(args, "gate <BotName>"));
            case "challenge" -> ChallengeCommand.run(requireArg(args, "challenge <BotName>"));
            case "record"    -> RecordCommand.run(args.length > 1 && args[1].equals("--verify"));
            default -> {
                System.out.println("알 수 없는 명령: " + args[0]);
                yield 2;
            }
        };
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
```

- [ ] **Step 5: Gradle 태스크 추가**

루트 `build.gradle` 맨 끝에 추가한다:

```groovy
// 에이전트가 쓰는 명령은 이 넷뿐이다.
['gate', 'challenge'].each { name ->
    tasks.register(name, JavaExec) {
        group = 'arena'
        description = "${name} — 봇 이름을 --bot=Gen07Bot 으로 준다"
        dependsOn ':arena-api:classes'
        classpath = project(':arena-api').sourceSets.main.runtimeClasspath
        mainClass = 'arena.api.ArenaApplication'
        args = [name, project.findProperty('bot') ?: '']
    }
}

tasks.register('record', JavaExec) {
    group = 'arena'
    description = 'record — 발표 번들 생성. --verify 로 재현 검증'
    dependsOn ':arena-api:classes'
    classpath = project(':arena-api').sourceSets.main.runtimeClasspath
    mainClass = 'arena.api.ArenaApplication'
    args = project.hasProperty('verify') ? ['record', '--verify'] : ['record']
}
```

- [ ] **Step 6: 테스트 통과 확인**

Run: `./gradlew :arena-bots:test :arena-api:test`
Expected: 6 tests PASS

동작 확인:

```bash
./gradlew gate -Pbot=Gen00Bot
```
Expected: Gen00Bot은 직진봇이라 **G7에서 반려된다.** 이게 정상이다 — Gen 0은 관문 대상이 아니며, 루프는 Gen 1부터 돈다.

```bash
./gradlew record
```
Expected: `web/public/data/`에 JSON 넷이 생긴다.

```bash
./gradlew record -Pverify
```
Expected: `재현 검증 통과 — 번들 해시 …`

- [ ] **Step 7: 커밋**

```bash
git add arena-bots/ arena-api/ build.gradle
git commit -m "$(cat <<'EOF'
feat: 하네스 CLI — gate, challenge, record

에이전트가 만지는 표면은 이 셋뿐이다. 봇 코드를 쓰고 이것만
실행한다.

record --verify는 번들을 다시 만들어 해시를 대조한다. 결정론이
지켜지고 있다면 두 번 만든 번들은 바이트 단위로 같다. 발표에서
R1을 주장할 때 이 명령의 출력이 곧 증거다.

BotRegistry는 명시적 목록을 쓴다. G3가 봇의 리플렉션을 금지하는데
하네스만 예외로 자동 탐색을 쓰면 규칙이 흐려진다.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 20: CLAUDE.md — 하네스의 문서 쪽 얼굴

C1은 규칙과 관문이 **문서와 코드**로 존재할 것을 요구한다. 코드 쪽은 `arena-gate`이고, 문서 쪽이 이것이다.

**Files:**
- Create: `CLAUDE.md`

**Interfaces:**
- Consumes: 앞선 모든 태스크에서 확정된 규칙
- Produces: 없음 (문서)

- [ ] **Step 1: 규칙서 작성**

`CLAUDE.md`:

````markdown
# 봇 작성 규칙서

이 저장소에서 새 세대 봇을 쓸 때 지켜야 할 계약이다.
**여기 적힌 모든 규칙은 관문이 기계로 판정한다.** 사람의 판단이 개입하는 항목은 없다.

## 봇의 계약

```java
public interface Bot {
    String name();
    Direction move(GameView view);
}
```

`arena-bots/src/main/java/arena/bots/gen/Gen<NN>Bot.java`에 만들고,
`BotRegistry.GENERATIONS`에 한 줄을 추가한다.

## 반드시 지킬 것

| | 규칙 | 판정 |
|---|---|---|
| 1 | **인스턴스 필드를 갖지 않는다.** 봇은 무상태 순수 함수다 | G2 |
| 2 | **아래 API를 쓰지 않는다** | G3 |
| 3 | **어떤 국면에서도 예외를 던지지 않고 null을 반환하지 않는다** | G4 |
| 4 | **같은 입력에는 항상 같은 출력을 낸다** | G5 |
| 5 | **한 수를 p99 5ms 안에 결정한다** | G6 |
| 6 | **베이스라인 3종에게 한 번도 지지 않는다** | G7 |

### 금지 API (규칙 2)

```
시드 없는 난수   Math.random(), new Random()      // new Random(seed)는 허용
시계             System.currentTimeMillis/nanoTime, java.time.*
동시성           Thread, ExecutorService, ForkJoinPool
외부 세계        java.io.*, java.nio.file.*, java.net.*
전역 가변 상태   non-final static 필드
우회             java.lang.reflect.*, Unsafe, Object.hashCode()
```

`private static final` 상수는 허용한다.

## 제출 절차

```bash
./gradlew gate      -Pbot=Gen07Bot   # 관문 G2~G7
./gradlew challenge -Pbot=Gen07Bot   # 챔피언전 100경기
```

`gate`를 통과해야 `challenge`를 돌릴 수 있다.
`challenge`에서 **승점 승률 60% 이상**이면 승격이다.

## 알아둘 것

- **직전 챔피언을 출발점으로 증분 개선한다.** 백지에서 다시 쓰지 않는다. 세대별 diff가 작고 선명해야 "이번 세대는 무엇을 배웠는가"가 한 문장으로 설명된다
- **심사 시드는 1~50이다.** 이 시드들에서만 통하는 수를 짜지 말 것. 별도의 홀드아웃 시드로 검증하며, 두 승률의 격차는 기록에 남는다
- **자멸은 반려 사유가 아니다.** 다만 자멸률은 세대별로 측정되어 기록된다
- **반려당한 코드는 지우지 않는다.** `records/gen-NN/attempt-M/`에 그대로 남는다
- **한 세대에 5회까지 시도할 수 있다.** 초과하면 실험을 종료하고 수렴으로 선언한다

## 기준값에 대해

관문 기준값과 승격 기준 60%는 **통과하지 못한다는 이유로 낮추지 않는다.**
통과하지 못하면 봇이 부족한 것이다.

## 관문을 추가할 때

관문을 새로 만들면 **그 관문을 일부러 위반하는 함정 봇을 함께 추가한다**
(`arena-gate/src/test/java/arena/gate/traps/`). 관문이 정말 잡아내는지 증명되지
않은 관문은 관문이 아니다.

그리고 **이 문서에도 규칙을 반영한다.** 문서와 관문이 서로 다른 것을 말하기
시작하면 하네스가 무너진다.
````

- [ ] **Step 2: 규칙서가 관문과 일치하는지 확인**

Run:
```bash
grep -o 'G[2-7]' CLAUDE.md | sort -u
```
Expected: `G2 G3 G4 G5 G6 G7` — 관문 여섯이 모두 문서에 언급된다

Run:
```bash
./gradlew :arena-gate:test --tests '*GateRunnerTest*'
```
Expected: PASS — 문서가 말하는 관문이 실제로 동작한다

- [ ] **Step 3: 커밋**

```bash
git add CLAUDE.md
git commit -m "$(cat <<'EOF'
docs: 봇 작성 규칙서

C1은 규칙과 관문이 문서와 코드로 존재할 것을 요구한다. 코드 쪽은
arena-gate이고 문서 쪽이 이것이다.

문서에 적힌 모든 규칙은 관문이 기계로 판정한다. 사람의 판단이
개입하는 항목은 하나도 없다.

관문을 추가할 때 함정 봇과 이 문서를 함께 고치도록 명시했다.
문서와 관문이 서로 다른 것을 말하기 시작하면 하네스가 무너진다.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 21: 전체 통합 확인

**Files:**
- Test: `arena-api/src/test/java/arena/api/HarnessSmokeTest.java`

**Interfaces:**
- Consumes: 앞선 모든 태스크
- Produces: 없음 (검증만)

- [ ] **Step 1: 통합 테스트 작성**

`arena-api/src/test/java/arena/api/HarnessSmokeTest.java`:

```java
package arena.api;

import arena.bots.BotRegistry;
import arena.bots.baseline.WallAvoidBot;
import arena.gate.GateContext;
import arena.gate.GateReport;
import arena.gate.GateRunner;
import arena.tournament.ChallengeReport;
import arena.tournament.Championship;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 하네스 전체가 이어져 도는지 확인한다.
 * 개별 단위는 각 모듈의 테스트가 지킨다.
 */
class HarnessSmokeTest {

    @Test
    void 벽회피봇은_관문을_전부_통과한다() {
        var bot = new WallAvoidBot();
        GateReport report = GateRunner.run(new GateContext(
                bot, bot.getClass(), Seeds.WIDTH, Seeds.HEIGHT, Seeds.JUDGING));

        assertTrue(report.passed(),
                report.failedGate() + "에서 막혔다: " + report.detail());
    }

    @Test
    void 관문을_통과한_봇이_Gen0_챔피언을_교체한다() {
        ChallengeReport report = Championship.judge(
                new WallAvoidBot(), BotRegistry.byName("Gen00Bot"),
                Seeds.JUDGING, Seeds.HOLDOUT, Seeds.WIDTH, Seeds.HEIGHT);

        assertTrue(report.promoted(),
                "승점 승률 " + report.scoreRate() + "로 승격에 실패했다");
    }

    @Test
    void 심사와_홀드아웃_시드는_겹치지_않는다() {
        assertTrue(Seeds.JUDGING.stream().noneMatch(Seeds.HOLDOUT::contains));
    }

    @Test
    void 승격_기준과_시간_상한이_스펙과_일치한다() {
        assertEquals(0.60, Championship.PROMOTION_THRESHOLD, 1e-9);
        assertEquals(5.0, GateRunner.P99_LIMIT_MILLIS, 1e-9);
        assertEquals(10_000, GateRunner.SAMPLE_SIZE);
        assertEquals(100, Seeds.JUDGING.size() * 2, "심사는 100경기여야 한다");
    }
}
```

- [ ] **Step 2: 전체 테스트 실행**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL, 모든 모듈의 테스트 PASS

- [ ] **Step 3: 재현 검증 두 번 돌리기**

Run:
```bash
./gradlew record
./gradlew record -Pverify
```
Expected: `재현 검증 통과 — 번들 해시 …`

한 번 더 돌려도 같은 해시가 나와야 한다. **다르면 R1이 깨진 것이므로 멈추고 원인을 찾는다.**

- [ ] **Step 4: 커밋**

```bash
git add arena-api/
git commit -m "$(cat <<'EOF'
test: 하네스 통합 스모크 테스트

관문 통과부터 승격 판정까지 전체가 이어져 도는지 확인한다.
스펙에 적힌 수치(승격 60%, p99 5ms, 국면 10,000개, 심사
100경기)가 코드와 일치하는지도 테스트로 고정했다.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

## 자체 검토 결과

계획을 스펙과 대조해 확인한 것과 고친 것.

**1. 스펙 커버리지**

| 스펙 절 | 담당 태스크 |
|---|---|
| §2 게임 규칙 · 턴 판정 | Task 1, 2, 3, 5, 6 |
| §2.2 좌석 교대 | Task 14 |
| §3 봇 계약 | Task 4 |
| §4 관문 G1~G7 | Task 9~15 (G1은 Gradle) |
| §4.5 CLAUDE.md | Task 20 |
| §5 루프 · CLI | Task 19 |
| §6 승격 심사 | Task 16 |
| §7 진단기 | Task 8 |
| §8 기록 · 번들 · 재현 검증 | Task 7, 17, 18, 19 |
| §10 모듈 구조 | Task 1 이후 전체 |
| §11 테스트 전략 T1~T4 | Task 6, 9, 15, 8, 21 |
| §12 베이스라인 · Gen 0 | Task 4, 16 |
| §13 파라미터 | Task 21에서 테스트로 고정 |

**§11 T5(프론트엔드 스키마 계약 테스트)는 이 계획에 없다.** 프론트엔드가 없기 때문이며, 계획 2(시각화)에서 다룬다.

**2. 계획 중 발견해 고친 것**

- **`arena-core`가 `arena-bots`에 의존할 뻔했다.** 엔진이 `Bot` 인터페이스를 받으면 의존이 역류한다. `BotFunction` 함수형 인터페이스를 도입해 끊었다 (Task 5)
- **`arena-gate`가 `arena-tournament`에 의존할 뻔했다.** G7이 시리즈 대전을 필요로 하는데 그게 tournament에 있으면 형제 모듈 간 의존이 생긴다. `SeriesRunner`를 `arena-core`로 옮겼다 (Task 14)
- **`NondeterministicTrap`이 `System.identityHashCode`를 쓰면 G3에서 먼저 걸린다.** G5를 시험할 수 없게 되므로 `new Object().hashCode()`로 바꿨다. 단 `Object.hashCode`도 G3 금지 목록에 있어 여전히 G3가 먼저 잡을 수 있다 — **Task 15의 `NondeterministicTrap은_G5에서_걸린다`가 G3를 반환하며 실패하면, `Object.hashCode`를 G3 금지 목록에서 빼고 G5에게 맡긴다.** 결정론 위반은 G5의 책임이고, G3는 R1을 깨는 *호출*을 막는 역할이다
- **스펙 §7.1의 `reachBefore`/`reachAfter`가 오해를 부른다.** 실제 의미는 최선 대안과 실제 선택의 reach다. 코드에서 `reachIfBest`/`reachChosen`으로 명명했고, **계획 완료 후 스펙 §7.1을 이 이름으로 갱신해야 한다**

**3. 타입 일관성 확인**

- `Grid`의 스냅샷은 전 구간에서 `[y][x]` 순서 (Task 2에서 테스트로 고정)
- `Standing.of(replays, subjectId)`가 좌석 교대를 이름으로 귀속 — Task 15, 16, 18에서 동일 시그니처
- `MoveAnalysis.reachAfterBest`/`reachAfterChosen` (진단 모듈) → `DiagnosisEntry.reachIfBest`/`reachChosen` (대전 모듈)로 매핑. Task 16에서 변환
- `GateRunner.SAMPLE_SIZE`, `GateRunner.P99_LIMIT_MILLIS`, `Championship.PROMOTION_THRESHOLD`가 유일한 수치 출처. Task 21이 스펙 값과 일치하는지 검증

---

## 계획 2·3 예고

이 계획이 끝나면 하네스가 완성되고 `Gen00Bot` 하나가 심겨 있다. 남은 둘은 별도 계획으로 다룬다.

- **계획 2 — 시각화.** Next.js + Canvas로 화면 6종. 입력은 이 계획이 만드는 `web/public/data/` 번들이다
- **계획 3 — 세대 루프 실행.** Gen 1부터 12까지 실제로 돌린다. 코드 작성이 아니라 하네스를 **운영**하는 절차이므로, 하네스가 완성되어 실제 반려 양상을 본 뒤에 쓰는 것이 맞다
