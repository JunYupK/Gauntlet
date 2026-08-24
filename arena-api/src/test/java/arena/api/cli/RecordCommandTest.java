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

    // --- verify의 두 겹은 서로 다른 메커니즘이다. 각각 독립적으로
    // 실패시켜서 둘 다 실제로 무는지 증명한다(RecordCommand의 클래스
    // javadoc ①·②) ---

    /**
     * 바이트 대조 층(②)만 걸리게 한다: {@code matchId}는 {@link
     * arena.core.ReplayHash#of}의 계산 필드가 아니므로, 이 필드만
     * 건드리면 해시 재계산 층(①)은 그대로 통과하고 "새로 지은 번들과
     * 바이트가 다르다"는 ②에서만 걸린다.
     */
    @Test
    void gallery_json의_matchId를_바꾸면_verify가_1을_반환한다(@TempDir Path tmp) throws Exception {
        Path records = tmp.resolve("records");
        Path data = tmp.resolve("data");
        RecordCommand.runInto(records, data, false);

        Path galleryPath = data.resolve("gallery.json");
        String json = Files.readString(galleryPath);
        String tampered = json.replaceFirst("(\"matchId\" : \"[^\"]*)\"", "$1X\"");
        assertNotEquals(json, tampered, "matchId 필드를 못 찾았다 — 픽스처(gallery.json 형식)가 바뀌었는지 확인");
        Files.writeString(galleryPath, tampered);

        int code = RecordCommand.runInto(records, data, true);

        assertEquals(1, code, "matchId가 저장된 번들과 달라졌는데도 verify가 통과했다 — 바이트 대조 층이 안 걸렸다");
    }

    /**
     * 해시 재계산 층(①)이 곧바로 걸리게 한다: 저장된 {@code hash}
     * 필드를 조작하면, 그 리플레이의 다른 필드(bot0Id·bot1Id·seed·
     * width·height·moves·result)로부터 다시 계산한 해시와 더 이상
     * 일치하지 않는다 — 새 번들을 짓지 않고 저장된 파일만 읽고도
     * 걸려야 한다.
     */
    @Test
    void gallery_json의_hash를_조작하면_verify가_1을_반환한다(@TempDir Path tmp) throws Exception {
        Path records = tmp.resolve("records");
        Path data = tmp.resolve("data");
        RecordCommand.runInto(records, data, false);

        Path galleryPath = data.resolve("gallery.json");
        String json = Files.readString(galleryPath);
        String tampered = json.replaceFirst("\"hash\" : \"sha256:", "\"hash\" : \"sha256:00");
        assertNotEquals(json, tampered, "hash 필드를 못 찾았다 — 픽스처(gallery.json 형식)가 바뀌었는지 확인");
        Files.writeString(galleryPath, tampered);

        int code = RecordCommand.runInto(records, data, true);

        assertEquals(1, code, "hash 필드가 조작됐는데도 verify가 통과했다 — 해시 재계산 층이 안 걸렸다");
    }
}
