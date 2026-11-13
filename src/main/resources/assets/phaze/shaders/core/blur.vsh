#version 330

#moj_import <phaze:common.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;

layout(std140) uniform BlurCompositeConfig {
    vec4 Radius;
    vec4 TintColor;
    vec2 Size;
    float Smoothness;
    float BlurRadius;
    float FrameMix;
    int RectMask;
    int BlurMode;
    vec2 BlurScreenSize;
    vec2 MaskPadding;
};

out vec2 FragCoord;
out vec4 FragColor;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vec2 padded = Size + 2.0 * MaskPadding;
    FragCoord = (rvertexcoord(gl_VertexID) * padded - MaskPadding) / max(Size, vec2(0.001));
    FragColor = Color;
}


