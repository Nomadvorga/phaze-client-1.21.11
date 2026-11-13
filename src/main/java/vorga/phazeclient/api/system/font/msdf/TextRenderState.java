package vorga.phazeclient.api.system.font.msdf;

import vorga.phazeclient.api.system.hud.Render2DState;

/** No GL binding queries: GPU pipelines isolate raster state; preserve only mutable pass globals. */
final class TextRenderState implements AutoCloseable {
    private final Render2DState state;
    TextRenderState() { this(false); }
    TextRenderState(boolean vanilla) { state = vanilla ? Render2DState.capture() : Render2DState.join(); }
    @Override public void close() { state.close(); }
}
