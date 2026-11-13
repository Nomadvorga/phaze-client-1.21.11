package vorga.phazeclient.base.util.shader;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ChunkAnimatorShaderPatcher {
    private static final String PATCH_MARKER = "u_PhazeChunkAnimMode";

    private static final Pattern POSITION_ASSIGN = Pattern.compile(
            "(vec3\\s+position\\s*=\\s*_vert_position\\s*\\+\\s*translation\\s*;)"
    );

    private static final String UNIFORMS =
            "\nuniform int u_PhazeChunkAnimMode;"
          + "\nuniform float u_PhazeSectionValue[256];"
          + "\nuniform vec3 u_PhazeChunkAnimDirection;"
          + "\nout float v_PhazeChunkAnimFade;\n";

    private static final String POSITION_PATCH =
            "$1\n"
          + "    v_PhazeChunkAnimFade = 1.0;\n"
          + "    if (u_PhazeChunkAnimMode != 0) {\n"
          + "        float phazeValue = u_PhazeSectionValue[int(_draw_id) & 255];\n"
          + "        if (u_PhazeChunkAnimMode == 1) {\n"
          + "            position += u_PhazeChunkAnimDirection * phazeValue;\n"
          + "        } else if (u_PhazeChunkAnimMode == 2) {\n"
          + "            v_PhazeChunkAnimFade = phazeValue;\n"
          + "        } else if (u_PhazeChunkAnimMode == 3) {\n"
          + "            vec3 phazeChunkCenter = translation + vec3(8.0);\n"
          + "            position = mix(phazeChunkCenter, position, phazeValue);\n"
          + "        }\n"
          + "    }";

    private ChunkAnimatorShaderPatcher() { }

    public static String patch(String original) {
        if (original == null || original.contains(PATCH_MARKER) || original.contains("v_PhazeChunkAnimFade")) return original;
        if (original.contains("fragColor = _linearFog")) return patchFragment(original);

        if (!original.contains("_draw_id")
                || !original.contains("u_chunkFades")
                || !original.contains("u_CurrentTime")
                || !original.contains("_get_draw_translation")) {
            return original;
        }

        Matcher position = POSITION_ASSIGN.matcher(original);
        if (!position.find()) return original;

        int insertAt = findDeclarationInsertionPoint(original);
        if (insertAt < 0) return original;

        String withUniforms = original.substring(0, insertAt) + UNIFORMS + original.substring(insertAt);
        String patched = POSITION_ASSIGN.matcher(withUniforms).replaceFirst(POSITION_PATCH);
        return patched.equals(withUniforms) ? original : patched;
    }

    private static String patchFragment(String original) {
        int fade = original.indexOf("in float fadeFactor;");
        if (fade < 0) return original;
        String withVarying = original.substring(0, fade) + "in float v_PhazeChunkAnimFade;\n" + original.substring(fade);
        String needle = "fragColor = _linearFog";
        int at = withVarying.indexOf(needle);
        if (at < 0) return original;
        return withVarying.substring(0, at)
                + "if (v_PhazeChunkAnimFade < 1.0) color = mix(vec4(u_FogColor.rgb, color.a), color, clamp(v_PhazeChunkAnimFade, 0.0, 1.0));\n    "
                + withVarying.substring(at);
    }

    private static int findDeclarationInsertionPoint(String source) {
        int cursor = 0;
        while (cursor < source.length()) {
            int lineEnd = source.indexOf('\n', cursor);
            if (lineEnd < 0) lineEnd = source.length();
            String line = source.substring(cursor, lineEnd).trim();
            if (!line.isEmpty() && !line.startsWith("#") && !line.startsWith("//")) {
                return cursor;
            }
            cursor = Math.min(source.length(), lineEnd + 1);
        }
        return -1;
    }
}
