#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;
in vec2 RectBase;
in vec2 RectSize;
in vec4 Radius;
in vec2 Params;
in vec4 OutlineColor;

out vec4 vColor;
out vec2 vRectBase;
out vec2 vRectSize;
out vec4 vRadius;
out vec2 vParams;
out vec4 vOutlineColor;

void main() {
    vColor = Color;
    vRectBase = RectBase;
    vRectSize = RectSize;
    vRadius = Radius;
    vParams = Params;
    vOutlineColor = OutlineColor;

    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}
