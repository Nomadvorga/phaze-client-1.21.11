package vorga.phazeclient.implement.menu.components.implement.settings;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.joml.Matrix3x2fStack;
import org.joml.Vector2f;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.system.cursor.CursorManager;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Offline cursor-state and GUI-versus-chat readout regression checks; no window or user config required. */
public final class GuiInteractionChecks {
    private static final String CURSOR = "vorga/phazeclient/api/system/cursor/CursorManager";
    private static final String ANIMATIONS = "vorga/phazeclient/implement/features/modules/other/Animations";
    private static final String SETTINGS = "vorga/phazeclient/implement/menu/components/implement/settings/";
    private static final String OVERLAY = SETTINGS + "ScaleSnapOverlay";
    private static final String CALLBACKS = SETTINGS + "GuiInteractionChecks";
    private static final List<Integer> appliedShapes = new ArrayList<>();
    private static final List<Integer> createdShapes = new ArrayList<>();
    private static int checks;

    public static void main(String[] args) throws Exception {
        checks = 0;
        minecraftInputAndMatrixContracts();
        messengerTextFieldEditing();
        sidebarViewportContracts();
        disabledDragging();
        enabledDragging();
        disableAndReenable();
        scrollAndScreenReset();
        readoutContracts();
        messengerContracts();
        messengerCardBounds();
        System.out.println("GUI interaction checks passed: " + checks);
    }

    private static void minecraftInputAndMatrixContracts() throws Exception {
        ClassNode menu = readClass("vorga/phazeclient/implement/menu/MenuScreen");
        for (String[] signature : new String[][]{
                {"mouseClicked", "(Lnet/minecraft/client/gui/Click;Z)Z"},
                {"mouseReleased", "(Lnet/minecraft/client/gui/Click;)Z"},
                {"mouseDragged", "(Lnet/minecraft/client/gui/Click;DD)Z"},
                {"keyPressed", "(Lnet/minecraft/client/input/KeyInput;)Z"},
                {"keyReleased", "(Lnet/minecraft/client/input/KeyInput;)Z"},
                {"charTyped", "(Lnet/minecraft/client/input/CharInput;)Z"}}) {
            check(menu.methods.stream().anyMatch(method -> method.name.equals(signature[0])
                            && method.desc.equals(signature[1])), "menu uses the 1.21.11 event API: " + signature[0]);
        }
        MethodNode guides = method(menu, "renderCenterGuides");
        check(calls(guides).stream().anyMatch(call -> call.owner.equals("net/minecraft/client/gui/DrawContext")
                        && call.name.equals("createNewRootLayer")), "center guides use GUI layer ordering, not a removed z matrix");
        check(calls(guides).stream().noneMatch(call -> call.owner.equals("com/mojang/blaze3d/systems/RenderSystem")
                        || call.name.equals("drawDeferredElements")), "guides neither change ambient scissor nor fake a GUI flush");
        check(calls(method(menu, "render")).stream().anyMatch(call -> call.owner.equals(menu.name)
                        && call.name.equals("applyMenuTransform")), "rendering uses the tested coordinate transform");

        // Keep only the production pure transform: no menu singleton, window, theme or config initialization.
        menu.superName = "java/lang/Object";
        menu.interfaces.clear();
        menu.fields.clear();
        menu.methods.removeIf(method -> !method.name.equals("applyMenuTransform"));
        Class<?> pureMenu = new OfflineClasses().define(menu);
        Method transform = pureMenu.getDeclaredMethod("applyMenuTransform", Matrix3x2fStack.class,
                float.class, float.class, float.class, float.class);
        transform.setAccessible(true);
        for (float overlay : new float[]{0.5F, 0.75F, 1.0F, 1.5F, 2.0F}) {
            for (float gui : new float[]{0.5F, 1.0F, 1.5F, 3.0F}) {
                Matrix3x2fStack matrices = new Matrix3x2fStack(4);
                transform.invoke(null, matrices, overlay, gui, 213.0F, 147.0F);
                for (Vector2f point : List.of(new Vector2f(213, 147), new Vector2f(13, 27), new Vector2f(463, 317))) {
                    Vector2f actual = matrices.transformPosition(point, new Vector2f());
                    float expectedX = overlay * (213 + (point.x - 213) * gui);
                    float expectedY = overlay * (147 + (point.y - 147) * gui);
                    check(Math.abs(actual.x - expectedX) < 0.001F && Math.abs(actual.y - expectedY) < 0.001F,
                            "overlay scaling includes the GUI pivot at every window/GUI scale");
                    float inputX = 213 + (actual.x / overlay - 213) / gui;
                    float inputY = 147 + (actual.y / overlay - 147) / gui;
                    check(Math.abs(inputX - point.x) < 0.001F && Math.abs(inputY - point.y) < 0.001F,
                            "render and input coordinate conversions stay inverse across scales");
                }
            }
        }
    }

