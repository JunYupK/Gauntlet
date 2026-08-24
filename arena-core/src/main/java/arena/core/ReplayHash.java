package arena.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 리플레이의 정규화 해시.
 *
 * record --verify가 전체 실험을 재실행해 이 값을 대조한다.
 * 발표에서 R1을 주장할 때 그 명령의 출력이 곧 증거가 된다.
 */
public final class ReplayHash {

    private ReplayHash() {}

    public static String of(
            String bot0Id, String bot1Id, long seed,
            int width, int height, String moves, MatchResult result) {

        String canonical = String.join("|",
                String.valueOf(Replay.SCHEMA),
                bot0Id, bot1Id,
                String.valueOf(seed),
                width + "x" + height,
                moves,
                String.valueOf(result.winner()),
                String.valueOf(result.turns()),
                result.reason().name());

        return "sha256:" + hex(sha256(canonical));
    }

    private static byte[] sha256(String s) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(s.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 쓸 수 없는 JVM", e);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
