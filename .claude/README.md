# `.claude/` — 저장소에 고정한 에이전트 하네스

이 디렉터리는 커밋된다. 클론한 사람이 추가 설치 없이 같은 스킬로 같은 절차를 밟게 하는 것이 목적이다.

## `skills/superpowers/`

[obra/superpowers](https://github.com/obra/superpowers) 플러그인을 **통째로 벤더링**한 것이다. MIT 라이선스이며 원본 `LICENSE`를 함께 둔다.

| | |
|---|---|
| 버전 | `v6.3.0` |
| 커밋 | `b36e0829c6d0140e93cfef2ca599b1b07d4a7797` |
| 벤더링한 것 | `.claude-plugin/plugin.json`, `skills/`, `hooks/`, `LICENSE` |

프로젝트 스킬 디렉터리 안에 `.claude-plugin/plugin.json`을 둔 폴더는 `superpowers@skills-dir` 플러그인으로 자동 로드된다. 그래서 스킬 이름이 `superpowers:brainstorming`처럼 접두사를 유지하고, `docs/superpowers/plans/`가 지목하는 `superpowers:subagent-driven-development` 같은 지시가 문서 그대로 동작한다.

**첫 세션에서 워크스페이스 신뢰(trust) 대화를 수락해야 로드된다.** 수락하지 않으면 프로젝트 스코프 플러그인은 억제된다.

## 업스트림 갱신

마켓플레이스가 아니라 저장소가 버전을 들고 있으므로 갱신은 수동이다.

```sh
git clone --depth 1 --branch v<새버전> https://github.com/obra/superpowers /tmp/sp
rm -rf .claude/skills/superpowers/{skills,hooks}
cp -r /tmp/sp/{skills,hooks} .claude/skills/superpowers/
cp /tmp/sp/.claude-plugin/plugin.json .claude/skills/superpowers/.claude-plugin/
cp /tmp/sp/LICENSE .claude/skills/superpowers/
```

위 표의 버전·커밋을 함께 고쳐라. `.claude-plugin/marketplace.json`은 **가져오지 않는다** — 이 폴더는 마켓플레이스가 아니라 플러그인이다.

## 왜 마켓플레이스 선언이 아닌가

`.claude/settings.json`에 `extraKnownMarketplaces`/`enabledPlugins`를 적는 방식은 **선언일 뿐 설치가 아니다.** 설치 기록은 사용자 레벨(`~/.claude/plugins/installed_plugins.json`)에 프로젝트 경로별로 남고 저장소를 따라가지 않는다. 실측 근거는 `log.md` D34에 있다.
