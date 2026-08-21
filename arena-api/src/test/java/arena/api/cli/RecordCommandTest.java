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
