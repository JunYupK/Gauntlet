# Bot Arena

봇들이 정해진 규칙으로 겨루고, 세대를 거치며 **코드 재작성**으로 개선되는 시스템.
사내 CoP 발표용 데모이고, 전하려는 메시지는 하나다 —
**"하네스와 루프를 갖추면 개발이 이만큼 빨라진다."**

그래서 이 저장소의 주인공은 최종 봇이 아니라 **만들어진 과정**이다.
잘 짜인 봇을 보여주는 자리가 아니라, 봇을 기계가 판정하고 자동으로
재시도시키는 **하네스·루프**를 보여주는 자리다. (배경은 [`BRIEF.md`](BRIEF.md))

## 종목

**Tron 라이트사이클.** 두 봇이 30×30 격자에서 매 턴 한 칸씩 움직이며 지나온
칸을 벽으로 남긴다. 벽·격자 밖·상대와의 정면 충돌에 먼저 걸리는 쪽이 진다.
최대 900턴(`width * height`)이며, 두 봇의 이번 턴 선택은 **동시에** 확정된다 —
어느 봇도 상대의 이번 수를 미리 볼 수 없다(`arena-core`의 `Match`·`GameView`).

## 이 프로젝트가 지키는 것 (요구사항 R1~R4)

| | 요구사항 | 어떻게 만족하나 |
|---|---|---|
| R1 | **재현 가능성** — 같은 시드·같은 봇이면 같은 바이트 | 대전 중에는 어떤 시간·난수 판정도 섞지 않는다. 시간 측정은 관문 G6 한 곳뿐 |
| R2 | **기계 판정 가능성** — 모든 합격 기준을 코드가 O/X로 낸다 | 관문 G2~G7이 전부 기계 판정. 사람의 눈이 개입하는 관문은 없다 |
| R3 | **육안 식별 가능성** — 초기 봇과 최종 봇의 차이를 설명 없이 지목 | 개선 곡선(생존 턴 10× 이상)과 12패널 갤러리로 그림으로 전달 |
| R4 | **발표 중 안정성** — 라이브 의존 없이 발표 당일이 돈다 | 프론트엔드는 백엔드·네트워크 없는 정적 export |

## 모듈 구조

Gradle 멀티모듈(Java 21). 의존은 **단방향**이다 — 왼쪽이 오른쪽을 모른다.

```
arena-core  →  arena-bots  →  arena-diagnostics  →  arena-gate  →  arena-tournament  →  arena-api
  엔진·규칙       봇 계약·구현        턴별 진단 지표         관문 G2~G7        챔피언전·번들 생성       CLI 진입점
```

프론트엔드는 별도로 `web/`에 있다(Next.js 정적 export).

## 봇의 계약

봇은 **무상태 순수 함수**다. 같은 입력에는 항상 같은 출력을 낸다.

```java
public interface Bot {
    String name();
    Direction move(GameView view);
}
```

`GameView`가 무엇을 들려주는지(내 머리·방향, 상대 머리·방향, 벽, 턴, 격자 크기)는
`arena-core/.../GameView.java`를 직접 본다. 새 세대는
`arena-bots/.../gen/Gen<NN>Bot.java`에 만들고 `BotRegistry.GENERATIONS`에 한 줄을
더한다. 규칙·관문의 상세 계약은 [`CLAUDE.md`](CLAUDE.md) §5~§12가 유일한 기준이다.

## 관문 G2~G7

제출된 봇은 `G2 → G7` 순서로 판정되고 **첫 실패에서 멈춘다**. G1(컴파일)은
Gradle이 여기 닿기 전에 이미 판정한다.

| | 규칙 | 관문 |
|---|---|---|
| G2 | 인스턴스 필드를 갖지 않는다 (무상태) | `StatelessGate` |
| G3 | 금지 API·가변 static 필드를 쓰지 않는다 | `ForbiddenApiGate` |
| G4 | 어떤 국면에서도 예외를 던지거나 null을 반환하지 않는다 | `LegalMoveGate` |
| G5 | 같은 입력에는 항상 같은 출력 (결정론) | `DeterminismGate` |
| G6 | 한 수를 p99 5ms 안에 결정한다 | `TimeBudgetGate` |
| G7 | 베이스라인 3종에게 패배 0회 | `RegressionGate` |

**관문 기준값은 루프가 통과하지 못한다는 이유로 낮추지 않는다.** 통과 못 하면
봇이 부족한 것이다(BRIEF §11-4).

## 실행

```bash
./gradlew gate      -Pbot=Gen00Bot   # 관문 G2~G7 판정
./gradlew challenge -Pbot=Gen00Bot   # 현 챔피언과 챔피언전(승점 승률 60% 이상이면 승격)
./gradlew record                     # 발표용 진짜 번들 생성 (web/public/data)
./gradlew record -Pverify            # 재현 검증 — 다시 만든 번들이 바이트 일치하는지
./gradlew fixture                    # 데모 번들 생성 (web/fixtures/data, 커밋됨)
./gradlew test                       # 전체 테스트
```

