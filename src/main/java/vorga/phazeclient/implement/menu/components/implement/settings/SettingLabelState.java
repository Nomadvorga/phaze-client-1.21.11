package vorga.phazeclient.implement.menu.components.implement.settings;

/** GL-free label geometry and one-shot marquee, driven by monotonic nanoseconds. */
public final class SettingLabelState {
    public static final int WIDTH_TRANSITION_MS = 200;
    public static final long WIDTH_TRANSITION_NANOS = WIDTH_TRANSITION_MS * 1_000_000L;
    public static final long TRAVEL_NANOS = 900_000_000L;
    public static final long HOLD_NANOS = 650_000_000L;
    public static final long RETURN_NANOS = 900_000_000L;
    public static final float FADE_WIDTH = 10.0F;
    public static final float FULL_FADE_DISTANCE = 2.0F;
    private String text = "";
    private float width, height, overflow, widthFrom, widthTarget;
    private long widthStarted, started;
    private boolean widthInitialized, running, returning, hovered;
    private float startFraction;
    public void layout(String text, float textWidth, float width, float height) {
        layout(text, textWidth, width, height, System.nanoTime());
    }
    public void layout(String text, float textWidth, float width, float height, long now) {
        layout(text, textWidth, width, height, now, 0.0F);
    }
    void layout(String text, float textWidth, float width, float height, long now, float animatedInset) {
        float target = Math.max(0.0F, width + animatedInset);
        if (!widthInitialized) {
            widthFrom = widthTarget = target;
            widthStarted = now;
            widthInitialized = true;
        }
        float currentWidth = sampleWidth(now);
        if (Float.compare(target, widthTarget) != 0) {
            widthFrom = currentWidth; widthTarget = target; widthStarted = now;
        }
        if (!this.text.equals(text)) { running = false; returning = false; }
        this.text = text;
        this.width = Math.max(0.0F, currentWidth - animatedInset);
        this.height = Math.max(0.0F, height);
        this.overflow = Math.max(0.0F, textWidth - this.width);
        if (!isOverflowing()) { running = false; returning = false; }
    }
    private float sampleWidth(long now) {
        float t = clamp((float) Math.max(0L, now - widthStarted) / WIDTH_TRANSITION_NANOS);
        float progress = 1.0F - (1.0F - t) * (1.0F - t);
        return widthFrom + (widthTarget - widthFrom) * progress;
    }
    public float getWidth() { return width; }
    public boolean isOverflowing() { return overflow > 0.0F && width > 0.0F && height > 0.0F; }
    public boolean isHovered() { return hovered; }
    public void updateHover(boolean hovered, long now) {
        float currentOffset = offset(now);
        boolean entered = hovered && !this.hovered, left = !hovered && this.hovered;
        this.hovered = hovered;
        if (entered && isOverflowing()) {
            startFraction = currentOffset / overflow; started = now; running = true; returning = false;
        } else if (left && running) {
            startFraction = currentOffset / overflow; started = now; returning = true;
            running = startFraction > 0.0F;
        }
    }
    public float offset(long now) {
        if (!running || !isOverflowing()) return 0.0F;
        long elapsed = Math.max(0L, now - started);
        if (returning) {
            long duration = (long) (RETURN_NANOS * startFraction);
            if (elapsed < duration) return overflow * startFraction * (1.0F - ease((float) elapsed / duration));
            running = false; returning = false; return 0.0F;
        }
        if (elapsed < TRAVEL_NANOS) return overflow * (startFraction + (1.0F - startFraction) * ease((float) elapsed / TRAVEL_NANOS));
        elapsed -= TRAVEL_NANOS;
        if (elapsed < HOLD_NANOS) return overflow;
        elapsed -= HOLD_NANOS;
        if (elapsed < RETURN_NANOS) return overflow * (1.0F - ease((float) elapsed / RETURN_NANOS));
        running = false; return 0.0F;
    }
    public float fadeWidth() { return Math.min(FADE_WIDTH, width * 0.5F); }
    public float edgeStrength(float hidden) { return clamp(hidden / FULL_FADE_DISTANCE); }
    public float alphaAt(float localX, float offset) {
        if (!isOverflowing()) return 1.0F;
        if (localX < 0.0F || localX > width) return 0.0F;
        float fade = fadeWidth(), left = edgeStrength(offset), right = edgeStrength(overflow - offset);
        return clamp((1.0F - left * (1.0F - ease(localX / fade)))
                * (1.0F - right * (1.0F - ease((width - localX) / fade))));
    }
    public float getOverflow() { return overflow; }
    public static float snapClipEdge(float localX, float scale, float translation) {
        return scale > 0.0F ? (Math.round(localX * scale + translation) - translation) / scale : localX;
    }
    private static float ease(float value) { float t = clamp(value); return t * t * (3.0F - 2.0F * t); }
    private static float clamp(float value) { return Math.max(0.0F, Math.min(1.0F, value)); }
}