    private static void disabledDragging() throws Exception {
        for (boolean[] options : new boolean[][]{{false, false}, {false, true}, {true, false}}) {
            CursorHarness cursor = new CursorHarness();
            cursor.options(options[0], options[1]);
            check(!cursor.enabled(), "either disabled toggle disables the actual Animations cursor gate");
            cursor.call("forceArrow");
            appliedShapes.clear();
            createdShapes.clear();
            for (int frame = 0; frame < 120; frame++) {
                cursor.call("beginFrame");
                cursor.call("requestHand");
                cursor.call("requestBeam");
                cursor.call("beginDrag", CursorManager.SHAPE_HRESIZE);
                cursor.call("notifyScroll", 0.0, 1.0);
                check(appliedShapes.isEmpty(), "disabled dragging must not briefly apply a resize cursor before endFrame");
                check(cursor.intField("dragOverrideShape") == 0, "disabled dragging does not arm a drag pin");
                cursor.call("endFrame", cursor.enabled());
                check(appliedShapes.isEmpty(), "disabled drag frames keep the existing arrow without OS cursor churn");
            }
            check(createdShapes.isEmpty(), "disabled dragging and scrolling allocate no native cursors");
        }
    }

    private static void messengerTextFieldEditing() throws Exception {
        Class<?> type = Class.forName("vorga.phazeclient.implement.menu.components.implement.other.MessengerTextField");
        var constructor = type.getDeclaredConstructor(int.class, java.util.function.Predicate.class);
        constructor.setAccessible(true);
        Object field = constructor.newInstance(3, (java.util.function.Predicate<Integer>) codePoint -> true);
        Method focused = type.getDeclaredMethod("setFocused", boolean.class);
        Method typed = type.getDeclaredMethod("charTyped", int.class);
        Method key = type.getDeclaredMethod("keyPressed", int.class, int.class);
        Method value = type.getDeclaredMethod("value");
        Method setValue = type.getDeclaredMethod("setValue", String.class);
        Method insert = type.getDeclaredMethod("insert", String.class);
        for (Method method : List.of(focused, typed, key, value, setValue, insert)) method.setAccessible(true);
        check(Boolean.FALSE.equals(typed.invoke(field, 0x1F600)), "an unfocused messenger field does not receive text");
        focused.invoke(field, true);
        typed.invoke(field, (int) 'A');
        typed.invoke(field, 0x1F600);
        typed.invoke(field, (int) 'B');
        check(value.invoke(field).equals("A\uD83D\uDE00B"), "messenger input preserves supplementary Unicode characters");
        typed.invoke(field, (int) 'C');
        check(value.invoke(field).equals("A\uD83D\uDE00B"), "maximum input size counts code points rather than UTF-16 units");
        key.invoke(field, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT, 0);
        key.invoke(field, org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE, 0);
        check(value.invoke(field).equals("AB"), "backspace removes the entire previous code point without leaving a surrogate");
        typed.invoke(field, 0x1F600);
        key.invoke(field, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT, 0);
        key.invoke(field, org.lwjgl.glfw.GLFW.GLFW_KEY_DELETE, 0);
        check(value.invoke(field).equals("AB"), "delete removes the entire next code point after a supplementary-character cursor move");
        for (int invalid : new int[]{-1, 0x110000, 0xD800, 0xDFFF, '\n', 0}) {
            check(Boolean.FALSE.equals(typed.invoke(field, invalid)), "messenger rejects invalid/control input code point: " + invalid);
        }
        setValue.invoke(field, (Object) null);
        check(value.invoke(field).equals(""), "null messenger input resets safely");
        insert.invoke(field, "A\uD800\u0001\uD83D\uDE00B");
        check(value.invoke(field).equals("A\uD83D\uDE00B"), "pasted input discards lone surrogates and controls without damaging valid code points");
    }

