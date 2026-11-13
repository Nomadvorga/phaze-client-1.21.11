package vorga.phazeclient.api.system.render;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.InputStream;

/** Headless bytecode contract checks; these do not execute OpenGL or measure FPS. */
public final class RenderLifecycleChecks {
    private static final String ROOT = "vorga/phazeclient/";
    private static int checks;

    public static void main(String[] args) throws Exception {
        ClassNode snapshots = read(ROOT + "api/system/snapshot/CardSnapshotCache");
        MethodNode begin = method(snapshots, "beginCapture", "(Lvorga/phazeclient/api/system/snapshot/CardSnapshotCache$Snapshot;FF)V");
        check(calls(begin, "endCapture", "(Z)V"), "begin failure rolls capture back");
        check(begin.tryCatchBlocks.stream().anyMatch(t -> "java/lang/Error".equals(t.type)), "begin rolls back Errors as well as exceptions");
        MethodNode end = method(snapshots, "endCapture", "(Z)V");
        check(end.tryCatchBlocks.stream().anyMatch(t -> t.type == null), "end capture has unconditional finally restoration");
        check(calls(end, "close", "(Z)V"), "snapshots close the shared capture with an explicit publication result");
        check(calls(end, "close", "()V"), "snapshots restore their GUI target-size scope");
        ClassNode passState = read(ROOT + "api/system/hud/HudPassState");
        MethodNode stateClose = method(passState, "close", "()V");
        for (String call : new String[]{"setRenderTargetFbHeight", "setProjectionMatrix", "enableScissorForRenderTypeDraws", "disableScissorForRenderTypeDraws", "getModelViewStack", "setShaderFog", "setShaderLights"}) {
            check(calls(stateClose, call, null), "capture restoration includes " + call);
        }
        for (String field : new String[]{"outputColorTextureOverride", "outputDepthTextureOverride"}) {
            check(writes(stateClose, "com/mojang/blaze3d/systems/RenderSystem", field), "capture restores the caller's " + field);
        }
        check(passState.methods.stream().noneMatch(m -> calls(m, "getFramebuffer", null)), "pass restoration never guesses the main framebuffer");
        ClassNode capture = read(ROOT + "api/system/hud/HudCapture");
        MethodNode captureClose = method(capture, "close", "(Z)V");
        check(captureClose.tryCatchBlocks.stream().anyMatch(t -> t.type == null), "failed capture flush still restores its parent");
        check(calls(captureClose, "discard", "()V"), "failed or aborted captures discard pending geometry");
        check(calls(captureClose, "addSuppressed", null), "capture cleanup does not hide the original rendering failure");
        check(calls(method(snapshots, "clearAll", "()V"), "endCapture", "(Z)V"), "cache disposal aborts active capture first");
        check(calls(method(snapshots, "clearAll", "()V"), "abortThrough", null), "snapshot disposal unwinds nested captures before deleting attachments");
        check(calls(method(snapshots, "copyRegionFromFramebuffer", "(Lvorga/phazeclient/api/system/snapshot/CardSnapshotCache$Snapshot;Lnet/minecraft/client/gl/Framebuffer;IIII)V"), "copyTextureToTexture", null), "snapshot copies use the 1.21.11 command encoder, not ambient GL bindings");
        MethodNode gpuDraw = read(ROOT + "api/system/draw/GpuDraw").methods.stream().filter(m -> m.name.equals("drawInternal")).findFirst().orElseThrow();
        check(gpuDraw.tryCatchBlocks.stream().anyMatch(t -> "java/lang/Throwable".equals(t.type)) && calls(gpuDraw, "close", "()V"), "GPU render passes are closed on success and failure");
        check(calls(gpuDraw, "getSortedBuffer", null) && calls(gpuDraw, "uploadImmediateIndexBuffer", null), "GPU draws upload sorted translucent indices");

        ClassNode overlays = read(ROOT + "api/system/render/WorldOverlayRenderer");
        MethodNode clear = method(overlays, "clear", "()V");
        check(calls(clear, "close", "()V"), "world cache closes native allocators");
        check(clear.tryCatchBlocks.stream().anyMatch(t -> t.type == null), "world allocator release is exception safe");

        ClassNode client = read(ROOT + "core/client/ClientMain");
        check(client.methods.stream().noneMatch(m -> calls(m, "saveCurrentConfig", null)), "client resource lifecycle does not duplicate config saves");
        check(calls(method(client, "onClientStopping", "(Lnet/minecraft/client/MinecraftClient;)V"), "close", null), "normal shutdown closes render resources");
        check(read(ROOT + "core/client/ClientRenderResources").methods.stream().anyMatch(m -> calls(m, "execute", null)), "GPU lifecycle is scheduled on client thread");
        ClassNode rpc = read(ROOT + "api/system/discord/DiscordManager");
        check(rpc.fields.stream().anyMatch(f -> f.name.equals("running") && (f.access & Opcodes.ACC_VOLATILE) != 0), "RPC shutdown flag is visible to its worker");
        check(calls(method(rpc, "init", "()V"), "setDaemon", "(Z)V"), "RPC worker cannot keep the process alive");
        check(calls(method(rpc, "stopRPC", "()V"), "interrupt", "()V"), "RPC shutdown wakes the sleeping worker");
        System.out.println("Render lifecycle checks PASSED (" + checks + " contracts; no GL context)");
    }

    private static boolean writes(MethodNode method, String owner, String name) {
        for (var instruction : method.instructions) {
            if (instruction instanceof FieldInsnNode field && field.getOpcode() == Opcodes.PUTSTATIC
                    && field.owner.equals(owner) && field.name.equals(name)) return true;
        }
        return false;
    }

    private static boolean calls(MethodNode method, String name, String desc) {
        for (var instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && call.name.equals(name)
                    && (desc == null || call.desc.equals(desc))) return true;
        }
        return false;
    }

    private static MethodNode method(ClassNode type, String name, String desc) {
        return type.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(desc)).findFirst().orElseThrow();
    }

    private static ClassNode read(String name) throws Exception {
        try (InputStream input = RenderLifecycleChecks.class.getResourceAsStream("/" + name + ".class")) {
            if (input == null) throw new AssertionError("Missing " + name);
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, 0);
            return node;
        }
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
