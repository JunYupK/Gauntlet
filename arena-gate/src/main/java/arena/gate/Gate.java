package arena.gate;

/**
 * 관문 하나. 반드시 코드가 O/X를 내야 한다.
 * 사람의 눈이 필요한 기준은 관문이 될 수 없다.
 */
public interface Gate {

    String id();

    GateResult check(GateContext ctx);
}