    private static void sidebarViewportContracts() throws Exception {
        ClassNode background = readClass("vorga/phazeclient/implement/menu/components/implement/other/BackgroundComponent");
        MethodNode render = method(background, "renderConfigs");
        check(calls(render).stream().anyMatch(call -> call.name.equals("configViewportY"))
                        && calls(render).stream().anyMatch(call -> call.name.equals("configViewportHeight")),
                "sidebar rendering derives its scissor from the shared footer-safe viewport");
        check(calls(render).stream().anyMatch(call -> call.owner.equals(SETTINGS + "SettingLabel") && call.name.equals("renderMsdf")),
                "long config labels use the same clipped one-shot marquee as other setting labels");
        for (String methodName : List.of("mouseClicked", "isInteractiveHover", "mouseScrolled")) {
            List<MethodInsnNode> instructions = calls(method(background, methodName));
            check(instructions.stream().anyMatch(call -> call.name.equals("configViewportY"))
                            && instructions.stream().anyMatch(call -> call.name.equals("configViewportHeight")),
                    "sidebar render/click/scroll coordinates share the viewport: " + methodName);
            check(instructions.stream().anyMatch(call -> call.name.equals("isConfigsViewOpen"))
                            && instructions.stream().anyMatch(call -> call.name.equals("isCosmeticsViewOpen")),
                    "hidden sidebar controls never handle full-page content input: " + methodName);
        }
        background.superName = "java/lang/Object";
        background.interfaces.clear();
        background.fields.removeIf(field -> !List.of("CONFIG_ROW_HEIGHT", "CONFIG_ROW_GAP", "HEADER_HEIGHT", "FOOTER_BUTTON_HEIGHT").contains(field.name));
        for (String fieldName : List.of("x", "y", "height")) {
            background.fields.add(new org.objectweb.asm.tree.FieldNode(Opcodes.ACC_PUBLIC, fieldName, "F", null, null));
        }
        // AbstractComponent owns float dimensions; only copy pure layout helpers, with their owner adjusted below.
        background.methods.removeIf(method -> !List.of("footerStartY", "configRowStartY", "configViewportY", "configViewportHeight", "configContentHeight").contains(method.name));
        for (MethodNode method : background.methods) for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof FieldInsnNode field && field.name.equals("height")) field.owner = background.name;
            if (instruction instanceof FieldInsnNode field && field.name.equals("y")) field.owner = background.name;
        }
        background.methods.add(plainConstructor());
        Class<?> layout = new OfflineClasses().define(background);
        Object instance = layout.getConstructor().newInstance();
        Method top = layout.getDeclaredMethod("configViewportY");
        Method viewport = layout.getDeclaredMethod("configViewportHeight");
        Method content = layout.getDeclaredMethod("configContentHeight", int.class);
        for (Method method : List.of(top, viewport, content)) method.setAccessible(true);
        for (float height : new float[]{0, 52, 248, 300, 720}) {
            layout.getField("height").setFloat(instance, height);
            layout.getField("y").setFloat(instance, 13.0F);
            float start = (float) top.invoke(instance);
            float size = (float) viewport.invoke(instance);
            float newConfigTop = 13.0F + height - 18.0F - 5.0F - 15.0F - 4.0F;
            check(size >= 0.0F, "sidebar viewport height stays nonnegative even on tiny panels");
            if (size > 0.0F) check(start + size <= newConfigTop - 4.0F,
                    "sidebar rows and label clipping never enter either footer button");
        }
        check((float) content.invoke(null, 0) == 0.0F && (float) content.invoke(null, 1) == 17.5F
                        && (float) content.invoke(null, 40) == 739.0F,
                "empty, single-row and long sidebar lists retain exact row/gap geometry");
    }

    private static MethodNode plainConstructor() {
        MethodNode constructor = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        constructor.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false));
        constructor.instructions.add(new InsnNode(Opcodes.RETURN));
        constructor.maxLocals = 1;
        constructor.maxStack = 1;
        return constructor;
    }

    private static void enabledDragging() throws Exception {
        CursorHarness cursor = new CursorHarness();
        cursor.options(true, true);
        check(cursor.enabled(), "both toggles enabled permit dynamic cursors");
        cursor.call("beginFrame");
        cursor.call("requestHand");
        cursor.call("endFrame", cursor.enabled());
        cursor.call("beginDrag", CursorManager.SHAPE_HRESIZE);
        check(appliedShapes.equals(List.of(CursorManager.SHAPE_HAND, CursorManager.SHAPE_HRESIZE)),
                "an enabled drag applies its resize cursor immediately");
        for (int frame = 0; frame < 120; frame++) {
            cursor.call("beginFrame");
            cursor.call("requestHand");
            cursor.call("requestNotAllowed");
            cursor.call("requestBeam");
            cursor.call("beginDrag", CursorManager.SHAPE_HRESIZE);
            cursor.call("endFrame", cursor.enabled());
            check(appliedShapes.size() == 2, "repeated drag frames and hover changes do not flicker or reapply the cursor");
        }
        check(createdShapes.equals(List.of(CursorManager.SHAPE_HAND, CursorManager.SHAPE_HRESIZE)),
                "each enabled cursor shape is created once and reused");
        cursor.call("endDrag");
        cursor.call("beginFrame");
        cursor.call("requestBeam");
        cursor.call("endFrame", cursor.enabled());
        check(appliedShapes.getLast() == CursorManager.SHAPE_BEAM, "release restores regular hover priorities");
        cursor.call("beginFrame");
        cursor.call("endFrame", cursor.enabled());
        check(appliedShapes.getLast() == CursorManager.SHAPE_ARROW, "leaving all widgets restores the arrow");
    }

    private static void disableAndReenable() throws Exception {
        for (boolean disableModule : new boolean[]{true, false}) {
            CursorHarness cursor = new CursorHarness();
            cursor.options(true, true);
            cursor.call("beginFrame");
            cursor.call("beginDrag", CursorManager.SHAPE_HRESIZE);
            cursor.call("endFrame", cursor.enabled());
            cursor.options(!disableModule, disableModule);
            cursor.call("beginFrame");
            cursor.call("beginDrag", CursorManager.SHAPE_VRESIZE);
            check(appliedShapes.equals(List.of(CursorManager.SHAPE_HRESIZE)),
                    "disabling during a drag prevents any further immediate resize changes");
            cursor.call("endFrame", cursor.enabled());
            check(appliedShapes.equals(List.of(CursorManager.SHAPE_HRESIZE, CursorManager.SHAPE_ARROW)),
                    "disabling an active drag restores the arrow exactly once");
            check(cursor.intField("dragOverrideShape") == 0, "the disabled frame clears the old drag pin");
            appliedShapes.clear();
            cursor.options(true, true);
            cursor.call("beginFrame");
            cursor.call("requestHand");
            cursor.call("endFrame", cursor.enabled());
            check(appliedShapes.equals(List.of(CursorManager.SHAPE_HAND)), "re-enabling cannot resurrect a stale drag");
            cursor.call("beginDrag", CursorManager.SHAPE_HRESIZE);
            check(appliedShapes.getLast() == CursorManager.SHAPE_HRESIZE, "a fresh drag works after re-enabling");
            cursor.call("endDrag");
            check(cursor.intField("dragOverrideShape") == 0, "release also clears a newly enabled drag");
        }
    }

    private static void scrollAndScreenReset() throws Exception {
        CursorHarness cursor = new CursorHarness();
        cursor.options(true, true);
        cursor.call("notifyScroll", 0.0, 1.0);
        cursor.field("scrollOverrideUntilNanos").setLong(null, Long.MAX_VALUE);
        cursor.call("beginFrame");
        cursor.call("requestBeam");
        cursor.call("endFrame", cursor.enabled());
        check(appliedShapes.equals(List.of(CursorManager.SHAPE_VRESIZE)), "enabled scrolling stays stable across hover frames");
        cursor.options(true, false);
        cursor.call("beginFrame");
        cursor.call("notifyScroll", 1.0, 0.0);
        cursor.call("endFrame", cursor.enabled());
        check(appliedShapes.equals(List.of(CursorManager.SHAPE_VRESIZE, CursorManager.SHAPE_ARROW)),
                "disabling clears a pending scroll override without a horizontal-cursor flash");
        check(cursor.intField("scrollOverrideShape") == 0
                        && cursor.field("scrollOverrideUntilNanos").getLong(null) == 0L,
                "disabled frames clear both scroll shape and expiry");
        cursor.options(true, true);
        cursor.call("beginFrame");
        cursor.call("requestHand");
        cursor.call("endFrame", cursor.enabled());
        check(appliedShapes.getLast() == CursorManager.SHAPE_HAND, "re-enabling does not restore the old scroll override");
        cursor.call("beginFrame");
        cursor.call("beginDrag", CursorManager.SHAPE_HRESIZE);
        cursor.call("forceArrow");
        int commits = appliedShapes.size();
        cursor.call("endFrame", cursor.enabled());
        check(appliedShapes.size() == commits && appliedShapes.getLast() == CursorManager.SHAPE_ARROW,
                "closing a screen clears the pin and prevents a late frame from restoring it");
        check(cursor.intField("dragOverrideShape") == 0 && cursor.intField("scrollOverrideShape") == 0,
                "screen removal clears all gesture overrides");
    }

    private static void readoutContracts() throws Exception {
        ClassNode value = readClass(SETTINGS + "ValueComponent");
        ClassNode menu = readClass("vorga/phazeclient/implement/menu/MenuScreen");
        for (ClassNode gui : List.of(value, menu)) {
            check(gui.methods.stream().flatMap(method -> calls(method).stream()).noneMatch(call -> call.owner.equals(OVERLAY)),
                    "GUI sliders and menu neither announce nor render nor hide the chat-only readout");
        }
        check(calls(method(value, "changeValue")).stream().anyMatch(call -> call.name.equals("applySnap")),
                "removing the popup preserves slider magnetic snapping");
        check(calls(method(value, "render")).stream().anyMatch(call -> call.owner.endsWith("/MsdfRenderer")
                        && call.name.equals("renderText")), "ordinary inline slider values remain visible");
        check(calls(method(value, "mouseReleased")).stream().anyMatch(call -> call.owner.equals(CURSOR)
                        && call.name.equals("endDrag")), "GUI slider release explicitly clears its cursor pin");

        ClassNode hud = readClass("vorga/phazeclient/implement/hud/CustomHudRenderer");
        check(calls(method(hud, "snapAndAnnounceHudScale")).stream().anyMatch(call -> call.owner.equals(OVERLAY)
                        && call.name.equals("show")), "active chat HUD resizing still updates the scale readout");
        int renderCalls = 0;
        for (MethodNode method : hud.methods) {
            for (AbstractInsnNode instruction : method.instructions) {
                if (!(instruction instanceof MethodInsnNode call) || !call.owner.equals(OVERLAY)
                        || !call.name.equals("render")) continue;
                renderCalls++;
                boolean chatGuard = false;
                int renderIndex = method.instructions.indexOf(instruction);
                for (AbstractInsnNode guard : method.instructions) {
                    if (method.instructions.indexOf(guard) >= renderIndex) break;
                    if (guard instanceof TypeInsnNode type && type.getOpcode() == Opcodes.INSTANCEOF
                            && type.desc.equals("net/minecraft/client/gui/screen/ChatScreen")) {
                        AbstractInsnNode branch = guard.getNext();
                        while (branch != null && branch.getOpcode() < 0) branch = branch.getNext();
                        chatGuard |= branch instanceof JumpInsnNode jump && jump.getOpcode() == Opcodes.IFEQ
                                && method.instructions.indexOf(jump.label) > renderIndex;
                    }
                }
                check(chatGuard, "the HUD readout draw is guarded by an active ChatScreen");
            }
        }
        check(renderCalls == 1, "the chat scale readout is drawn exactly once outside the GUI");
    }

    private static void messengerContracts() throws Exception {
        String owner = "vorga/phazeclient/implement/menu/components/implement/other/MessengerViewComponent";
        ClassNode messenger = readClass(owner);
        for (String name : List.of("renderNavigation", "renderCompactFriend", "renderConversationHeader",
                "renderConversationCard", "renderFriendRequestCard", "renderSelectableUser",
                "renderMessage", "renderInput", "renderActionButton", "renderRequestActionButton")) {
            check(calls(method(messenger, name)).stream().anyMatch(call -> call.owner.equals(owner)
                            && call.name.equals("animatedHover")),
                    "messenger interactive control has an independent smooth hover: " + name);
        }
        check(constant(messenger, "HOVER_MS") == 170.0F,
                "messenger hover uses the same 170 ms duration as module-setting rows");
        check(calls(method(messenger, "animatedHover")).stream().anyMatch(call -> call.name.equals("getOutputFloat")),
                "messenger samples hover without allocating boxed animation output");
        check(messenger.methods.stream().filter(method -> method.name.startsWith("lambda$animatedHover"))
                        .flatMap(method -> calls(method).stream()).anyMatch(call -> call.name.equals("setDirectionAndFinish")),
                "new hover states start at a finished idle state instead of flashing on first render");

        MethodNode conversationHeader = method(messenger, "renderConversationHeader");
        MethodNode moduleHeader = method(readClass(
                "vorga/phazeclient/implement/menu/components/implement/other/ModuleDetailComponent"), "renderHeader");
        for (MethodNode header : List.of(conversationHeader, moduleHeader)) {
            boolean usesMissingGlyph = false;
            for (AbstractInsnNode instruction : header.instructions) {
                usesMissingGlyph |= instruction instanceof LdcInsnNode literal && "‹".equals(literal.cst);
            }
            check(!usesMissingGlyph && calls(header).stream().anyMatch(call -> call.owner.equals(
                            "vorga/phazeclient/implement/menu/UiMsdfIconAtlas") && call.name.equals("renderBackArrow")),
                    "conversation and module headers draw the same MSDF back arrow, not a missing font glyph");
        }
        try (InputStream manifest = GuiInteractionChecks.class.getClassLoader()
                .getResourceAsStream("assets/phaze/msdf/ui_icons.json")) {
            check(manifest != null && new String(manifest.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                            .matches("(?s).*\"id\":\"minecraft:textures/back_arrow.png\"[^}]*\\}[^}]*\"inkBounds\".*"),
                    "the back arrow is in the MSDF icon atlas with its visible stroke bounds");
        }

        String style = "vorga/phazeclient/implement/menu/MenuStyle";
        for (String paletteMethod : List.of("settingSurface", "settingOutline")) {
            check(messenger.methods.stream().filter(method -> method.name.equals("drawChatCard"))
                            .flatMap(method -> calls(method).stream()).anyMatch(call -> call.owner.equals(style)
                                    && call.name.equals(paletteMethod)),
                    "messenger cards reuse the shared setting palette: " + paletteMethod);
        }
        check(calls(method(messenger, "drawStatus")).stream().noneMatch(call -> call.owner.endsWith("/ShapeProperties")),
                "online and offline labels no longer draw status dots");
        check(calls(method(messenger, "drawStatus")).stream().anyMatch(call -> call.name.equals("drawText")),
                "removing status dots retains the localized status text");
        boolean dotSpacing = false;
        for (AbstractInsnNode instruction : method(messenger, "drawCenteredStatus").instructions) {
            if (instruction instanceof LdcInsnNode constant && Float.valueOf(7.0F).equals(constant.cst)) {
                dotSpacing = true;
            }
        }
        check(!dotSpacing, "centered status labels do not reserve the removed dot's horizontal space");
        for (String name : List.of("renderCenter", "mouseClicked")) {
            check(calls(method(messenger, name)).stream().anyMatch(call -> call.owner.equals(owner)
                            && call.name.equals("listBottom")),
                    "rendering and clicks share the same list viewport: " + name);
        }
        MethodNode center = method(messenger, "renderCenter");
        int onlineReads = 0;
        for (AbstractInsnNode instruction : center.instructions) {
            if (!(instruction instanceof MethodInsnNode call) || !call.name.equals("getOnlineCount")) continue;
            onlineReads++;
            int readIndex = center.instructions.indexOf(instruction);
            boolean globalGuard = false;
            for (AbstractInsnNode guard : center.instructions) {
                if (center.instructions.indexOf(guard) >= readIndex) break;
                if (!(guard instanceof FieldInsnNode field) || field.getOpcode() != Opcodes.GETSTATIC
                        || !field.owner.equals(owner + "$Section") || !field.name.equals("GLOBAL")) continue;
                AbstractInsnNode branch = guard.getNext();
                while (branch != null && branch.getOpcode() < 0) branch = branch.getNext();
                globalGuard |= branch instanceof JumpInsnNode jump && jump.getOpcode() == Opcodes.IF_ACMPNE
                        && center.instructions.indexOf(jump.label) > readIndex;
            }
            check(globalGuard, "the header online counter is guarded by the Global Chat section");
        }
        check(onlineReads > 0, "Global Chat retains its online counter");
    }

    private static void messengerCardBounds() throws Exception {
        ClassNode messenger = readClass("vorga/phazeclient/implement/menu/components/implement/other/MessengerViewComponent");
        check(constant(messenger, "DIRECT_CARD_HEIGHT") == constant(messenger, "FRIEND_CARD_HEIGHT")
                        && constant(messenger, "DIRECT_CARD_HEIGHT") < 44.0F,
                "direct-message cards are compact and match friend-card height");
        float cardHeight = constant(messenger, "FRIEND_CARD_HEIGHT");
        check(cardHeight == AbstractSettingComponent.ROW_HEIGHT,
                "friend and direct-message cards match the module-setting row height");
        float avatarSize = constant(messenger, "CARD_AVATAR_SIZE");
        float avatarInset = constant(messenger, "CARD_AVATAR_INSET");
        check(avatarSize > 0.0F && avatarSize < cardHeight,
                "compact friend-card avatars fit inside the setting-height row");
        check(Math.abs(avatarInset - (cardHeight - avatarInset - avatarSize)) < 0.0001F,
                "friend-card avatars have equal top, bottom and left padding");
        check(constant(messenger, "CARD_TEXT_INSET") > avatarInset + avatarSize,
                "compact friend-card text remains separated from the avatar");
        float lineStep = constant(messenger, "CARD_TEXT_LINE_STEP");
        check(lineStep == 10.0F, "friend-card names and statuses retain their existing line spacing");
        for (float[] textLayout : new float[][]{
                {constant(messenger, "DIRECT_CARD_NAME_OFFSET"), 5.2F}
        }) {
            float textTop = textLayout[0];
            float textBottom = textTop + lineStep + textLayout[1];
            check(textTop > 0.0F && textBottom < cardHeight,
                    "both lines fit inside compact friend and direct-message cards");
            check(Math.abs(textTop - (cardHeight - textBottom)) < 0.0001F,
                    "friend-card names and their second line remain vertically centered");
        }
        float nameOffset = constant(messenger, "COMPACT_FRIEND_NAME_OFFSET");
        float statusOffset = constant(messenger, "COMPACT_FRIEND_STATUS_OFFSET");
        check(Math.abs(statusOffset - nameOffset - 7.0F) < 0.0001F,
                "sidebar friend names and statuses retain their existing line spacing");
        check(Math.abs(nameOffset - (18.0F - statusOffset - 4.8F)) < 0.0001F,
                "sidebar friend text has equal top and bottom card padding");
        check(nameOffset > 1.0F && statusOffset > 8.0F,
                "sidebar friend names and statuses move down together");
        float backSize = constant(messenger, "DIRECT_BACK_SIZE");
        float backInset = constant(messenger, "DIRECT_BACK_INSET");
        float backHeight = constant(messenger, "DIRECT_HEADER_HEIGHT");
        float backIconSize = constant(messenger, "DIRECT_BACK_ICON_SIZE");
        check(backHeight == 27.0F && Math.abs(backSize - (backHeight - backInset * 2.0F)) < 0.0001F,
                "the conversation back button is a square with equal header insets");
        check(backIconSize == 10.0F && backIconSize > 7.5F,
                "the conversation back arrow is slightly larger than the module-settings arrow");
        check(backIconSize < backSize,
                "the back arrow stays within its square hover surface");
        float[][] layouts = {
                {constant(messenger, "BLOCKED_ROW_HEIGHT"), constant(messenger, "BLOCKED_ROW_STEP")},
                {constant(messenger, "DIRECT_CARD_HEIGHT"), constant(messenger, "DIRECT_ROW_STEP")},
                {constant(messenger, "REQUEST_CARD_HEIGHT"), constant(messenger, "REQUEST_ROW_STEP")}
        };
        float headerInset = constant(messenger, "HEADER_TEXT_INSET");
        // Isolate only the actual pure layout methods: no Minecraft window, avatar service or user configuration.
        messenger.superName = "java/lang/Object";
        messenger.fields.clear();
        messenger.methods.removeIf(method -> !List.of("inside", "cardIndex", "listBottom", "headerRightTextX").contains(method.name));
        Class<?> bounds = new OfflineClasses().define(messenger);
        Method card = bounds.getDeclaredMethod("cardIndex", double.class, double.class,
                float.class, float.class, float.class, float.class, float.class, float.class, int.class);
        Method bottom = bounds.getDeclaredMethod("listBottom", float.class, float.class, boolean.class);
        Method headerRight = bounds.getDeclaredMethod("headerRightTextX", float.class, float.class, float.class);
        card.setAccessible(true);
        bottom.setAccessible(true);
        headerRight.setAccessible(true);
        for (float width : new float[]{120.0F, 240.0F, 360.0F}) {
            float textWidth = 30.5F;
            float textX = (float) headerRight.invoke(null, 100.0F, width, textWidth);
            check(Math.abs(100.0F + width - textX - textWidth - headerInset) < 0.0001F,
                    "header online text has the same right padding as the title's left padding");
        }
        check((float) bottom.invoke(null, 50.0F, 240.0F, false) == 288.0F,
                "lists without a composer use the full content height");
        check((float) bottom.invoke(null, 50.0F, 240.0F, true) == 267.0F,
                "message lists leave the same composer height and gap in rendering and hit tests");
        for (float[] layout : layouts) {
            float height = layout[0];
            float step = layout[1];
            float listBottom = 50.0F + step * 2.0F + height;
            for (int index = 0; index < 3; index++) {
                double rowY = 50.0F + index * step;
                check((int) card.invoke(null, 110.0D, rowY + height / 2.0F,
                                100.0F, 50.0F, 240.0F, height, step, listBottom, 3) == index,
                        "each compact card selects its own row");
                check((int) card.invoke(null, 110.0D, rowY + height + 2.0F,
                                100.0F, 50.0F, 240.0F, height, step, listBottom, 3) == -1,
                        "card gaps are not clickable");
            }
            check((int) card.invoke(null, 110.0D, 50.0D + step * 2.0F + height / 2.0F,
                            100.0F, 50.0F, 240.0F, height, step, listBottom - 1.0F, 3) == -1,
                    "a last card that cannot be rendered fully cannot be clicked invisibly");
            check((int) card.invoke(null, 99.0D, 51.0D,
                            100.0F, 50.0F, 240.0F, height, step, listBottom, 3) == -1,
                    "clicking outside the card's horizontal bounds does not select it");
        }
    }

    private static float constant(ClassNode node, String name) {
        return ((Number) node.fields.stream().filter(field -> field.name.equals(name))
                .findFirst().orElseThrow().value).floatValue();
    }

    /** Native-call replacements used only by the isolated compiled CursorManager. */
    public static long createStandardCursor(int shape) {
        createdShapes.add(shape);
        return shape;
    }

    public static void setCursor(long window, long cursor) {
        check(window == 1L, "native cursor changes use the isolated test window");
        appliedShapes.add((int) cursor);
    }

    private static final class CursorHarness {
        private final Class<?> manager;
        private final Object animations;
        private final BooleanSetting dynamicCursor;
        private final Method dynamicEnabled;
        private final Map<String, Method> methods = new HashMap<>();

        CursorHarness() throws Exception {
            appliedShapes.clear();
            createdShapes.clear();
            OfflineClasses loader = new OfflineClasses();
            ClassNode module = readClass(ANIMATIONS);
            // Keep the real isDynamicCursorEnabled implementation, but bypass
            // unrelated server-policy services and their persisted client ID.
            check(module.methods.stream().noneMatch(method -> method.name.equals("isEnabled")),
                    "offline eligibility does not overwrite an Animations-specific implementation");
            MethodNode eligibility = new MethodNode(Opcodes.ACC_PUBLIC, "isEnabled", "()Z", null, null);
            eligibility.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
            eligibility.instructions.add(new FieldInsnNode(Opcodes.GETFIELD,
                    "vorga/phazeclient/api/feature/module/Module", "state", "Z"));
            eligibility.instructions.add(new InsnNode(Opcodes.IRETURN));
            module.methods.add(eligibility);
            Class<?> animationClass = loader.define(module);
            animations = animationClass.getMethod("getInstance").invoke(null);
            dynamicCursor = (BooleanSetting) animationClass.getField("dynamicCursor").get(animations);
            dynamicEnabled = animationClass.getMethod("isDynamicCursorEnabled");

            ClassNode cursor = readClass(CURSOR);
            MethodNode window = method(cursor, "windowHandle");
            window.instructions.clear();
            window.tryCatchBlocks.clear();
            window.instructions.add(new InsnNode(Opcodes.LCONST_1));
            window.instructions.add(new InsnNode(Opcodes.LRETURN));
            for (MethodNode method : cursor.methods) {
                for (MethodInsnNode call : calls(method)) {
                    if (!call.owner.equals("org/lwjgl/glfw/GLFW")) continue;
                    call.owner = CALLBACKS;
                    call.name = switch (call.name) {
                        case "glfwCreateStandardCursor" -> "createStandardCursor";
                        case "glfwSetCursor" -> "setCursor";
                        default -> throw new AssertionError("Unexpected native cursor call: " + call.name);
                    };
                }
            }
            manager = loader.define(cursor);
            for (Method method : manager.getMethods()) methods.put(method.getName(), method);
        }

        void options(boolean moduleEnabled, boolean cursorEnabled) throws Exception {
            animations.getClass().getField("state").setBoolean(animations, moduleEnabled);
            dynamicCursor.setValue(cursorEnabled);
        }

        boolean enabled() throws Exception {
            return (boolean) dynamicEnabled.invoke(animations);
        }

        void call(String name, Object... arguments) throws Exception {
            methods.get(name).invoke(null, arguments);
        }

        Field field(String name) throws Exception {
            Field field = manager.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        }

        int intField(String name) throws Exception {
            return field(name).getInt(null);
        }
    }

    private static final class OfflineClasses extends ClassLoader {
        OfflineClasses() {
            super(GuiInteractionChecks.class.getClassLoader());
        }

        Class<?> define(ClassNode node) {
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            byte[] bytes = writer.toByteArray();
            return defineClass(node.name.replace('/', '.'), bytes, 0, bytes.length);
        }
    }

    private static ClassNode readClass(String name) throws Exception {
        try (InputStream input = GuiInteractionChecks.class.getClassLoader().getResourceAsStream(name + ".class")) {
            if (input == null) throw new AssertionError("Missing class: " + name);
            ClassNode node = new ClassNode(Opcodes.ASM9);
            new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG);
            return node;
        }
    }

    private static MethodNode method(ClassNode node, String name) {
        return node.methods.stream().filter(method -> method.name.equals(name)).findFirst().orElseThrow();
    }

    private static List<MethodInsnNode> calls(MethodNode method) {
        List<MethodInsnNode> calls = new ArrayList<>();
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call) calls.add(call);
        }
        return calls;
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
