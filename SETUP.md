# SETUP.md — 로컬 맥북에서 돌리기

이 저장소를 로컬(macOS)에서 클론해 하네스·프론트엔드를 그대로 실행하고,
계획 3(세대 루프)까지 이어가기 위한 가이드다. **하네스는 결정론적이라(R1)
로컬 결과가 원격/CI 결과와 바이트 단위로 동일하다** — `record --verify`가
그것을 매번 확인한다.

## 0. 요구사항

| 도구 | 버전 | 왜 |
|---|---|---|
| **JDK 21** | 21 (Temurin 권장) | `build.gradle`의 툴체인이 `languageVersion 21`을 요구. 자동 다운로드 플러그인이 없어 **직접 설치**해야 한다 |
| **Node.js** | 20 LTS 이상 | 프론트엔드가 Next 15 / React 19 |
| **Git** | 아무 최신 | 클론 |
| Gradle | — | **설치 불필요.** 래퍼(`./gradlew`, 8.11.1)가 알아서 받는다 |

Apple Silicon(M1~)·Intel 맥 모두 동일하다.

## 1. 설치

### JDK 21 (둘 중 하나)

```bash
# (A) Temurin — Gradle이 표준 위치에서 자동 인식해서 가장 말썽 없다
brew install --cask temurin@21

# (B) SDKMAN — 여러 JDK를 오갈 거면 이쪽
curl -s "https://get.sdkman.io" | bash && source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk install java 21-tem
```

> `brew install openjdk@21`(cask 아님)은 keg-only라 Gradle이 못 찾을 수 있다.
> 그 경우 `JAVA_HOME`을 직접 잡아준다 — 아래 §5 트러블슈팅.

### Node.js

```bash
brew install node        # 또는 nvm 사용 중이면: nvm install --lts
```

### 확인

```bash
java -version     # openjdk version "21..." 이어야 한다
node -v           # v20 이상
```

## 2. 클론

```bash
git clone https://github.com/JunYupK/Gauntlet
cd Gauntlet
```

## 3. 하네스 실행 (백엔드)

`gradlew`가 Gradle과 의존성을 처음 한 번 내려받는다(몇 분).

```bash
./gradlew test                        # 전체 테스트 (첫 빌드 검증용으로 먼저 돌려보길 권장)
./gradlew gate      -Pbot=Gen00Bot    # 관문 G2~G7 판정
./gradlew challenge -Pbot=Gen00Bot    # 현 챔피언과 챔피언전
./gradlew record                      # 발표용 진짜 번들 생성 (web/public/data)
./gradlew record -Pverify             # 재현 검증 — 다시 만든 번들이 바이트 일치하는지
./gradlew fixture                     # 데모 번들 생성 (web/fixtures/data, 커밋돼 있음)
```

### 종료 코드는 `ARENA_EXIT_CODE` 줄로 읽는다

Gradle의 `JavaExec`은 0이 아닌 코드를 전부 빌드 실패로 뭉갠다. 그래서 CLI가
진짜 코드(**0** 성공 / **1** 판정 거부 / **2** 호출 오류 / **3** 하네스 오류)를
표준 출력 **마지막 줄**에 다시 싣는다:

```bash
CODE=$(./gradlew gate -Pbot=Gen00Bot | grep -o 'ARENA_EXIT_CODE=[0-3]' | tail -1 | cut -d= -f2)
echo "exit=$CODE"
```

`ARENA_EXIT_CODE` 줄이 **아예 없으면** 그것도 하네스가 죽은 것으로 친다(그 줄을
찍는 게 CLI 자신이라, 거기 닿기 전에 JVM이 죽으면 줄이 안 나온다). 자세한
근거는 `CLAUDE.md` §8.

## 4. 프론트엔드 실행 (web)

