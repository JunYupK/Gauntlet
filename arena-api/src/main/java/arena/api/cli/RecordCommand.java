package arena.api.cli;

import arena.api.Seeds;
import arena.bots.BotRegistry;
import arena.core.Replay;
import arena.core.ReplayHash;
import arena.tournament.BundleBuilder;
import arena.tournament.RecordStore;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 발표 번들을 만든다. --verify는 전체를 다시 만들어 내용을 대조한다.
 *
 * 결정론이 지켜지고 있다면 두 번 만든 번들은 바이트 단위로 같다.
 * 발표에서 R1을 주장할 때 이 명령의 출력이 곧 증거다.
 *
 * verify는 서로 다른 것을 잡는 두 겹으로 이뤄진다.
 *
 * ① {@link #verifyReplayHashes}가 **저장돼 있는 그대로의** {@code
 * outputDir/gallery.json}을 실제로 역직렬화해({@code List<Replay>},
 * JsonNode로 얼버무리지 않는다) 각 리플레이의 필드로부터 {@link
 * ReplayHash#of}를 다시 계산해 저장된 hash와 대조한다 — "저장된 해시가
 * 그 리플레이의 필드로부터 실제로 재현되는가"를 잡는다. 새로 지은
 * 번들이 아니라 저장된 번들 자체를 읽는 게 핵심이다: 방금 지은
 * 번들은 같은 코드가 방금 계산한 해시를 그대로 담고 있어 항상
 * 자기 자신과 일치하므로, 그걸 읽으면 "저장된 기록이 실제로 재현
 * 가능한가"라는 질문에 답하지 못한다.
 *
 * ② 새로 지은 번들 전체를 기존 번들과 바이트 단위로 비교한다 —
 * "빌드 전체가 결정론적인가"를 잡는다. ①만으로는 부족하다:
 * roundrobin.json처럼 리플레이가 없는 파일의 드리프트나, {@code
 * matchId}처럼 해시 계산에 안 쓰이는 필드의 드리프트를 놓친다.
 *
 * {@code MatchResult.isDraw()}가 만드는 파생 필드 "draw" 때문에
 * gallery.json을 {@code List<Replay>}로 완전히 역직렬화하면
 * {@code UnrecognizedPropertyException}이 날 수 있었다 — 그래서 이
 * 재검증 자체가 성립하려면 {@link BundleBuilder}·{@link RecordStore}가
 * 쓰는 ObjectMapper에서 그 파생 필드를 애초에 쓰지 않게 고쳐야 했다
 * (두 클래스의 MAPPER javadoc 참고). 그 수정 없이는 이 클래스의 verify가
 * "재검증이 하네스 자신의 버그로 인해 항상 실패한다"는 거짓 반려를
 * 냈을 것이다.
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

        String hashMismatch = verifyReplayHashes(outputDir);
        if (hashMismatch != null) {
            System.out.println("재현 검증 실패 — R1이 깨졌다");
            System.out.println("  " + hashMismatch);
            return 1;
        }

        Path scratch = outputDir.resolveSibling(outputDir.getFileName() + "-verify");
        try {
            buildInto(recordsDir, scratch);

            String before = digestOf(outputDir);
            String after = digestOf(scratch);

            if (!before.equals(after)) {
                System.out.println("재현 검증 실패 — R1이 깨졌다");
                System.out.println("  기존 " + before);
                System.out.println("  재생성 " + after);
                return 1;
            }

            System.out.println("재현 검증 통과 — 번들 해시 " + before);
            return 0;
        } finally {
            deleteRecursively(scratch);
        }
    }

    private static void buildInto(Path recordsDir, Path outputDir) {
        BundleBuilder.build(
                BotRegistry.allGenerations(),
                BotRegistry.latestGeneration(),
                Seeds.GALLERY,
                Seeds.JUDGING,
                Seeds.ROUND_ROBIN,
                Seeds.WIDTH, Seeds.HEIGHT,
                new RecordStore(recordsDir),
                outputDir, false);
    }

    /**
     * gallery.json을 실제로 {@code List<Replay>}로 역직렬화해(JsonNode로
     * 얼버무리지 않는다), 각 리플레이의 필드로부터 {@link ReplayHash#of}를
     * 다시 계산해 저장된 {@code hash}와 대조한다. 전부 일치하면 null,
     * 아니면 사람이 읽을 불일치 설명을 돌려준다.
     */
    private static String verifyReplayHashes(Path bundleDir) {
        Path galleryPath = bundleDir.resolve("gallery.json");
        List<Replay> gallery;
        try {
            gallery = new ObjectMapper().readValue(galleryPath.toFile(), new TypeReference<List<Replay>>() {});
        } catch (IOException e) {
            throw new UncheckedIOException("gallery.json을 읽을 수 없다: " + galleryPath, e);
        }

        for (Replay r : gallery) {
            String recomputed = ReplayHash.of(
                    r.bot0Id(), r.bot1Id(), r.seed(), r.width(), r.height(), r.moves(), r.result());
            if (!recomputed.equals(r.hash())) {
                return "리플레이 " + r.matchId() + "의 해시가 재계산과 다르다"
                        + "\n    저장된 해시  " + r.hash()
                        + "\n    재계산된 해시 " + recomputed;
            }
        }
        return null;
    }

    /**
     * 번들 전체를 상대 경로 순으로 이어붙여 해시한다.
     *
     * {@code Files.list}(비재귀)가 아니라 {@code Files.walk}를 쓴다 —
     * Task 3에서 {@code sources/}가 outputDir 아래 하위 디렉터리로
     * 들어오면서, 비재귀 나열이 그 디렉터리 자체를 "파일"로 집어
     * {@code Files.readAllBytes}에 넘기면 IOException으로 터졌다.
     * 정렬·해시 입력 모두 파일명이 아니라 {@code dir} 기준 상대 경로
     * 문자열을 쓴다 — 그래야 {@code sources/gen-00.java}처럼 이름이
     * 겹칠 수 있는 하위 파일도 구분되고, 디렉터리 구조 자체의 드리프트
     * (파일이 엉뚱한 하위 폴더로 옮겨지는 것)도 해시에 반영된다.
     * 경로 구분자를 "/"로 정규화하는 이유는 이 산출물이 다른 운영체제
     * 에서 다시 만들어 대조돼야 하기 때문이다(R1) — Windows에서
     * {@code Path::toString}은 "\"를 쓴다.
     */
    private static String digestOf(Path dir) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (var files = Files.walk(dir)) {
                // relativePath를 파일당 한 번만 계산해 정렬 키와 해시 입력에
                // 그대로 재사용한다 (Map.Entry로 캐시) — 같은 문자열을 두 번
                // 계산하지 않는다.
                files.filter(Files::isRegularFile)
                        .map(p -> Map.entry(relativePath(dir, p), p))
                        .sorted(Map.Entry.comparingByKey())
                        .forEach(entry -> {
                            try {
                                // 반드시 UTF-8을 명시한다. 플랫폼 기본 문자셋에
                                // 맡기면 같은 번들이 기계마다 다른 해시를 낸다 —
                                // 바이트 동일성이 이 산출물의 존재 이유다.
                                md.update(entry.getKey().getBytes(StandardCharsets.UTF_8));
                                md.update(Files.readAllBytes(entry.getValue()));
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

    /** {@code base} 기준 상대 경로를 "/" 구분자로 정규화한 문자열로 낸다. */
    private static String relativePath(Path base, Path p) {
        return base.relativize(p).toString().replace('\\', '/');
    }

    private static void deleteRecursively(Path dir) {
        if (!Files.exists(dir)) return;
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
