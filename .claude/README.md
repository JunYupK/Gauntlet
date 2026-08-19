# `.claude/` — 저장소에 고정한 에이전트 하네스

이 디렉터리는 커밋된다. 클론한 사람이 **추가 설치 없이** 같은 스킬로 같은 절차를 밟게 하는 것이 목적이다.

## `skills/`

[obra/superpowers](https://github.com/obra/superpowers)의 스킬 14종을 **그대로 복사해** 프로젝트 스킬로 둔 것이다. MIT 라이선스이며 원본 전문은 `SUPERPOWERS-LICENSE`에 있다.

| | |
|---|---|
| 버전 | `v6.3.0` |
| 커밋 | `b36e0829c6d0140e93cfef2ca599b1b07d4a7797` |
| 가져온 것 | `skills/*` 전부 (파일 내용 무수정) |
| 가져오지 않은 것 | `.claude-plugin/`, `hooks/` — 아래 이유 참조 |

### 스킬 이름에 `superpowers:` 접두사가 없다

프로젝트 스킬은 폴더 이름 그대로 등록된다. `docs/superpowers/plans/`와 각 스킬 문서가 `superpowers:subagent-driven-development`처럼 접두사를 붙여 지목하지만, 이 저장소에서 실제 이름은 **`subagent-driven-development`**다. 접두사를 뗀 나머지가 곧 스킬 이름이라고 읽으면 된다.

접두사를 살리려면 `.claude-plugin/plugin.json`을 둬서 폴더를 플러그인으로 만들어야 하는데, **프로젝트 스코프 플러그인은 워크스페이스 신뢰(trust)를 수락하기 전까지 억제된다.** 매번 새 컨테이너로 뜨는 세션에서는 그 조건이 성립하지 않아 스킬이 하나도 안 뜬다. 접두사보다 "어디서든 무조건 뜬다"를 택했다. 같은 이유로 SessionStart 훅도 빠졌다 — 훅은 플러그인에만 붙는다.

## 업스트림 갱신

저장소가 버전을 들고 있으므로 갱신은 수동이다.

```sh
git clone --depth 1 --branch v<새버전> https://github.com/obra/superpowers /tmp/sp
rm -rf .claude/skills
cp -r /tmp/sp/skills .claude/skills
cp /tmp/sp/LICENSE .claude/SUPERPOWERS-LICENSE
```

위 표의 버전·커밋도 함께 고쳐라.

## 왜 마켓플레이스 선언이 아닌가

`.claude/settings.json`에 `extraKnownMarketplaces`/`enabledPlugins`를 적는 방식은 **선언일 뿐 설치가 아니다.** 설치 기록은 사용자 레벨 `~/.claude/plugins/installed_plugins.json`에 프로젝트 경로별로 남고 저장소를 따라가지 않는다. 세 방식을 실제로 재어 본 결과는 `log.md` D34에 있다.
