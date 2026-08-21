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
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.List;

/**
 * 발표 번들을 만든다. --verify는 전체를 다시 만들어 내용을 대조한다.
 *
 * 결정론이 지켜지고 있다면 두 번 만든 번들은 바이트 단위로 같다.
 * 발표에서 R1을 주장할 때 이 명령의 출력이 곧 증거다.
 *
 * verify는 두 겹으로 재검증한다: ① gallery.json을 실제로 역직렬화해
 * ({@code List<Replay>}, JsonNode로 얼버무리지 않는다) 각 리플레이의
 * 필드로부터 {@link ReplayHash#of}를 다시 계산해 저장된 hash와 대조하고,
 * ② 새로 지은 번들 전체를 기존 번들과 바이트 단위로 비교한다. ①이
 * "저장된 해시가 그 리플레이의 필드로부터 실제로 재현되는가"를,
 * ②가 "빌드 전체가 결정론적인가"를 각각 잡는다 — 어느 한쪽만으로는
 * 부족하다: ①만 하면 roundrobin.json 같은 리플레이가 없는 파일의
 * 드리프트를 놓치고, ②만 하면 "두 번 다 우연히 같은 방식으로 틀렸다"는
 * 경우(예: 항상 같은 잘못된 해시를 쓰는 버그)를 못 잡는다.
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

        Path scratch = outputDir.resolveSibling(outputDir.getFileName() + "-verify");
        try {
            buildInto(recordsDir, scratch);

            String hashMismatch = verifyReplayHashes(scratch);
            if (hashMismatch != null) {
                System.out.println("재현 검증 실패 — R1이 깨졌다");
                System.out.println("  " + hashMismatch);
                return 1;
            }

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
                Seeds.WIDTH, Seeds.HEIGHT,
                new RecordStore(recordsDir),
                outputDir);
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