### 종료 코드를 Gradle 종료 코드로 읽지 말 것

CLI는 종료 코드 넷을 구분한다 — **0** 성공 / **1** 판정 거부(관문 반려·승격
실패·재현 실패) / **2** 호출 오류 / **3** 하네스 오류. 그런데 Gradle의
`JavaExec`은 0이 아닌 코드를 전부 빌드 실패로 뭉갠다. 그래서 진짜 코드는
표준 출력 **마지막 줄**에 다시 실린다:

```
ARENA_EXIT_CODE=<0|1|2|3>
```

Gradle 태스크 자체는 **하네스 오류(3)일 때만** 실패한다 — 관문 반려(1)는 세대
루프의 정상적인 결과이지 빌드 고장이 아니기 때문이다. 스크립트는 이렇게 읽는다:

```bash
CODE=$(./gradlew gate -Pbot=Gen00Bot | grep -o 'ARENA_EXIT_CODE=[0-3]' | tail -1 | cut -d= -f2)
```

`ARENA_EXIT_CODE` 줄이 **아예 없으면** 그것도 하네스가 죽은 것으로 읽는다(줄을
찍는 것이 CLI 자신이라, 거기 닿기 전에 JVM이 죽으면 줄이 안 나온다). 자세한
근거는 [`CLAUDE.md`](CLAUDE.md) §8.

## 프론트엔드 (`web/`)

하네스가 만든 정적 번들을 여섯 화면으로 그리는 **Next.js 정적 export**다
(`output: 'export'`). 백엔드도 네트워크도 없이 발표 당일이 돈다(R4).

화면 순서는 결과 먼저, 과정 나중(`lib/screens.ts`):

1. **세대 갤러리** — R3의 증거. 여러 세대의 경기를 한눈에
2. **개선 곡선** — 세대별 생존 턴의 상승 (R3 배율 10× 이상)
3. **루프 타임라인** — 발표의 주인공. 관문 반려·챔피언전 반려·승격이 세대마다
4. **세대별 코드 diff** — 무엇을 바꿔 이겼는가
5. **단일 경기 + 진단** — 리플레이 재생과 턴별 지표
6. **히트맵과 과적합 격차** — 순환 우위, 심사/홀드아웃 승률 차이

```bash
cd web
npm install
npm run dev          # 개발 서버 (데모 번들)
npm run build        # 발표용 진짜 번들 (ARENA_BUNDLE=public/data, ./gradlew record 필요)
npm run build:demo   # 데모 번들 (ARENA_BUNDLE=fixtures/data). 화면 맨 위에 주황 "데모 번들" 띠
npm test             # 순수 함수 단위 테스트 (Vitest)
npm run test:e2e     # 정적 export 스모크 (Playwright, 콘솔 오류 0건)
```

`ARENA_BUNDLE`엔 **기본값이 없다** — 못 읽으면 빌드를 그 자리에서 죽인다. 발표용
빌드가 값을 빠뜨린 채 조용히 데모 번들로 나가는 사고를 막기 위해서다.

## 데모 번들이 "가짜"인 부분과 "진짜"인 부분

지금 등록된 세대봇은 `Gen00Bot`(StraightBot 수준) 하나뿐이고, 진짜 세대 루프는
아직 여러 세대를 만들어내지 않았다. 그래서 프론트엔드는 깊이 0~11 벽회피봇으로
만든 **데모 번들**로 화면을 검증한다.

- **진짜인 것**: 경기·리플레이·해시·진단·개선 곡선은 전부 실제 엔진 출력이다.
  생존 턴 10×도 조작이 아니라 측정 결과다.
- **가짜인 것**: "이 봇들이 세대 루프가 만든 것"이라는 부분뿐이다.
  `meta.json`의 `demo` 플래그와 화면 배너가 그것을 밝힌다.

## CI

`.github/workflows/ci.yml`이 PR과 `master` 푸시에서 돈다.

- **Java** — `./gradlew test`에 더해 `record` → `record --verify`로 R1을 검증한다.
  Gradle 종료 코드가 아니라 `ARENA_EXIT_CODE` 줄을 읽고, 줄이 없으면 실패로 친다.
- **web** — 데모 번들을 재생성해 커밋본과의 드리프트를 잡고, `npm test`·
  `build:demo`·Playwright 스모크를 돌린다.

## 문서

| 문서 | 역할 |
|---|---|
| [`BRIEF.md`](BRIEF.md) | 입력. 사람이 갖고 들어온 재료 (설계가 아니다) |
| [`CLAUDE.md`](CLAUDE.md) | 저장소에서 일하는 방법 + 봇 규칙서(§5~§12)의 유일한 기준 |
| [`log.md`](log.md) | 결정 로그. 무엇을 왜 그렇게 정했는지 (기각한 대안도 지우지 않는다) |
| `docs/superpowers/specs/` | 설계 스펙 |
| `docs/superpowers/plans/` | 구현 계획 (태스크·스텝 체크박스) |
