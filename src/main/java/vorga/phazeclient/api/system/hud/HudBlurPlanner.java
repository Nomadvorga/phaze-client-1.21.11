package vorga.phazeclient.api.system.hud;

import java.util.ArrayList;
import java.util.List;

/** Previous layout is only a preparation hint; live draws validate radius, region and revision. */
public final class HudBlurPlanner {
    public static final int MAX_REQUESTS = 128;
    private List<Request> previous = new ArrayList<>(MAX_REQUESTS), current = new ArrayList<>(MAX_REQUESTS);
    private boolean rendered;
    public record Rect(int x, int y, int width, int height) {
        public boolean contains(Rect other) {
            return other.x >= x && other.y >= y && other.x + other.width <= x + width && other.y + other.height <= y + height;
        }
    }
    public record Request(float radius, Rect region) {}
    public record Group(float radius, List<Rect> regions) {}
    public void beginFrame() { current.clear(); rendered = false; }
    public void remember(float radius, Rect region) {
        rendered = true;
        if (!Float.isFinite(radius) || radius <= 8 || region.width <= 0 || region.height <= 0) return;
        Request request = new Request(radius, region);
        if (current.size() < MAX_REQUESTS && !current.contains(request)) current.add(request);
    }
    public void endFrame() {
        if (!rendered) return;
        rendered = false;
        List<Request> old = previous;
        previous = current; current = old; current.clear();
    }
    public void reset() { previous.clear(); current.clear(); rendered = false; }
    public List<Group> groups() {
        List<Group> groups = new ArrayList<>();
        for (Request request : previous) {
            Group group = null;
            for (Group candidate : groups) {
                if (Float.floatToIntBits(candidate.radius) == Float.floatToIntBits(request.radius)) { group = candidate; break; }
            }
            if (group == null) { group = new Group(request.radius, new ArrayList<>()); groups.add(group); }
            boolean covered = false;
            for (Rect rect : group.regions) if (rect.contains(request.region)) { covered = true; break; }
            if (!covered) { group.regions.removeIf(request.region::contains); group.regions.add(request.region); }
        }
        return groups;
    }
    public static Rect scale(Rect rect, int sw, int sh, int tw, int th) { return map(rect, sw, sh, tw, th, 2); }
    public static Rect requiredSource(Rect target, int tw, int th, int sw, int sh, float offset, boolean downsample) {
        int guard = (int) Math.ceil(Math.max(0, offset) * (downsample ? 1 : 2)) + 1;
        return map(target, tw, th, sw, sh, guard);
    }
    private static Rect map(Rect rect, int fw, int fh, int tw, int th, int guard) {
        int left = Math.max(0, (int) Math.floor(rect.x * (double) tw / fw) - guard);
        int bottom = Math.max(0, (int) Math.floor(rect.y * (double) th / fh) - guard);
        int right = Math.min(tw, (int) Math.ceil((rect.x + rect.width) * (double) tw / fw) + guard);
        int top = Math.min(th, (int) Math.ceil((rect.y + rect.height) * (double) th / fh) + guard);
        return new Rect(left, bottom, Math.max(1, right - left), Math.max(1, top - bottom));
    }
}
