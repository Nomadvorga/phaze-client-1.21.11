package vorga.phazeclient.api.system.hud;

/** Logical source HudPassState, kept separate from the 1.21.11 GPU pass snapshot. */
public final class HudRenderModes {
    public enum Mode { DIRECT, CAPTURE, GRADIENT, LOGIC }
    private Mode mode = Mode.DIRECT;
    public boolean isBatchPass() { return mode == Mode.CAPTURE || mode == Mode.GRADIENT; }
    public boolean includesBlur() { return mode == Mode.CAPTURE; }
    public boolean isGradientPass() { return mode == Mode.GRADIENT; }
    public boolean isLogicOnlyPass() { return mode == Mode.LOGIC; }
    public void run(Mode next, Runnable render) {
        Mode previous = mode;
        mode = next;
        try { render.run(); } finally { mode = previous; }
    }
    public void reset() { mode = Mode.DIRECT; }
}
