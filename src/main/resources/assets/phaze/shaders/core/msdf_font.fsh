#version 330

// Per-glyph MSDF parameters are vertex attributes; optional run effects use std140.
in vec2 TexCoord;
in vec4 FragColor;
in vec4 vMsdfParams;
in vec2 GlobalPos;

layout(std140) uniform MsdfEffects {
    vec4 OutlineColor;
    vec4 EffectFlags; // outline enabled, outline thickness, fadeout enabled, text x
    vec4 Fadeout; // start, end, max width, label fade enabled
    vec4 LabelFadeBounds; // left, right, ramp width, unused
    vec4 LabelFadeStrength;
};

uniform sampler2D Sampler0;

out vec4 OutColor;

float median(vec3 color) {
    return max(min(color.r, color.g), min(max(color.r, color.g), color.b));
}

void main() {
    float range = vMsdfParams.x;
    float thickness = vMsdfParams.y;
    float smoothness = vMsdfParams.z;

    float dist = median(texture(Sampler0, TexCoord).rgb) - 0.5 + thickness;
    vec2 unitRange = vec2(range) / vec2(textureSize(Sampler0, 0));
    vec2 screenTexSize = vec2(1.0) / max(fwidth(TexCoord), vec2(1.0e-6));
    float pixels = max(0.5 * dot(unitRange, screenTexSize), 1.0);
    float alpha = smoothstep(-smoothness, smoothness, dist * pixels);

    vec4 color = vec4(FragColor.rgb, FragColor.a * alpha);
    if (EffectFlags.x > 0.5) {
        color = mix(OutlineColor, FragColor, alpha);
        color.a *= smoothstep(-smoothness, smoothness, (dist + EffectFlags.y) * pixels);
    }
    if (EffectFlags.z > 0.5 && Fadeout.z > 0.0) {
        float normalizedX = (GlobalPos.x - EffectFlags.w) / Fadeout.z;
        color.a *= 1.0 - smoothstep(Fadeout.x, max(Fadeout.y, Fadeout.x + 1.0e-6), normalizedX);
    }
    if (Fadeout.w > 0.5) {
        float width = max(LabelFadeBounds.z, 1.0e-6);
        float leftRamp = smoothstep(0.0, width, GlobalPos.x - LabelFadeBounds.x);
        float rightRamp = smoothstep(0.0, width, LabelFadeBounds.y - GlobalPos.x);
        color.a *= mix(1.0, leftRamp, LabelFadeStrength.x) * mix(1.0, rightRamp, LabelFadeStrength.y);
    }
    OutColor = color;
}
