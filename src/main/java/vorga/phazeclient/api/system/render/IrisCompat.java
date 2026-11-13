package vorga.phazeclient.api.system.render;

import net.fabricmc.loader.api.FabricLoader;
import java.lang.reflect.Method;

/** Optional API probe is resolved once; never loads Iris classes on vanilla. */
public final class IrisCompat {
    private static final Object API;
    private static final Method SHADOW;
    static {
        Object api = null;
        Method shadow = null;
        if (FabricLoader.getInstance().isModLoaded("iris")) {
            try {
                Class<?> type = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                api = type.getMethod("getInstance").invoke(null);
                shadow = type.getMethod("isRenderingShadowPass");
            } catch (ReflectiveOperationException failure) {
                System.err.println("[Phaze/Iris] Cannot query shadow pass: " + failure);
            }
        }
        API = api;
        SHADOW = shadow;
    }
    private IrisCompat() {}

    public static boolean isShadowPass() {
        if (SHADOW == null) return false;
        try { return (boolean) SHADOW.invoke(API); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException("Iris shadow query failed", failure); }
    }
}
