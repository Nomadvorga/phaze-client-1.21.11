package vorga.phazeclient.base.util.shader;

import net.minecraft.util.Identifier;
import com.mojang.blaze3d.shaders.ShaderType;

/** Patches the current loaded source, preserving modern fog, RGSS and per-face lighting. */
public final class WorldColorShaderPatcher {
    public static final String UNIFORM = "PhazeWorldColor";
    private static final String MARKER = "// Phaze world color UBO";
    private static final String BLOCK = """
            // Phaze world color UBO
            layout(std140) uniform PhazeWorldColor {
                vec4 PhazeBlockColor;
                vec4 PhazeBlockParams;
                vec4 PhazeFluidColor;
                vec4 PhazeFluidParams;
                vec4 PhazeSkyColor;
                vec4 PhazeSkyParams;
                vec4 PhazeCloudColor;
                vec4 PhazeCloudParams;
                vec4 PhazePlayerColor;
                vec4 PhazePlayerParams;
                vec4 PhazeEntityColor;
                vec4 PhazeEntityParams;
                vec4 PhazeWorldActive;
            };
            """;
    private static final String APPLY = """
            vec4 phazeApplyWorldColor(vec4 color, vec4 tint, vec4 params) {
                if (PhazeWorldActive.x < 0.5 || params.z < 0.5) return color;
                vec3 rgb = color.rgb * max(tint.rgb, vec3(0.0));
                float gray = dot(clamp(rgb, 0.0, 1.0), vec3(0.2126, 0.7152, 0.0722));
                rgb = mix(vec3(gray), rgb, clamp(params.x, 0.0, 2.0));
                if (params.y > 0.0) rgb = mix(rgb, vec3(1.0), clamp(params.y, 0.0, 1.0));
                else if (params.y < 0.0) rgb *= 1.0 + clamp(params.y, -1.0, 0.0);
                return vec4(max(rgb, vec3(0.0)), color.a * tint.a);
            }
            """;
    private WorldColorShaderPatcher() {}

    public static boolean supports(Identifier shader) {
        if (shader == null || !shader.getNamespace().equals("minecraft")) return false;
        return switch (shader.getPath()) {
            case "core/terrain", "core/entity", "core/sky", "core/rendertype_clouds" -> true;
            default -> false;
        };
    }

    public static String patch(Identifier shader, ShaderType type, String source) {
        if (source == null || !supports(shader) || source.contains(MARKER)) return source;
        String path = shader.getPath();
        if (type == ShaderType.VERTEX) {
            if (!path.equals("core/entity")) return source;
            source = replaceOnce(source, "texCoord0 = UV0;", """
                    v_PhazeEntityTarget = 0.0;
                    if (PhazeWorldActive.x > 0.5 && Color.a > 0.990 && Color.a < 0.999) {
                        v_PhazeEntityTarget = Color.a > 0.994 ? 1.0 : 2.0;
                    #ifdef PER_FACE_LIGHTING
                        vertexPerFaceColorBack.a = 1.0;
                        vertexPerFaceColorFront.a = 1.0;
                    #else
                        vertexColor.a = 1.0;
                    #endif
                    }
                    texCoord0 = UV0;
                    """, shader);
            return declarations(source, BLOCK + "\nout float v_PhazeEntityTarget;\n");
        }
        if (type != ShaderType.FRAGMENT) return source;
        String extra = "";
        switch (path) {
            case "core/terrain" -> source = replaceOnce(source, "fragColor = apply_fog(color,", """
                    #ifdef PHAZE_TERRAIN_FLUID
                        color = phazeApplyWorldColor(color, PhazeFluidColor, PhazeFluidParams);
                    #else
                        color = phazeApplyWorldColor(color, PhazeBlockColor, PhazeBlockParams);
                    #endif
                        fragColor = apply_fog(color,
                    """, shader);
            case "core/entity" -> {
                extra = "\nin float v_PhazeEntityTarget;\n";
                source = replaceOnce(source, "fragColor = apply_fog(color,", """
                        if (v_PhazeEntityTarget > 0.5) {
                            color = phazeApplyWorldColor(color,
                                v_PhazeEntityTarget < 1.5 ? PhazePlayerColor : PhazeEntityColor,
                                v_PhazeEntityTarget < 1.5 ? PhazePlayerParams : PhazeEntityParams);
                        }
                        fragColor = apply_fog(color,
                        """, shader);
            }
            case "core/sky" -> source = replaceOnce(source, "apply_fog(ColorModulator,",
                    "apply_fog(phazeApplyWorldColor(ColorModulator, PhazeSkyColor, PhazeSkyParams),", shader);
            case "core/rendertype_clouds" -> source = replaceOnce(source, "vec4 color = vertexColor;",
                    """
                    vec4 color = vertexColor;
                    if (PhazeCloudParams.w < 0.5) color = phazeApplyWorldColor(vertexColor, PhazeCloudColor, PhazeCloudParams);
                    """, shader);
            default -> throw new IllegalStateException("Unsupported WorldColor shader " + shader);
        }
        return declarations(source, BLOCK + extra + APPLY);
    }

    private static String declarations(String source, String declarations) {
        // Keep #version first; imported source #line directives remain intact.
        int version = source.indexOf("#version");
        int newline = source.indexOf('\n', version);
        if (version < 0 || newline < 0) throw new IllegalStateException("WorldColor shader has no #version header");
        return source.substring(0, newline + 1) + declarations + source.substring(newline + 1);
    }

    private static String replaceOnce(String source, String needle, String replacement, Identifier shader) {
        int at = source.indexOf(needle);
        if (at < 0 || source.indexOf(needle, at + needle.length()) >= 0)
            throw new IllegalStateException("WorldColor source contract changed for " + shader + ": " + needle);
        return source.substring(0, at) + replacement + source.substring(at + needle.length());
    }
}
