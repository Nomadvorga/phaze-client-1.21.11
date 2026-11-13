package vorga.phazeclient.api.system.hud;

/** Phase-preserving render-thread clock. Failed captures must not advance it. */
public final class HudAnimationRefreshClock {
    private int fps;
    private long interval, next;
    private boolean initialized;

    public HudAnimationRefreshClock(int fps) { setFps(fps); }

    public void setFps(int value) {
        int clamped = Math.max(1, Math.min(360, value));
        if (fps == clamped) return;
        fps = clamped;
        interval = 1_000_000_000L / fps;
        reset();
    }

    public boolean due(long now) { return !initialized || now - next >= 0; }

    public void captured(long started) {
        if (!initialized || started - next < 0) next = started + interval;
        else next = started + interval - (started - next) % interval;
        initialized = true;
    }

    public void reset() { initialized = false; }
}
