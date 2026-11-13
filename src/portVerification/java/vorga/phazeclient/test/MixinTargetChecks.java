package vorga.phazeclient.test;

import com.google.gson.JsonParser;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.*;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Offline selector audit. Actual Mixin application still needs a client smoke run. */
public final class MixinTargetChecks {
    private static final String MIXIN = "Lorg/spongepowered/asm/mixin/";
    private static final Map<String, ClassNode> CLASSES = new HashMap<>();
    private static final Map<String, String> CLASS_NAMES = new HashMap<>();
    private static final Map<String, String> MEMBER_NAMES = new HashMap<>();
    private static final Remapper MAPPINGS = new Remapper() {
        @Override public String map(String name) { return CLASS_NAMES.getOrDefault(name, name); }
        @Override public String mapMethodName(String owner, String name, String descriptor) {
            return MEMBER_NAMES.getOrDefault(owner + ";" + name + descriptor, name);
        }
        @Override public String mapFieldName(String owner, String name, String descriptor) {
            return MEMBER_NAMES.getOrDefault(owner + ";" + name + ":" + descriptor, name);
        }
    };
    private static final List<String> ERRORS = new ArrayList<>();
    private static final List<String> SKIPPED = new ArrayList<>();
    private static int checked;

    public static void main(String[] args) throws Exception {
        loadMappings();
        var config = JsonParser.parseString(Files.readString(Path.of("src/main/resources/phaze.mixins.json"))).getAsJsonObject();
        String root = config.get("package").getAsString().replace('.', '/') + "/";
        Set<String> registered = new HashSet<>();
        for (String section : List.of("mixins", "client")) {
            for (var entry : config.getAsJsonArray(section)) {
                String name = root + entry.getAsString().replace('.', '/');
                check(registered.add(name), name, "registered only once");
                audit(name);
            }
        }
        auditConsumedMixins(root, registered);
        auditAccessWidener();
        Path report = Path.of("build/reports/port/mixin-targets.txt");
        Files.createDirectories(report.getParent());
        String summary = "Checked " + checked + " selectors/members/access wideners; " + ERRORS.size() + " mismatches; " + SKIPPED.size() + " optional classes unavailable";
        Files.writeString(report, summary + "\n" + String.join("\n", ERRORS) + "\n" + String.join("\n", SKIPPED));
        System.out.println(summary);
        SKIPPED.forEach(System.out::println);
        ERRORS.forEach(System.err::println);
        if (!ERRORS.isEmpty()) throw new AssertionError("Mixin target audit failed; see " + report);
    }

