package arena.bots;

import arena.core.Direction;
import arena.core.GameView;
import org.junit.jupiter.api.Test;

import java.util.List;

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

    // --- 정렬: 현재 등록된 세대가 하나뿐이라 실제 GENERATIONS로는 순서
    // 역전을 재현할 수 없다. sortByGeneration을 직접 찔러 문자열 정렬이
    // 아니라 숫자 정렬임을 등록 개수와 무관하게 고정한다 ---

    @Test
    void 세대_정렬은_문자열이_아니라_숫자_순서를_따른다() {
        // 문자열 정렬이었다면 "Gen100Bot" < "Gen20Bot" < "Gen9Bot"
        // ("1" < "2" < "9") — 숫자 정렬이면 9 < 20 < 100이다. 등록
        // 순서(리스트 순서)도 일부러 숫자 순서와 다르게 섞는다.
        Bot gen100 = fakeBot("Gen100Bot");
        Bot gen9 = fakeBot("Gen9Bot");
        Bot gen20 = fakeBot("Gen20Bot");

        List<Bot> sorted = BotRegistry.sortByGeneration(List.of(gen100, gen9, gen20));

        assertEquals(List.of("Gen9Bot", "Gen20Bot", "Gen100Bot"),
                sorted.stream().map(Bot::name).toList());
    }

    @Test
    void allGenerations은_등록된_유일한_세대를_그대로_돌려준다() {
        var generations = BotRegistry.allGenerations();

        assertEquals(2, generations.size(), "등록된 세대 수가 바뀌었다면 이 테스트도 같이 갱신한다");
        assertEquals("Gen00Bot", generations.get(0).name());
        assertEquals("Gen01Bot", generations.get(1).name());
    }

    // --- 최신 세대: get(size-1)과 latestGeneration()을 서로 비교하면
    // latestGeneration()의 정의 그 자체와 비교하는 동어반복이 된다.
    // highestGeneration을 직접 찔러 "숫자가 가장 큰 세대"라는 실제
    // 계약을, 리스트 순서와 무관하게 고정한다 ---

    @Test
    void 최신_세대는_등록_순서와_무관하게_숫자가_가장_큰_세대다() {
        Bot gen1 = fakeBot("Gen1Bot");
        Bot gen20 = fakeBot("Gen20Bot");
        Bot gen9 = fakeBot("Gen9Bot");

        // 숫자로 가장 큰 gen20을 리스트 맨 앞도 맨 뒤도 아닌 중간에 둔다 —
        // get(size-1) 같은 "마지막 원소" 우연 일치를 배제한다.
        Bot latest = BotRegistry.highestGeneration(List.of(gen1, gen20, gen9));

        assertEquals("Gen20Bot", latest.name());
    }

    @Test
    void latestGeneration은_실제_등록된_최신_세대를_돌려준다() {
        assertEquals("Gen01Bot", BotRegistry.latestGeneration().name());
    }

    // --- 챔피언 선택: 도전자보다 한 세대 낮은 최고 세대가 챔피언이다 ---

    @Test
    void championFor는_도전자보다_한_세대_낮은_최고_세대를_고른다() {
        Bot g0 = fakeBot("Gen00Bot");
        Bot g1 = fakeBot("Gen01Bot");
        Bot g2 = fakeBot("Gen02Bot");
        List<Bot> gens = List.of(g0, g1, g2);
        assertEquals("Gen01Bot", BotRegistry.championFor(g2, gens).name());
        assertEquals("Gen00Bot", BotRegistry.championFor(g1, gens).name());
    }

    @Test
    void championFor는_Gen0_아래_챔피언이_없으면_거부한다() {
        Bot g0 = fakeBot("Gen00Bot");
        assertThrows(IllegalArgumentException.class,
                () -> BotRegistry.championFor(g0, List.of(g0)));
    }

    // --- 등록 검증: 클래스 초기화가 강제하는 세 규칙을 임의의 목록으로 직접 찌른다 ---

    @Test
    void 유효한_등록은_거부되지_않는다() {
        assertDoesNotThrow(() -> BotRegistry.validate(
                List.of(fakeBot("Gen01Bot")), List.of(fakeBot("StraightBot"))));
    }

    @Test
    void 세대_이름이_GenNNBot_형식이_아니면_등록을_거부한다() {
        Bot malformed = fakeBot("Gen00");  // "Bot" 접미사가 없다

        var e = assertThrows(IllegalStateException.class,
                () -> BotRegistry.validate(List.of(malformed), List.of()));
        assertTrue(e.getMessage().contains("Gen00"), e.getMessage());
    }

    @Test
    void 세대와_베이스라인의_이름이_겹치면_등록을_거부한다() {
        // byName의 findFirst()는 세대를 베이스라인보다 먼저 본다 — 이름이
        // 겹치면 --bot=<그 이름>이 어느 쪽을 가리키는지 등록 순서에
        // 조용히 좌우된다. 그래서 겹침 자체를 등록 시점에 막는다.
        Bot generation = fakeBot("Gen07Bot");
        Bot baseline = fakeBot("Gen07Bot");

        var e = assertThrows(IllegalStateException.class,
                () -> BotRegistry.validate(List.of(generation), List.of(baseline)));
        assertTrue(e.getMessage().contains("Gen07Bot"), e.getMessage());
    }

    @Test
    void 이름에_파이프가_있으면_등록을_거부한다() {
        // ReplayHash의 정규화 문자열은 "|"로 필드를 가른다 — 이름에
        // "|"가 섞이면 필드 경계가 흐려진다(BundleBuilder.buildGallery와
        // DeterminismGate 둘 다 bot.name()을 그대로 Match.play에 넘긴다).
        Bot evil = fakeBot("Evil|Bot");

        var e = assertThrows(IllegalStateException.class,
                () -> BotRegistry.validate(List.of(), List.of(evil)));
        assertTrue(e.getMessage().contains("|"), e.getMessage());
    }

    /** 이름만 통제하는 최소 Bot. move()는 이 테스트들에서 호출되지 않는다. */
    private static Bot fakeBot(String name) {
        return new Bot() {
            @Override
            public String name() { return name; }

            @Override
            public Direction move(GameView view) {
                throw new UnsupportedOperationException("이름 검증 테스트 전용 — move는 호출되지 않아야 한다");
            }
        };
    }
}
