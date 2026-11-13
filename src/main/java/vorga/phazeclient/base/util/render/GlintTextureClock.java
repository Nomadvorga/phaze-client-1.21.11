package vorga.phazeclient.base.util.render;

/** Render-owned incremental texture clock; changing speed never rescales the epoch. */
public final class GlintTextureClock {
    private boolean active;
    private long previousRealMs, textureMs;
    private double fractionalMs, previousSpeed;

    public long sample(long realMs, boolean enabled, double speed) {
        if (!enabled) {
            active = false;
            return realMs;
        }
        double boundedSpeed = Double.isFinite(speed) ? Math.max(0.2, Math.min(4.0, speed)) : 1.0;
        if (!active) {
            active = true;
            previousRealMs = textureMs = realMs;
            fractionalMs = 0;
        } else if (realMs > previousRealMs) {
            double elapsed = (realMs - previousRealMs) * previousSpeed + fractionalMs;
            long wholeMs = (long) elapsed;
            textureMs += wholeMs;
            fractionalMs = elapsed - wholeMs;
            previousRealMs = realMs;
        }
        previousSpeed = boundedSpeed;
        return textureMs;
    }
}
