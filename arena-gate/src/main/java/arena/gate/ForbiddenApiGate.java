package arena.gate;

import org.objectweb.asm.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * G3 — R1을 깨뜨릴 수 있는 API를 바이트코드에서 찾아낸다.
 *
 * 시드 있는 Random은 허용한다. 바이트코드에서는 생성자 디스크립터로
 * 구분된다 — ()V는 시드 없음, (J)V는 시드 있음.
 *
 * 봇 클래스 하나만 보면 안 된다. 금지 호출을 익명 클래스·정적 중첩
 * 클래스·로컬 클래스로 옮기기만 해도 단일 클래스 스캔은 통과한다 —
 * G2가 상속으로 뚫렸던 것과 같은 모양의 구멍이다. javac는 클래스가
 * 참조하는 모든 중첩 클래스를 InnerClasses 애트리뷰트에 남기므로,
 * 봇의 최상위 클래스 이름을 접두어로 삼아 그 자손만 추려 재귀적으로
 * 따라간다(무관한 라이브러리 내부 클래스까지 끌려오지 않도록). 람다
 * 본문은 컴파일러가 같은 클래스의 private synthetic 메서드로 넣으므로
 * 재귀 없이도 이미 잡힌다.
 */
public final class ForbiddenApiGate implements Gate {

    /** 이 접두사로 시작하는 클래스는 통째로 금지. */
    private static final String[] FORBIDDEN_PREFIXES = {
            "java/io/",
            "java/nio/file/",
            "java/net/",
            "java/util/concurrent/",
            "java/time/",
            "java/lang/reflect/",
            "sun/misc/Unsafe",
            "java/lang/Thread",
            "java/lang/ProcessBuilder",
    };

    /** owner.name 정확 일치 금지. */
    private static final String[] FORBIDDEN_METHODS = {
            "java/lang/Math.random",
            "java/lang/System.currentTimeMillis",
            "java/lang/System.nanoTime",
            "java/lang/System.identityHashCode",
            "java/lang/System.getenv",
            "java/lang/System.getProperty",
            "java/lang/Object.hashCode",
    };

    @Override
    public String id() { return "G3"; }

    @Override
    public GateResult check(GateContext ctx) {
        List<String> violations = new ArrayList<>();
        ClassLoader loader = ctx.botClass().getClassLoader();
        String rootInternalName = ctx.botClass().getName().replace('.', '/');
        String nestedPrefix = rootInternalName + "$";

        Set<String> visited = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(rootInternalName);
        visited.add(rootInternalName);

        while (!queue.isEmpty()) {
            String internalName = queue.remove();
            byte[] bytecode = readClassFile(loader, internalName);

            Set<String> discoveredNested = new HashSet<>();
            new ClassReader(bytecode).accept(
                    new Scanner(simpleLabel(internalName), violations,
                            nestedPrefix, discoveredNested),
                    ClassReader.SKIP_FRAMES);

            for (String nested : discoveredNested) {
                if (visited.add(nested)) {
                    queue.add(nested);
                }
            }
        }

        if (violations.isEmpty()) {
            return GateResult.pass(id());
        }
        return GateResult.fail(id(),
                "금지 API를 " + violations.size() + "곳에서 사용한다:\n  "
                        + String.join("\n  ", violations));
    }

    /** 진단 메시지에 쓸 짧은 이름. 패키지는 빼고 클래스 이름만 남긴다. */
    private static String simpleLabel(String internalName) {
        int slash = internalName.lastIndexOf('/');
        return slash < 0 ? internalName : internalName.substring(slash + 1);
    }

    private static byte[] readClassFile(ClassLoader loader, String internalName) {
        String resource = internalName + ".class";
        try (InputStream in = loader.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("클래스 파일을 찾을 수 없다: " + resource);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("클래스 파일을 읽지 못했다: " + resource, e);
        }
    }

    private static final class Scanner extends ClassVisitor {

        private final String botName;
        private final List<String> violations;
        private final String nestedPrefix;
        private final Set<String> discoveredNested;

        Scanner(String botName, List<String> violations,
                String nestedPrefix, Set<String> discoveredNested) {
            super(Opcodes.ASM9);
            this.botName = botName;
            this.violations = violations;
            this.nestedPrefix = nestedPrefix;
            this.discoveredNested = discoveredNested;
        }

        @Override
        public void visitInnerClass(String name, String outerName, String innerName, int access) {
            // 봇 자신의 자손(익명·정적 중첩·로컬 클래스)만 따라간다.
            // java/util/Map$Entry 같은 무관한 라이브러리 내부 클래스까지
            // 끌려오지 않도록 접두어로 거른다.
            if (name.startsWith(nestedPrefix)) {
                discoveredNested.add(name);
            }
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor,
                                       String signature, Object value) {
            boolean isStatic = (access & Opcodes.ACC_STATIC) != 0;
            boolean isFinal = (access & Opcodes.ACC_FINAL) != 0;

            if (isStatic && !isFinal) {
                violations.add("가변 static 필드: " + name
                        + " — 전역 가변 상태는 경기 간 오염을 만든다");
            }
            return super.visitField(access, name, descriptor, signature, value);
        }

        @Override
        public MethodVisitor visitMethod(int access, String methodName, String descriptor,
                                         String signature, String[] exceptions) {
            return new MethodVisitor(Opcodes.ASM9) {

                @Override
                public void visitMethodInsn(int opcode, String owner, String name,
                                            String desc, boolean isInterface) {
                    // 시드 없는 Random 생성자만 콕 집어 금지한다.
                    if (owner.equals("java/util/Random")
                            && name.equals("<init>")
                            && desc.equals("()V")) {
                        violations.add(botName + "." + methodName
                                + " → new Random() (시드 없음). 시드를 주면 허용된다");
                        return;
                    }

                    String qualified = owner + "." + name;
                    for (String forbidden : FORBIDDEN_METHODS) {
                        if (qualified.equals(forbidden)) {
                            violations.add(botName + "." + methodName + " → " + qualified);
                            return;
                        }
                    }
                    checkOwner(owner, methodName);
                }

                @Override
                public void visitTypeInsn(int opcode, String type) {
                    if (opcode == Opcodes.NEW) {
                        checkOwner(type, methodName);
                    }
                }

                @Override
                public void visitFieldInsn(int opcode, String owner, String name, String desc) {
                    checkOwner(owner, methodName);
                }

                private void checkOwner(String owner, String where) {
                    for (String prefix : FORBIDDEN_PREFIXES) {
                        if (owner.startsWith(prefix)) {
                            violations.add(botName + "." + where + " → " + owner);
                            return;
                        }
                    }
                }
            };
        }
    }
}
