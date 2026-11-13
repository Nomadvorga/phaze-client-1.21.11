package vorga.phazeclient.api.system.hud;

/** GPU-pass equivalent of the source GL scope. Blend/cull/depth are immutable pipeline state. */
public final class Render2DState implements AutoCloseable {
    private static int depth;
    private static final Render2DState JOINED = new Render2DState(false);
    private final HudPassState state;
    private boolean closed;
    private Render2DState(boolean capture) {
        state = capture ? HudPassState.capture() : null;
        if (capture) depth++;
    }
    public static Render2DState capture() { return new Render2DState(true); }
    public static Render2DState join() { return depth > 0 ? JOINED : capture(); }
    public static boolean isJoined() { return depth > 0; }
    @Override public void close() {
        if (state == null || closed) return;
        closed = true;
        try { state.close(); } finally { depth--; }
    }
}
