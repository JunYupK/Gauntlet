package arena.gate;

public record GateResult(String gateId, boolean passed, String detail) {

    public static GateResult pass(String gateId) {
        return new GateResult(gateId, true, "");
    }

    public static GateResult fail(String gateId, String detail) {
        return new GateResult(gateId, false, detail);
    }
}
