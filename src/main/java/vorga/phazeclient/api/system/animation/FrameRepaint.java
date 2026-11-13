package vorga.phazeclient.api.system.animation;

/**
 * Render-thread deadline by which a cached GUI frame has to be drawn again. Anything that changes on its own
 * between input events (animations, marquees, carets) reports here while it is drawn.
 */
public final class FrameRepaint {
    private static long deadlineNanos = Long.MAX_VALUE;

    private FrameRepaint() {
    }

    /** The next frame has to be drawn again. */
    public static void now() {
        deadlineNanos = Long.MIN_VALUE;
    }

    public static void at(long nanos) {
        if (nanos < deadlineNanos) deadlineNanos = nanos;
    }

    /** For a caret toggled by {@code currentTimeMillis() / periodMs}. */
    public static void blink(long periodMs) {
        long millis = System.currentTimeMillis();
        at(System.nanoTime() + (periodMs - Math.floorMod(millis, periodMs)) * 1_000_000L);
    }

    public static void reset() {
        deadlineNanos = Long.MAX_VALUE;
    }

    public static long deadline() {
        return deadlineNanos;
    }
}
