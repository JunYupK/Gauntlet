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