```bash
cd web
npm install
npx playwright install chromium   # 스모크 테스트(test:e2e)를 돌릴 때만 필요.
                                  # 원격 환경엔 Chromium이 미리 깔려 있지만 로컬은 직접 받아야 한다.

npm run dev          # 개발 서버 (데모 번들, http://localhost:3000)
npm run build        # 발표용 진짜 번들 빌드 (../가 아니라 web/public/data — ./gradlew record 먼저)
npm run build:demo   # 데모 번들 빌드 (화면 맨 위에 주황 "데모 번들" 띠)
npm test             # 순수 함수 단위 테스트 (Vitest)
npm run test:e2e     # 정적 export 스모크 (Playwright, 콘솔 오류 0건)
```

`ARENA_BUNDLE` 환경변수는 각 npm 스크립트 안에 이미 박혀 있어 따로 export할
필요가 없다(`dev`·`build:demo`·`test`는 `fixtures/data`, `build`는 `public/data`).
`@playwright/test`가 캐럿 없이 `1.56.0`으로 고정된 이유는 `web/playwright.config.ts`
상단 주석에 있다(설치되는 Chromium 리비전과의 짝을 깨지 않기 위해서다).

## 5. 계획 3(세대 루프) 이어가기

계획 문서는 저장소에 이미 있다:
`docs/superpowers/plans/2026-08-27-arena-generation-loop.md`

두 가지를 기억한다.

1. **루프는 자율 실행 바이너리가 아니다**(스펙 §5.1). 에이전트가 세대 봇을
   쓰고 `gate`·`challenge`·`record` 넷만 돌린다. 그래서 로컬에서도 **Claude Code를
   깔고** 같은 subagent-driven 방식으로 진행하는 게 자연스럽다:
   ```bash
   # 맥에 Claude Code 설치 후, 저장소에서:
   #   "docs/superpowers/plans/2026-08-27-arena-generation-loop.md 를
   #    subagent-driven-development 로 실행해줘"
   ```
2. 다세대 루프가 끝까지 돌려면 계획 3의 **인프라 3태스크(챔피언 선택·attempt
   연속성·5회 수렴)를 먼저** 구현해야 한다. `gate`로 봇 하나 판정은 지금도 되지만,
   `Gen01Bot`을 챔피언전까지 돌리는 건 그 수정 뒤에 가능하다. 계획 문서에 정확한
   코드까지 적혀 있다.

작업 규칙(커밋은 한국어·왜를 적는다, `log.md`는 append-only, 관문·승격 기준은
낮추지 않는다, 베이스라인 3종 동결 등)은 `CLAUDE.md`가 유일한 기준이다.

## 6. 트러블슈팅

- **`Could not find a Java installation ... languageVersion=21` / 툴체인 오류**
  JDK 21이 안 깔렸거나 Gradle이 못 찾는 것. 설치를 확인하고, keg-only openjdk를
  썼다면 `JAVA_HOME`을 잡아준다:
  ```bash
  # Temurin(cask) 예시 — 실제 경로는 /usr/libexec/java_home 로 확인
  export JAVA_HOME=$(/usr/libexec/java_home -v 21)
  ./gradlew --version   # JVM 이 21 인지 확인
  ```
- **Playwright "executable doesn't exist"** — `npx playwright install chromium`을
  안 돌렸거나 버전이 어긋난 것. `@playwright/test`는 `1.56.0` 고정이므로 그대로 두고
  브라우저만 받으면 된다.
- **`./gradlew`가 `BUILD SUCCESSFUL`인데 봇이 거부됨** — 정상이다. Gradle 태스크는
  하네스 오류(3)일 때만 실패하고, 관문 반려(1)·호출 오류(2)는 초록 불로 끝난다.
  반드시 `ARENA_EXIT_CODE` 줄을 읽어라(§3).
- **첫 `./gradlew`가 느리다** — Gradle 배포판과 의존성을 처음 받는 것. 한 번만 그렇다.

더 넓은 그림(모듈 구조·관문·요구사항)은 `README.md`, 봇 작성 계약은
`CLAUDE.md` §5~§12를 본다.