    /** A valid accessor still fails at runtime if the config never installs its interface. */
    private static void auditConsumedMixins(String root, Set<String> registered) throws Exception {
        Path output = Path.of("build/classes/java/main");
        check(Files.isDirectory(output), output.toString(), "production bytecode is available");
        if (!Files.isDirectory(output)) return;
        Set<String> consumers = new TreeSet<>();
        try (var files = Files.walk(output)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".class")).toList()) {
                String name = output.relativize(file).toString().replace('\\', '/').replaceFirst("\\.class$", "");
                if (name.startsWith(root) && !registered.contains(name)) continue;
                ClassNode node = read(name);
                if (node == null) continue;
                for (MethodNode method : node.methods) for (AbstractInsnNode instruction : method.instructions) {
                    String owner = instruction instanceof TypeInsnNode type ? type.desc
                            : instruction instanceof MethodInsnNode call ? call.owner : null;
                    if (owner != null && owner.startsWith(root)) consumers.add(owner);
                }
            }
        }
        for (String name : consumers) {
            ClassNode node = read(name);
            if (node != null && annotation(node.visibleAnnotations, node.invisibleAnnotations, MIXIN + "Mixin;") != null)
                check(registered.contains(name), name, "consumed mixin interface is registered");
        }
    }

    private static void auditAccessWidener() throws Exception {
        Path path = Path.of("src/main/resources/phaze.accesswidener");
        List<String> lines = Files.readAllLines(path);
        check(lines.getFirst().strip().equals("accessWidener v2 named"), path.toString(), "named v2 header");
        for (int index = 1; index < lines.size(); index++) {
            String line = lines.get(index).split("#", 2)[0].strip();
            if (line.isEmpty()) continue;
            String[] entry = line.split("\\s+");
            String location = path + ":" + (index + 1);
            if (entry.length < 3) { check(false, location, "invalid entry " + line); continue; }
            ClassNode owner = read(entry[2]);
            check(owner != null, location, "class " + entry[2]);
            if (owner == null) continue;
            switch (entry[1]) {
                case "class" -> check(entry.length == 3, location, "class entry arity");
                case "field" -> check(entry.length == 5 && owner.fields.stream().anyMatch(field ->
                        field.name.equals(entry[3]) && field.desc.equals(entry[4])), location, "declared field " + line);
                case "method" -> check(entry.length == 5 && owner.methods.stream().anyMatch(method ->
                        method.name.equals(entry[3]) && method.desc.equals(entry[4])), location, "declared method " + line);
                default -> check(false, location, "unknown member kind " + entry[1]);
            }
        }
    }

    private static void loadMappings() throws Exception {
        Path path = Path.of(System.getProperty("phaze.port.mappings"));
        List<String> lines = Files.readAllLines(path);
        String[] header = lines.getFirst().split("\t");
        if (!Arrays.asList(header).containsAll(List.of("official", "intermediary", "named"))) {
            throw new IllegalStateException("Expected official/intermediary/named Yarn mappings: " + path);
        }
        Map<String, String> intermediaryNames = new HashMap<>();
        for (String line : lines) if (line.startsWith("c\t")) {
            String[] values = line.split("\t", -1);
            CLASS_NAMES.put(values[1], values[3]);
            CLASS_NAMES.put(values[2], values[3]);
            intermediaryNames.put(values[1], values[2]);
        }
        Remapper intermediary = new Remapper() {
            @Override public String map(String name) { return intermediaryNames.getOrDefault(name, name); }
        };
        String officialOwner = null, intermediaryOwner = null;
        for (String line : lines) {
            if (line.startsWith("c\t")) {
                String[] values = line.split("\t", -1);
                officialOwner = values[1]; intermediaryOwner = values[2];
            } else if (line.startsWith("\tm\t") || line.startsWith("\tf\t")) {
                String[] values = line.split("\t", -1);
                boolean method = values[1].equals("m");
                String separator = method ? "" : ":";
                String descriptor = method ? intermediary.mapMethodDesc(values[2]) : intermediary.mapDesc(values[2]);
                MEMBER_NAMES.put(officialOwner + ";" + values[3] + separator + values[2], values[5]);
                MEMBER_NAMES.put(intermediaryOwner + ";" + values[4] + separator + descriptor, values[5]);
            }
        }
    }

    private static void audit(String name) throws Exception {
        ClassNode mixin = read(name);
        if (mixin == null) { ERRORS.add("Missing registered mixin " + name); return; }
        AnnotationNode definition = annotation(mixin.visibleAnnotations, mixin.invisibleAnnotations, MIXIN + "Mixin;");
        if (definition == null) { ERRORS.add("No @Mixin on " + name); return; }
        List<String> targets = new ArrayList<>();
        for (Object value : list(value(definition, "value"))) targets.add(((Type) value).getInternalName());
        for (Object value : list(value(definition, "targets"))) targets.add(value.toString().replace('.', '/'));
        for (String targetName : targets) {
            ClassNode target = read(targetName);
            if (target == null) {
                if (targetName.startsWith("net/minecraft/") || targetName.startsWith("com/mojang/")) ERRORS.add(name + ": absent vanilla target " + targetName);
                else SKIPPED.add(name + ": optional target not on verification classpath: " + targetName);
                continue;
            }
            for (FieldNode field : mixin.fields) {
                var shadow = annotation(field.visibleAnnotations, field.invisibleAnnotations, MIXIN + "Shadow;");
                if (shadow != null) check(hasField(target, field.name, field.desc) || aliases(shadow).stream().anyMatch(alias -> hasFieldUnchecked(target, alias, field.desc)), name, "@Shadow field " + field.name + field.desc);
            }
            for (MethodNode method : mixin.methods) {
                var shadow = annotation(method.visibleAnnotations, method.invisibleAnnotations, MIXIN + "Shadow;");
                if (shadow != null) {
                    String prefix = Objects.toString(value(shadow, "prefix"), "shadow$");
                    String member = method.name.startsWith(prefix) ? method.name.substring(prefix.length()) : method.name;
                    check(!methods(target, member + method.desc).isEmpty() || aliases(shadow).stream().anyMatch(alias -> !methodsUnchecked(target, alias + method.desc).isEmpty()), name, "@Shadow method " + member + method.desc);
                }
                for (AnnotationNode annotation : annotations(method)) {
                    if (annotation.desc.equals(MIXIN + "gen/Accessor;")) accessor(name, target, method, annotation);
                    if (annotation.desc.equals(MIXIN + "gen/Invoker;")) invoker(name, target, method, annotation);
                    if (annotation.desc.startsWith(MIXIN + "injection/") || annotation.desc.startsWith("Lcom/llamalad7/mixinextras/injector/")) {
                        for (Object selector : list(value(annotation, "method"))) {
                            List<MethodNode> selected = methods(target, selector.toString());
                            check(!selected.isEmpty(), name, method.name + " injection selector " + selector);
                            if (annotation.desc.equals(MIXIN + "injection/Inject;") && !selected.isEmpty()) {
                                check(selected.stream().anyMatch(host -> callbackMatches(method, host)), name,
                                        method.name + " callback arguments for " + selector);
                            }
                            for (Object atValue : list(value(annotation, "at"))) {
                                if (atValue instanceof AnnotationNode at) injectionPoint(name, method.name, selected, at);
                            }
                        }
                    }
                }
            }
        }
    }

    private static boolean callbackMatches(MethodNode handler, MethodNode host) {
        Type[] arguments = Type.getArgumentTypes(handler.desc);
        Type[] targetArguments = Type.getArgumentTypes(host.desc);
        List<Integer> capturedArguments = new ArrayList<>();
        int callback = -1;
        for (int index = 0; index < arguments.length; index++) {
            String descriptor = arguments[index].getDescriptor();
            if (descriptor.equals(MIXIN + "injection/callback/CallbackInfo;")
                    || descriptor.equals(MIXIN + "injection/callback/CallbackInfoReturnable;")) {
                callback = index;
                boolean returning = Type.getReturnType(host.desc).getSort() != Type.VOID;
                if (returning != descriptor.endsWith("CallbackInfoReturnable;")) return false;
                break;
            }
            if (!hasSugar(handler, index)) capturedArguments.add(index);
        }
        if (callback < 0 || Type.getReturnType(handler.desc).getSort() != Type.VOID) return false;
        if (capturedArguments.isEmpty()) return true;
        if (capturedArguments.size() != targetArguments.length) return false;
        for (int index = 0; index < targetArguments.length; index++) {
            int parameter = capturedArguments.get(index);
            if (!arguments[parameter].equals(targetArguments[index]) && !hasParameterAnnotation(handler, parameter, MIXIN + "injection/Coerce;")) return false;
        }
        return true;
    }

    private static boolean hasSugar(MethodNode method, int index) {
        for (List<AnnotationNode>[] annotations : Arrays.asList(method.visibleParameterAnnotations, method.invisibleParameterAnnotations)) {
            if (annotations != null && index < annotations.length && annotations[index] != null) {
                for (AnnotationNode annotation : annotations[index]) if (annotation.desc.startsWith("Lcom/llamalad7/mixinextras/sugar/")) return true;
            }
        }
        return false;
    }

    private static boolean hasParameterAnnotation(MethodNode method, int index, String descriptor) {
        for (List<AnnotationNode>[] annotations : Arrays.asList(method.visibleParameterAnnotations, method.invisibleParameterAnnotations)) {
            if (annotations != null && index < annotations.length && annotations[index] != null) {
                for (AnnotationNode annotation : annotations[index]) if (annotation.desc.equals(descriptor)) return true;
            }
        }
        return false;
    }

    private static void accessor(String name, ClassNode target, MethodNode method, AnnotationNode annotation) throws Exception {
        String field = Objects.toString(value(annotation, "value"), "");
        if (field.isEmpty()) field = inferredName(method.name, "get", "set", "is");
        Type type = Type.getMethodType(method.desc);
        String descriptor = type.getArgumentTypes().length == 0 ? type.getReturnType().getDescriptor() : type.getArgumentTypes()[0].getDescriptor();
        check(hasField(target, field, descriptor), name, "@Accessor " + field + descriptor);
    }

    private static void invoker(String name, ClassNode target, MethodNode method, AnnotationNode annotation) throws Exception {
        String member = Objects.toString(value(annotation, "value"), "");
        if (member.isEmpty()) member = inferredName(method.name, "call", "invoke");
        String descriptor = method.desc;
        if (member.equals("<init>")) descriptor = descriptor.substring(0, descriptor.indexOf(')') + 1) + "V";
        check(!methods(target, member + descriptor).isEmpty(), name, "@Invoker " + member + descriptor);
    }

    private static void injectionPoint(String name, String hook, List<MethodNode> methods, AnnotationNode at) {
        String selector = Objects.toString(value(at, "target"), "");
        if (selector.isEmpty() || methods.isEmpty()) return;
        String kind = Objects.toString(value(at, "value"), "");
        if (!kind.startsWith("INVOKE") && !kind.equals("FIELD") && !kind.equals("NEW")) return;
        int ordinal = value(at, "ordinal") instanceof Integer number ? number : -1;
        int opcode = value(at, "opcode") instanceof Integer number ? number : -1;
        boolean found = false;
        for (MethodNode method : methods) {
            int matches = 0;
            for (AbstractInsnNode instruction : method.instructions) {
                if (opcode >= 0 && instruction.getOpcode() != opcode) continue;
                if (instruction instanceof MethodInsnNode call && kind.startsWith("INVOKE")) {
                    if (selector.equals("L" + call.owner + ";" + call.name + call.desc)) matches++;
                }
                if (instruction instanceof FieldInsnNode field && kind.equals("FIELD")) {
                    if (selector.equals("L" + field.owner + ";" + field.name + ":" + field.desc)) matches++;
                }
                if (kind.equals("NEW")) {
                    if (instruction instanceof TypeInsnNode type && instruction.getOpcode() == org.objectweb.asm.Opcodes.NEW
                            && (selector.equals(type.desc) || selector.equals("L" + type.desc + ";"))) matches++;
                    if (instruction instanceof MethodInsnNode call && call.name.equals("<init>")
                            && selector.equals("L" + call.owner + ";" + call.name + call.desc)) matches++;
                }
            }
            found |= matches > 0 && (ordinal < 0 || ordinal < matches);
        }
        // NEW may use the constructor descriptor, which is checked as an invocation above.
        check(found, name, hook + " @At(" + kind + ", ordinal=" + ordinal + ") " + selector);
    }

    private static List<MethodNode> methods(ClassNode type, String selector) throws Exception {
        int descriptor = selector.indexOf('(');
        String name = descriptor < 0 ? selector : selector.substring(0, descriptor);
        int owner = name.lastIndexOf(';');
        if (owner >= 0) name = name.substring(owner + 1);
        String desc = descriptor < 0 ? null : selector.substring(descriptor);
        String wanted = name;
        List<MethodNode> found = type.methods.stream().filter(method -> (wanted.equals("*") || method.name.equals(wanted)) && (desc == null || method.desc.equals(desc))).toList();
        if (found.isEmpty() && !wanted.equals("<init>") && type.superName != null) {
            ClassNode parent = read(type.superName);
            if (parent != null) return methods(parent, selector);
        }
        return found;
    }

    private static boolean hasField(ClassNode type, String name, String desc) throws Exception {
        if (type.fields.stream().anyMatch(field -> field.name.equals(name) && field.desc.equals(desc))) return true;
        ClassNode parent = type.superName == null ? null : read(type.superName);
        return parent != null && hasField(parent, name, desc);
    }

    private static boolean hasFieldUnchecked(ClassNode type, String name, String desc) {
        try { return hasField(type, name, desc); } catch (Exception exception) { throw new IllegalStateException(exception); }
    }

    private static List<MethodNode> methodsUnchecked(ClassNode type, String selector) {
        try { return methods(type, selector); } catch (Exception exception) { throw new IllegalStateException(exception); }
    }

    private static List<String> aliases(AnnotationNode annotation) { return list(value(annotation, "aliases")).stream().map(Object::toString).toList(); }

    private static String inferredName(String name, String... prefixes) {
        for (String prefix : prefixes) if (name.startsWith(prefix) && name.length() > prefix.length()) {
            String tail = name.substring(prefix.length());
            return Character.toLowerCase(tail.charAt(0)) + tail.substring(1);
        }
        return name;
    }

    private static ClassNode read(String name) throws Exception {
        if (CLASSES.containsKey(name)) return CLASSES.get(name);
        try (InputStream input = MixinTargetChecks.class.getClassLoader().getResourceAsStream(name + ".class")) {
            if (input == null) { CLASSES.put(name, null); return null; }
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(new ClassRemapper(node, MAPPINGS), ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            CLASSES.put(name, node);
            return node;
        }
    }

    private static Object value(AnnotationNode annotation, String key) {
        if (annotation.values != null) for (int i = 0; i < annotation.values.size(); i += 2) if (annotation.values.get(i).equals(key)) return annotation.values.get(i + 1);
        return null;
    }

    private static List<?> list(Object value) { return value == null ? List.of() : value instanceof List<?> values ? values : List.of(value); }
    private static List<AnnotationNode> annotations(MethodNode method) {
        List<AnnotationNode> result = new ArrayList<>();
        if (method.visibleAnnotations != null) result.addAll(method.visibleAnnotations);
        if (method.invisibleAnnotations != null) result.addAll(method.invisibleAnnotations);
        return result;
    }

    private static AnnotationNode annotation(List<AnnotationNode> visible, List<AnnotationNode> invisible, String desc) {
        for (List<AnnotationNode> list : Arrays.asList(visible, invisible)) if (list != null) for (AnnotationNode annotation : list) if (annotation.desc.equals(desc)) return annotation;
        return null;
    }

    private static void check(boolean condition, String name, String message) { checked++; if (!condition) ERRORS.add(name + ": " + message); }
}
